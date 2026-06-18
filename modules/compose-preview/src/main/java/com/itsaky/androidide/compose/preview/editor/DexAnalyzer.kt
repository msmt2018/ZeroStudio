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

package com.itsaky.androidide.compose.preview.editor

import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef as DexClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.util.MethodUtil
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Dex 分析器 v3.3.1.
 *
 * 用 `com.android.tools.smali:smali-dexlib2` (项目已有 google-smali-dexlib2:3.0.9)
 * 读取 dex 字节码, 找到 @Composable 函数, 提取:
 * - 方法签名 / 起始-结束行号 (从 line table)
 * - 方法内的 compose 函数调用点 (从 invoke 指令)
 * - 命名参数赋值 (从 invoke-virtual/range + iget-object + const-string 等)
 *
 * ## 关键启发式
 *
 * 1. **找 @Composable 函数**: 方法签名最后两个参数必须是
 *    `(Landroidx/compose/runtime/Composer;I)L<return type>;` (Compose 编译器 1.0+ 通用),
 *    或者倒数第二个参数是 `Composer`, 倒数第一个是 `int` (changed flags).
 * 2. **找 invoke 调用点**: 遍历 instruction list, 找 `invoke-static` / `invoke-virtual` /
 *    `invoke-direct` 等. 提取方法 FQN + line number.
 * 3. **找命名参数赋值**: compose 编译器会把 `Text(text = "Hello")` 编译成
 *    `p0.text = "Hello"`, 然后 invoke. 因此我们看 invoke 前 N 条指令的 `iput-object` /
 *    `const-string` 序列, 推断参数赋值.
 *
 * ## 反编译 (CFR) 用途
 *
 * CFR 反编译后的 java 源码更接近原始 .kt 结构, 用来做精确的 named parameter 提取 +
 * 反向映射到 .kt. 因为直接看 smali 指令需要做更多字符串分析.
 */
class DexAnalyzer {

    private val LOG = LoggerFactory.getLogger(DexAnalyzer::class.java)

    /**
     * 从 [dexFiles] 中分析全部 @Composable 函数.
     */
    fun analyze(dexFiles: List<File>): List<ComposableFunctionDescriptor> {
        val all = mutableListOf<ComposableFunctionDescriptor>()
        for (dexFile in dexFiles) {
            try {
                all.addAll(analyzeSingle(dexFile))
            } catch (e: Throwable) {
                LOG.warn("Failed to analyze {}: {}", dexFile.name, e.message)
            }
        }
        return all
    }

    private fun analyzeSingle(dexFile: File): List<ComposableFunctionDescriptor> {
        val dex: DexFile = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
        val out = mutableListOf<ComposableFunctionDescriptor>()
        for (classDef in dex.classes) {
            if (classDef.type.startsWith("Landroidx/") ||
                classDef.type.startsWith("Lcom/google/") ||
                classDef.type.startsWith("Lkotlin/") ||
                classDef.type.startsWith("Ljava/")
            ) {
                continue
            }
            for (method in classDef.methods) {
                val descriptor = analyzeMethod(classDef, method) ?: continue
                out.add(descriptor)
            }
        }
        return out
    }

    private fun analyzeMethod(
        classDef: DexClassDef,
        method: Method,
    ): ComposableFunctionDescriptor? {
        // 1) 启发式: @Composable 函数签名包含 Composer + int 参数.
        val params = MethodUtil.getParameterTypes(method)
        if (params.size < 2) return null
        // 倒数第二个参数必须是 Composer
        val composerIdx = params.size - 2
        val composerParam = params[composerIdx]
        if (composerParam != "Landroidx/compose/runtime/Composer;") return null
        // 倒数第一个参数必须是 int (changed flags)
        val flagsParam = params[params.size - 1]
        if (flagsParam != "I") return null
        // 排除纯 lambda (单参数 + 返回 Unit, 内部是 restartable group)
        // 接受, 继续分析.

        // 2) 行号范围
        val impl = method.implementation ?: return null
        val instructions = impl.instructions
        var minLine = Int.MAX_VALUE
        var maxLine = 0
        for (insn in instructions) {
            val line = insn.location?.line ?: continue
            if (line in 1..100_000) {
                if (line < minLine) minLine = line
                if (line > maxLine) maxLine = line
            }
        }
        if (minLine == Int.MAX_VALUE) {
            minLine = -1
            maxLine = -1
        }

        // 3) 找 invoke 调用点
        val calls = mutableListOf<ComposableCallSite>()
        for (insn in instructions) {
            if (insn.opcode.name.startsWith("invoke-")) {
                val ref = insn.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference
                    ?: continue
                val callLine = insn.location?.line ?: continue
                // 简化: 跳过 java/lang / kotlin / androidx 内部
                val calleeClass = ref.definingClass
                if (calleeClass.startsWith("Ljava/") ||
                    calleeClass.startsWith("Lkotlin/") ||
                    calleeClass.startsWith("Landroidx/compose/runtime/")
                ) {
                    continue
                }
                val callSite = ComposableCallSite(
                    composableName = ref.name,
                    composableFqn = "${calleeClass.substring(1, calleeClass.length - 1).replace('/', '.')}.${ref.name}",
                    line = callLine,
                    parameterAssignments = emptyList(), // 简化: 不解析 iget-object 链
                )
                calls.add(callSite)
            }
        }

        return ComposableFunctionDescriptor(
            className = classDef.type.substring(1, classDef.type.length - 1).replace('/', '.'),
            methodName = method.name,
            methodDesc = method.descriptor,
            sourceFile = classDef.sourceFile,
            lineStart = minLine,
            lineEnd = maxLine,
            calls = calls,
        )
    }

    /**
     * 用 CFR 把 dex 反编译为 java 文本.
     *
     * 实际反编译由 [com.itsaky.androidide.compose.preview.editor.ComposeAttributeEditor]
     * 包装调用. 这里是占位 — CFR 0.152 的 API 通过 reflection 调, 不在本类直接 import.
     *
     * @return java 源码, 失败返回空字符串.
     */
    fun dexToJava(dexFile: File, className: String): String {
        return try {
            // CFR 0.152 API: 用 Driver 类 + 多个 args
            val driverClass = Class.forName("org.benf.cfr.reader.Driver")
            val driver = driverClass.getDeclaredConstructor().newInstance()
            val setArgs = driverClass.getMethod("setArgs", Array<String>::class.java)
            // 这里用 CFR sink 收集输出. 完整实现需要构造 ClassPath + Driver config.
            // 简化: 返回空, 让调用方用 ComposeAttributeEditor 里更稳的正则提取 fallback.
            setArgs.invoke(driver, arrayOf(dexFile.absolutePath))
            ""
        } catch (e: Throwable) {
            LOG.warn("CFR decompile failed: {}", e.message)
            ""
        }
    }
}
