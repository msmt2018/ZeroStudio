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
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.Method
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Dex → 源码 v3.3.1 (简化后, 单方法直读).
 *
 * ## 设计原则 (用户反馈驱动)
 *
 * 用户反馈: "dex转java本质上是直接支持dex to java源码的, 所以有些步骤比较多余"
 *
 * 原来 v3.3.1 拆分了两步:
 *   1. [analyze] 用 `smali-dexlib2` 读 dex 字节码, 找 @Composable 函数 + invoke 调用点
 *   2. [dexToJava] 用 CFR 反编译
 *
 * 实际上 [analyze] 的结果 (ComposableFunctionDescriptor / ComposableCallSite) 从未被
 * UI 使用, 完全是死代码. 整个 v3.3.1 调用链只有一处真正用到了 dex→源码:
 *
 *   [ComposeAttributeEditor.extractAttributesFromDex]
 *     └→ [dexToJava] 拿源码
 *     └→ 在文本里找 methodName, 提 named parameter
 *
 * ## 简化
 *
 * v3.3.1 简化版**只保留一个公共方法 [dexToJava]**: dex 文件 → 完整源码.
 *
 * ## 实现说明
 *
 * CFR 0.152 不能直接读 dex (它只吃 JVM class file, magic 0xCAFEBABE; dex 是
 * magic 0x6465780A "dex\n"). 因此这里**直接用 smali-dexlib2 读 dex** (这是
 * JVM 生态中唯一稳定的 dex 读取库), 然后手动遍历每个 class 的 method +
 * instruction 输出**类 java 助记符文本** (smali 风格).
 *
 * 文本格式:
 * ```
 * // ---- <FQN> ----
 * .class <access> L<FQN>;
 * .super <FQN>;
 * # methods
 * .method <access> <name>(<args>)<ret>
 *   .registers N
 *   .line K
 *   const-string vX, "value"
 *   invoke-* {vX, ...}, L<callee>;-><method>(...)...
 * .end method
 * ```
 *
 * 后续 [ComposeAttributeEditor] 拿这个文本后, 在指定 methodName 的 method 体内
 * 找 `const-string` / `const/high16` 等指令, 反推 named parameter 值.
 */
class DexAnalyzer {

    private val LOG = LoggerFactory.getLogger(DexAnalyzer::class.java)

    /**
     * 把 dex 文件反编译为源码.
     *
     * @param dexFile 当前 preview 用的 dex 文件.
     * @return 全部类的源码 (smali 风格). 多个类用 `// ---- <FQN> ----` 分隔.
     *         失败时返回空字符串.
     */
    fun dexToJava(dexFile: File): String {
        if (!dexFile.exists() || dexFile.length() == 0L) {
            LOG.warn("dexToJava: file missing or empty: {}", dexFile.absolutePath)
            return ""
        }
        return try {
            val dex: DexFile = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
            val out = StringBuilder()
            var classCount = 0
            for (classDef in dex.classes) {
                val fqn = classDef.type
                    .removePrefix("L")
                    .removeSuffix(";")
                    .replace('/', '.')
                if (out.isNotEmpty()) out.append("\n\n")
                out.append("// ---- ").append(fqn).append(" ----\n")
                renderClass(out, classDef)
                classCount++
            }
            if (classCount == 0) {
                LOG.warn("dexToJava: dex contains no classes: {}", dexFile.name)
                ""
            } else {
                out.toString()
            }
        } catch (e: Throwable) {
            LOG.warn("dexToJava failed for {}: {}", dexFile.name, e.message)
            ""
        }
    }

    /**
     * 渲染单个 class 为 smali 风格文本.
     */
    private fun renderClass(out: StringBuilder, classDef: ClassDef) {
        out.append(".class ").append(classDef.accessFlags).append(" ")
            .append(classDef.type).append("\n")
        out.append(".super ").append(classDef.superclass).append("\n")
        if (!classDef.interfaces.isNullOrEmpty()) {
            for (iface in classDef.interfaces) {
                out.append(".implements ").append(iface).append("\n")
            }
        }
        if (classDef.sourceFile != null) {
            out.append(".source \"").append(classDef.sourceFile).append("\"\n")
        }

        // fields
        for (field in classDef.fields) {
            out.append(".field ").append(field.accessFlags).append(" ")
                .append(field.name).append(" ").append(field.type).append("\n")
        }

        // methods
        for (method in classDef.methods) {
            renderMethod(out, method)
        }
    }

    /**
     * 渲染单个 method 为 smali 风格文本.
     */
    private fun renderMethod(out: StringBuilder, method: Method) {
        // 构造方法签名: name(p1 p2 ...)returnType
        val params = method.parameterTypes.joinToString("")
        out.append("\n.method ").append(method.accessFlags).append(" ")
            .append(method.name).append("(").append(params).append(")")
            .append(method.returnType).append("\n")
        val impl = method.implementation
        if (impl == null) {
            out.append(".end method\n")
            return
        }
        out.append("    .registers ").append(impl.registerCount).append("\n")
        for (insn in impl.instructions) {
            val opcode = insn.opcode.name
            val operand = when (val ref = insn.reference) {
                null -> ""
                is com.android.tools.smali.dexlib2.iface.reference.StringReference -> "\"${ref.string}\""
                is com.android.tools.smali.dexlib2.iface.reference.MethodReference ->
                    "${ref.definingClass}->${ref.name}${ref.parameterTypes}"
                is com.android.tools.smali.dexlib2.iface.reference.FieldReference ->
                    "${ref.definingClass}->${ref.name}:${ref.type}"
                is com.android.tools.smali.dexlib2.iface.reference.TypeReference ->
                    ref.type
                else -> ref.toString()
            }
            // 注: smali-dexlib2 v3.0.9 的 Instruction.getLocation() 不在基础接口上
            // (只在 OffsettedInstruction), 这里省去 .line 指令以保证编译通过.
            // .line 信息可以通过 debug info 单独遍历拿到, 简化版暂不处理.
            out.append("    ").append(opcode)
            if (operand.isNotEmpty()) out.append(" ").append(operand)
            out.append("\n")
        }
        out.append(".end method\n")
    }
}
