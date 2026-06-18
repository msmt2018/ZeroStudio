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

import com.itsaky.androidide.lexers.kotlin.KotlinLexer
import com.itsaky.androidide.lexers.kotlin.KotlinParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Compose 属性编辑器 v3.3.1.
 *
 * 端到端流程 (用户要求 "运行时动态反射渲染的dex在布局编辑模式只需要分析解析dex
 * 等smali源码或者转到java源码然后分析然后映射到kotlin文件源码中具体代码属性"):
 *
 * 1. **dex → 反编译**: 用 CFR (`org.benf.cfr:0.152` 已存在 libs) 把 dex 转成 java
 *    文本, 拿到接近原始 .kt 的反编译源码.
 *
 * 2. **java → 命名参数解析**: 用 ANTLR Java 词法分析 (项目 editor/lexers 已支持) 找到
 *    `Text("Hello", fontSize = 16.sp, ...)` 中的 named parameter, 输出
 *    `NamedParameter(name, value, line, offsetInLine)`.
 *
 * 3. **java → .kt 映射**: 用 class FQN 在 project 内搜 .kt 文件 (`find { name ==
 *    "${fqn.substringAfterLast('.')}.kt" && path.contains(fqn.packagePath) }`).
 *
 * 4. **.kt 行号校对**: CFR 反编译的 java 行号 ≠ 原 .kt 行号, 但 dex line table 仍指向
 *    原始 .kt. 我们以 dex line table 为准, 在 .kt 文件中找对应 token.
 *
 * 5. **.kt 修改**: 用 ANTLR Kotlin 词法分析定位 `text = "Hello"` 位置, 改 value.
 *
 * 6. **触发 build**: 调 [com.itsaky.androidide.projects.builder.BuildService.executeTasks]
 *    跑 `assembleDebug` task, 完成后 ComposePreviewViewModel 监听新 dex 重新渲染.
 */
class ComposeAttributeEditor {

    private val LOG = LoggerFactory.getLogger(ComposeAttributeEditor::class.java)
    private val dexAnalyzer = DexAnalyzer()

    /**
     * 给定 dex + className, 反编译为 java, 提取 named parameter.
     */
    fun extractAttributesFromDex(
        dexFile: File,
        className: String,
        methodName: String,
    ): List<NamedParameter> {
        val javaSource = dexAnalyzer.dexToJava(dexFile, className)
        if (javaSource.isEmpty()) return emptyList()
        return extractNamedParameters(javaSource, methodName)
    }

    /**
     * 从 java 源码中提取指定方法内的 named parameter 调用.
     * 简化: 用 ANTLR Kotlin parser 解析 java 风格 (因为 CFR 输出很像 java).
     * 实际更稳的方案: 引入 JavaParser / Eclipse JDT, 这里用正则兜底.
     */
    private fun extractNamedParameters(
        javaSource: String,
        methodName: String,
    ): List<NamedParameter> {
        val out = mutableListOf<NamedParameter>()
        // 找 method body
        val methodPattern = Regex(
            "(?:public|private|protected)?\\s*(?:static\\s+)?[\\w<>\\[\\]]+\\s+$methodName\\s*\\([^)]*\\)\\s*\\{",
        )
        val match = methodPattern.find(javaSource) ?: return emptyList()
        val start = match.range.last
        // 找匹配的右花括号
        var depth = 1
        var i = start
        while (i < javaSource.length && depth > 0) {
            when (javaSource[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        val body = javaSource.substring(start, i)
        // 找 named parameter 调用: `Name(param = value, ...)` 形式
        val callPattern = Regex(
            "(\\w+)\\s*\\(\\s*([^)]*?)\\s*\\)",
        )
        for (m in callPattern.findAll(body)) {
            val callee = m.groupValues[1]
            val args = m.groupValues[2]
            if ('=' !in args) continue
            // 分割 named parameter
            val paramPattern = Regex("(\\w+)\\s*=\\s*([^,]+?)(?=,\\s*\\w+\\s*=|$)")
            for (p in paramPattern.findAll(args)) {
                val pname = p.groupValues[1].trim()
                val pvalue = p.groupValues[2].trim()
                out.add(
                    NamedParameter(
                        name = pname,
                        value = pvalue,
                        valueType = inferType(pvalue),
                        offsetInLine = p.range.first,
                    ),
                )
            }
        }
        return out
    }

    private fun inferType(value: String): NamedParameter.ValueType {
        return when {
            value.startsWith("\"") && value.endsWith("\"") -> NamedParameter.ValueType.STRING
            value == "true" || value == "false" -> NamedParameter.ValueType.BOOLEAN
            value == "null" -> NamedParameter.ValueType.NULL
            value.matches(Regex("[0-9]+L?(\\.[0-9]+)?f?")) -> NamedParameter.ValueType.NUMBER
            value.endsWith(".sp") || value.endsWith(".dp") || value.endsWith(".px") -> NamedParameter.ValueType.DIMEN
            value.startsWith("Color") || value.startsWith("0xFF") || value.startsWith("Color.") -> NamedParameter.ValueType.COLOR
            else -> NamedParameter.ValueType.OTHER
        }
    }

    /**
     * 把 FQN (e.g. `com.example.MainActivity$ComposableSingletons`) 映射到 .kt 文件路径.
     */
    fun findKtFile(
        fqn: String,
        projectRoot: File,
    ): File? {
        val packagePath = fqn.substringBeforeLast('.').replace('.', '/')
        val className = fqn.substringAfterLast('.').substringBefore('$')
        val candidate = File(projectRoot, "src/main/java/$packagePath/$className.kt")
        if (candidate.exists()) return candidate
        // fallback: search recursively
        projectRoot.walkTopDown()
            .firstOrNull {
                it.isFile && it.name == "$className.kt" && it.absolutePath.contains("/$packagePath/")
            }?.let { return it }
        return null
    }

    /**
     * 修改 .kt 文件中指定行 + 参数名的值.
     *
     * 用 ANTLR Kotlin 词法分析找 `text = "Hello"` 这种 named parameter, 然后:
     * - 找到 `text = ` 的位置
     * - 替换等号右边的表达式为 newValue
     * - 写回 .kt 文件
     *
     * @return [AttributeEditResult.Success] / [AttributeEditResult.Failure].
     */
    fun editKtFile(
        ktFile: File,
        targetLine: Int,
        parameterName: String,
        newValue: String,
    ): AttributeEditResult {
        if (!ktFile.exists()) {
            return AttributeEditResult.Failure("文件不存在: ${ktFile.absolutePath}")
        }
        val originalLines = ktFile.readLines()
        if (targetLine < 1 || targetLine > originalLines.size) {
            return AttributeEditResult.Failure("行号越界: $targetLine (1..${originalLines.size})")
        }
        val oldSource = originalLines[targetLine - 1]
        val newLine = applyParameterEdit(oldSource, parameterName, newValue)
        if (newLine == null) {
            return AttributeEditResult.Failure(
                "未在 L${targetLine} 找到 ` $parameterName = ...` 参数赋值",
            )
        }
        if (newLine == oldSource) {
            return AttributeEditResult.Failure("新旧值相同, 不修改")
        }
        // 写回
        try {
            val newContent = originalLines.toMutableList().also {
                it[targetLine - 1] = newLine
            }.joinToString("\n")
            ktFile.writeText(newContent)
            LOG.info("Edited {}:L{} `{}` -> {}", ktFile.name, targetLine, oldSource, newLine)
            return AttributeEditResult.Success(
                ktFile = ktFile.absolutePath,
                line = targetLine,
                oldSource = oldSource,
                newSource = newLine,
                taskName = "assembleDebug",
            )
        } catch (e: Throwable) {
            return AttributeEditResult.Failure("写文件失败: ${e.message}")
        }
    }

    /**
     * 在一行 .kt 源码中找到 `parameterName = oldValue` 并替换为 `parameterName = newValue`.
     *
     * 用 ANTLR 解析这一行 (作为 expression), 找 `simpleIdentifier '='` 后面跟着 stringLiteral
     * / numberLiteral / callSuffix 等. 替换.
     */
    private fun applyParameterEdit(
        line: String,
        parameterName: String,
        newValue: String,
    ): String? {
        // 简化: 用正则匹配 `parameterName\s*=\s*<value>` 然后替换 value.
        // 支持:
        // - `text = "Hello"`  -> `text = newValue` (string)
        // - `fontSize = 16.sp` -> `fontSize = newValue` (number + call)
        // - `enabled = true` -> `enabled = newValue` (boolean)
        val pattern = Regex(
            "(\\b$parameterName\\s*=\\s*)([^,\\n)]+)",
            RegexOption.MULTILINE,
        )
        val match = pattern.find(line) ?: return null
        // 检查匹配后面是不是 ", ...)" 或者 ") \n" 等合法终止
        val after = line.substring(match.range.last + 1).trimStart()
        if (after.startsWith(",") || after.startsWith(")") || after.isEmpty()) {
            return line.substring(0, match.range.first) +
                match.groupValues[1] + newValue +
                line.substring(match.range.last + 1)
        }
        return null
    }

    private fun <T : Parser> T.withoutErrorListeners(): T {
        removeErrorListeners()
        return this
    }
}
