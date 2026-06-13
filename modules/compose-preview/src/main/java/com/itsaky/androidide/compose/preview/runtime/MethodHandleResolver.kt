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
import java.util.concurrent.ConcurrentHashMap

/**
 * 反射 -> MethodHandle 解析器.
 *
 * 取代旧的 [ComposableRenderer] 中直接 `Method.invoke` 的方式:
 * - **MethodHandle 比反射 invoke 快 5x~10x**(JIT 优化, 避免每次 boxing 数组分配).
 * - 缓存 `(Class, functionName) -> (MethodHandle, isStatic)`, 首次查找后 O(1) 命中.
 *
 * 用法:
 * ```kotlin
 * val resolved = resolver.resolve(clazz, "MyComposable")
 * // 静态: resolved.handle.invokeWithArguments(composer, 0)
 * // 实例: resolved.handle.invokeWithArguments(instance, composer, 0)
 * ```
 */
class MethodHandleResolver {

    private val cache = ConcurrentHashMap<CacheKey, Resolved>()

    fun resolve(clazz: Class<*>, functionName: String): Resolved? {
        val key = CacheKey(clazz, functionName)
        cache[key]?.let { return it }
        val resolved = doResolve(clazz, functionName)
        if (resolved != null) {
            cache[key] = resolved
        }
        return resolved
    }

    fun invalidate(clazz: Class<*>? = null) {
        if (clazz == null) {
            cache.clear()
        } else {
            cache.keys.removeAll { it.clazz == clazz }
        }
    }

    private fun doResolve(clazz: Class<*>, functionName: String): Resolved? {
        val methods = clazz.declaredMethods
        val lookup = MethodHandles.lookup()

        // 1) 精确匹配
        methods.firstOrNull { it.name == functionName }?.let { method ->
            return wrapMethod(lookup, method)
        }
        // 2) Kotlin lambdas: funName$lambda, funName$1, funName$default
        val candidates = methods.filter { m ->
            !m.name.contains("\$default") &&
                (m.name.startsWith("$functionName\$") || m.name == "${functionName}\$lambda")
        }
        // 选参数最少的 (最接近 @Composable 原始签名)
        return candidates.minByOrNull { it.parameterCount }?.let { wrapMethod(lookup, it) }
    }

    private fun wrapMethod(lookup: MethodHandles.Lookup, method: Method): Resolved {
        method.isAccessible = true
        val handle = lookup.unreflect(method)
        return Resolved(handle = handle, method = method, isStatic = Modifier.isStatic(method.modifiers))
    }

    private data class CacheKey(val clazz: Class<*>, val functionName: String)

    data class Resolved(
        val handle: MethodHandle,
        val method: Method,
        val isStatic: Boolean
    )

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandleResolver::class.java)
    }
}
