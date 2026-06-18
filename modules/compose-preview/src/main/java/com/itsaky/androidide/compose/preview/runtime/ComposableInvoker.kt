/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.runtime

import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Composable 函数反射调用器 v3.
 *
 * 取代 v2 的 [MethodHandleResolver] + [ComposableRenderer.invoke] 拼接.
 *
 * ## Compose 编译器 ABI 回顾
 *
 * 编译器对 `@Composable fun Foo(a: A, b: B = defaultValue)` 会生成如下字节码签名之一
 * (具体哪一种由编译器版本 + 函数是否含 default value 决定):
 *
 * - 静态无默认值: `static void Foo(A, B, Composer, int)`
 * - 静态含默认值: `static void Foo(A, B, Composer, int, int)` (最后一参是 `$default` 掩码)
 * - 实例无默认值: `void Foo(this, A, B, Composer, int)`
 * - 实例含默认值: `void Foo(this, A, B, Composer, int, int)`
 *
 * 旧 v2 实现只处理了 "静态+2参" / "实例+2参" 两种简单情况, 一旦用户代码含默认值或额外参数,
 * [MethodHandle.invokeWithArguments] 就会因参数数量不匹配抛 [WrongMethodTypeException],
 * 然后 [ComposableRenderer] 会走"参数最多的兜底分支"错误地把 user params 全填 0 / null,
 * 最终渲染出一个空白或异常的 Composable.
 *
 * ## v3 设计
 *
 * 1. **统一签名搜索**: 在目标类上扫描所有 `Foo` (含 `Foo$1`, `Foo$lambda` 但排除 `Foo$default`) 的方法,
 *    严格按"末两参 / 末三参是 Composer, int[, int]"的特征挑选 compose 函数.
 * 2. **正确数清参数槽位**: user args 在前, composer/changed/default 在末, 一并装入数组传给 MethodHandle.
 * 3. **错误诊断**: 找不到函数 / 找到多个候选 / invoke 失败时, 给出可读信息 (类名 + 函数名 + 签名列表).
 */
class ComposableInvoker {

    private val LOG = LoggerFactory.getLogger(ComposableInvoker::class.java)

    private data class CacheKey(val clazz: Class<*>, val functionName: String)

    private val cache = HashMap<CacheKey, Resolved>()

    /**
     * 在 [clazz] 上找到名为 [functionName] 的 @Composable 函数并 invoke 一次.
     *
     * @param composer   当前 [androidx.compose.runtime.Composer] 实例.
     * @param instance   实例方法所需的 receiver, 静态方法传 null.
     * @param args       用户参数 (按声明顺序). 缺省参数不要传, 这里统一按 `$default = -1` 走.
     *
     * @return [InvokeResult], [InvokeResult.ok] = true 时表示调用成功.
     */
    fun invoke(
        clazz: Class<*>,
        functionName: String,
        composer: Any,
        instance: Any?,
        args: Array<out Any?> = emptyArray(),
    ): InvokeResult {
        val resolved = resolve(clazz, functionName)
            ?: return InvokeResult.err(
                "Composable function '$functionName' not found on class '${clazz.name}'."
            )

        return try {
            val handle = resolved.handle
            val params = resolved.parameterTypes
            val callArgs = Array<Any?>(params.size) { i -> UNSET }

            // 1) instance slot (only if non-static)
            if (!resolved.isStatic) {
                if (instance == null) {
                    return InvokeResult.err(
                        "Composable '${resolved.method.name}' is non-static but no instance was provided."
                    )
                }
                callArgs[0] = instance
            }

            // 2) user args - 注入到 instance 之后 / 合成参数之前
            val userArgOffset = if (resolved.isStatic) 0 else 1
            for ((i, arg) in args.withIndex()) {
                val slot = userArgOffset + i
                if (slot >= callArgs.size) break
                callArgs[slot] = arg
            }

            // 3) composer / changed / default slots
            val tailStart = params.size - resolved.tailArgCount
            callArgs[tailStart] = composer
            callArgs[tailStart + 1] = 0               // $changed = 0
            if (resolved.hasDefaultBitmask) {
                callArgs[tailStart + 2] = -1          // $default = -1 (全用调用方传的值)
            }

            // 4) 防御: 任何 UNSET 的 user arg 换成 null
            for (i in userArgOffset until tailStart) {
                if (callArgs[i] === UNSET) callArgs[i] = null
            }

            handle.invokeWithArguments(*callArgs)
            InvokeResult.ok()
        } catch (e: Throwable) {
            LOG.error("Failed to invoke {}.{}", clazz.name, functionName, e)
            InvokeResult.err(
                "Failed to invoke '${clazz.simpleName}.${functionName}': " +
                    (e.cause?.message ?: e.message ?: e::class.java.simpleName)
            )
        }
    }

    /** 找到 @Composable 函数, 返回其 [Resolved], 找不到时返回 null. */
    fun resolve(clazz: Class<*>, functionName: String): Resolved? {
        cache[CacheKey(clazz, functionName)]?.let { return it }
        val resolved = doResolve(clazz, functionName)
        if (resolved != null) {
            cache[CacheKey(clazz, functionName)] = resolved
        }
        return resolved
    }

    /** 清空缓存, 让 [resolve] 重新扫描. */
    fun invalidate() {
        cache.clear()
    }

    private fun doResolve(clazz: Class<*>, functionName: String): Resolved? {
        val candidates = clazz.declaredMethods
            .filter { m ->
                // 排除 $default (合成) / 内部桥接
                !m.name.contains("\$default") &&
                    !m.isBridge &&
                    !m.isSynthetic &&
                    (m.name == functionName ||
                        m.name.startsWith("$functionName\$") ||
                        m.name == "$functionName\$lambda")
            }

        if (candidates.isEmpty()) {
            LOG.warn("No composable candidates for {}.{}", clazz.name, functionName)
            return null
        }

        val lookup = MethodHandles.lookup()

        // 排序策略: 参数最少的最可能是 @Composable 原始函数 (非 $lambda / $1 展开)
        val sorted = candidates.sortedBy { it.parameterCount }
        for (method in sorted) {
            val desc = describeSignature(method)
            if (!looksLikeComposableSignature(method)) {
                LOG.debug("Skipping {}.{} (not a composable signature): {}",
                    clazz.name, method.name, desc)
                continue
            }
            val resolved = wrapMethod(lookup, method)
            LOG.info("Resolved composable {}.{}: {}", clazz.name, method.name, desc)
            return resolved
        }

        // 兜底: 即使没匹配上 compose 签名, 也用参数最少的 (用户可能手写了奇怪的可空 Composable)
        val fallback = sorted.first()
        LOG.warn("Falling back to non-composable signature for {}.{}: {}",
            clazz.name, fallback.name, describeSignature(fallback))
        return wrapMethod(lookup, fallback)
    }

    private fun wrapMethod(lookup: MethodHandles.Lookup, method: Method): Resolved {
        method.isAccessible = true
        val handle = lookup.unreflect(method)
        val params = method.parameterTypes
        val isStatic = Modifier.isStatic(method.modifiers)
        val tail = computeTailArgCount(params)
        return Resolved(
            handle = handle,
            method = method,
            isStatic = isStatic,
            tailArgCount = tail,
            // $default bitmask 段: 末尾 tail 段里除 Composer, int 外还有 1 个 int 参数
            hasDefaultBitmask = tail == 3,
            parameterTypes = params,
        )
    }

    /**
     * 判断 method 末几个参数是否符合 Composer, int[, int] 模式.
     */
    private fun looksLikeComposableSignature(method: Method): Boolean {
        val params = method.parameterTypes
        if (params.size < 2) return false
        val tail = computeTailArgCount(params)
        if (tail <= 0) return false
        // tail 段必须是: [Composer, int] 或 [Composer, int, int]
        val composerIdx = params.size - tail
        if (!isComposerType(params[composerIdx])) return false
        if (!isInteger(params[composerIdx + 1])) return false
        if (tail == 3 && !isInteger(params[composerIdx + 2])) return false
        return true
    }

    /**
     * 计算末尾 composer+changed[+default] 段的长度.
     * 支持: tail=2 (无 default), tail=3 (含 $default bitmask).
     */
    private fun computeTailArgCount(params: Array<Class<*>>): Int {
        val n = params.size
        if (n < 2) return 0
        val secondLast = params[n - 2]
        val last = params[n - 1]
        if (isComposerType(secondLast) && isInteger(last)) return 2
        if (n >= 3) {
            val thirdLast = params[n - 3]
            if (isComposerType(thirdLast) && isInteger(params[n - 2]) && isInteger(last)) return 3
        }
        return 0
    }

    private fun isComposerType(clz: Class<*>): Boolean {
        // 兼容 "androidx.compose.runtime.Composer" 与子类型
        if (clz.name == "androidx.compose.runtime.Composer") return true
        return runCatching { Class.forName("androidx.compose.runtime.Composer").isAssignableFrom(clz) }.getOrDefault(false)
    }

    private fun isInteger(clz: Class<*>): Boolean = clz == Int::class.javaPrimitiveType ||
        clz == Integer.TYPE ||
        clz == Int::class.java

    private fun describeSignature(method: Method): String {
        val sb = StringBuilder()
        sb.append(if (Modifier.isStatic(method.modifiers)) "static " else "instance ")
        sb.append(method.returnType.simpleName).append(' ').append(method.name).append('(')
        method.parameterTypes.forEachIndexed { i, p ->
            if (i > 0) sb.append(", ")
            sb.append(p.simpleName)
        }
        sb.append(')')
        return sb.toString()
    }

    /**
     * 解析后的可调用方法描述.
     */
    data class Resolved(
        val handle: MethodHandle,
        val method: Method,
        val isStatic: Boolean,
        /** Composer+changed[+default] 段的长度 (2 或 3). */
        val tailArgCount: Int,
        /** 是否含 $default bitmask 段 (tailArgCount == 3). */
        val hasDefaultBitmask: Boolean,
        val parameterTypes: Array<Class<*>>,
    )

    data class InvokeResult(val ok: Boolean, val errorMessage: String?) {
        companion object {
            fun ok() = InvokeResult(true, null)
            fun err(msg: String) = InvokeResult(false, msg)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ComposableInvoker::class.java)
        private val UNSET = Any()
    }
}
