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

package com.itsaky.androidide.compose.preview.data.source

import com.itsaky.androidide.lexers.kotlin.KotlinLexer
import com.itsaky.androidide.lexers.kotlin.KotlinParser
import com.itsaky.androidide.lexers.kotlin.KotlinParserBaseListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.slf4j.LoggerFactory

/**
 * @Composable 函数抽取器 v3.3.
 *
 * 复用 `editor/lexers` 中生成的 ANTLR Kotlin 语法树, 找到全部
 * `funDeclaration`, 然后检查:
 * 1. 是否有 `@Composable` 标注 (modifier 列表中包含 `Composable`)
 * 2. 参数类型列表 (从 functionValueParameters 抽取)
 *
 * 不用 TreeSitterSymbolResolver 是因为:
 * - TreeSitterSymbolResolver 输出 SymbolInfo 表达 code outline, 没有
 *   "该函数是不是 @Composable" 的语义.
 * - ComposableSymbolExtractor 专做"找 @Composable", 跟 TreeSitterSymbolResolver
 *   解耦, 单独可单元测试.
 *
 * @author android_zero
 */
class ComposableSymbolExtractor {

    private val LOG = LoggerFactory.getLogger(ComposableSymbolExtractor::class.java)

    /**
     * 从 .kt 源码中抽取全部 @Composable 函数. 按行号排序.
     *
     * 如果源码为空 / 解析失败, 返回空列表.
     */
    fun extract(source: String): List<ComposableFunctionInfo> {
        if (source.isBlank()) return emptyList()
        return try {
            val lexer = KotlinLexer(CharStreams.fromString(source))
            val tokens = CommonTokenStream(lexer)
            val parser = KotlinParser(tokens).withoutErrorListeners()
            val collector = ComposableFunctionCollector(source)
            ParseTreeWalker.DEFAULT.walk(collector, parser.kotlinFile())
            collector.functions.sortedBy { it.line }
        } catch (e: Throwable) {
            LOG.warn("Failed to extract @Composable functions: {}", e.message)
            emptyList()
        }
    }

    private class ComposableFunctionCollector(
        private val source: String,
    ) : KotlinParserBaseListener() {

        val functions = mutableListOf<ComposableFunctionInfo>()

        override fun enterFunctionDeclaration(ctx: KotlinParser.FunctionDeclarationContext) {
            // 1) 函数名
            val name = ctx.identifier()?.text ?: return

            // 2) modifier 列表 — 检查是否有 @Composable (或 @Preview)
            val modifierList = ctx.modifierList()?.cleanedText().orEmpty()
            val hasComposable = modifierList.contains("@Composable")
            if (!hasComposable) return
            val hasPreview = modifierList.contains("@Preview")

            // 3) 参数列表 — 从 functionValueParameters 拿每个参数的 type
            //    functionValueParameter -> parameter -> type
            val paramCtx = ctx.functionValueParameters()
            val parameterTypes = paramCtx?.functionValueParameter().orEmpty()
                .mapNotNull { it.parameter()?.type()?.text?.cleanWhitespace() }
                .filter { it.isNotBlank() }

            // 4) 签名 — 用 "fun name(p1: T1, p2: T2)" 格式
            val paramSignature = parameterTypes
                .mapIndexed { idx, t -> "p$idx: $t" }
                .joinToString(", ")
            val signature = "fun $name($paramSignature)"

            // 5) 行号 (1-based, 用户看起来更直观)
            val line = ctx.start.line

            functions += ComposableFunctionInfo(
                name = name,
                signature = signature,
                line = line,
                parameterTypes = parameterTypes,
                hasPreviewAnnotation = hasPreview,
            )
        }

        private fun String.cleanWhitespace(): String =
            replace(Regex("\\s+"), " ").trim()
    }

    private fun <T : Parser> T.withoutErrorListeners(): T {
        removeErrorListeners()
        return this
    }
}
