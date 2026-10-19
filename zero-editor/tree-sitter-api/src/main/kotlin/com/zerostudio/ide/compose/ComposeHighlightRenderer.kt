package com.zerostudio.ide.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.zerostudio.ide.treesitter.HighlightSpanArray
import com.zerostudio.ide.treesitter.tsh.TshCompiler

/**
 * @author android_zero
 *
 * 变更说明：
 * 完美对应 5 整型步长的扁平数组渲染，直接解码 RGBA 全色域与复合字体样式。
 */
object ComposeHighlightRenderer {

    fun buildAnnotatedString(
        fullText: String,
        spans: HighlightSpanArray,
        actionResolver: ((actionId: Int) -> String?)? = null
    ): AnnotatedString {
        val builder = AnnotatedString.Builder(fullText)

        spans.forEachSpan { startByte, endByte, packedStyle, priority, actionId ->
            if (startByte < 0 || endByte > fullText.length || startByte >= endByte) return@forEachSpan

            // 解码 RGBA 全色域
            val r = (packedStyle ushr 24) and 0xFF
            val g = (packedStyle ushr 16) and 0xFF
            val b = (packedStyle ushr 8) and 0xFF
            val a = packedStyle and 0xFF
            val color = Color(r, g, b, a)

            // 解码字体样式
            val isBold = (priority and TshCompiler.FLAG_BOLD) != 0
            val isItalic = (priority and TshCompiler.FLAG_ITALIC) != 0
            val isUnderline = (priority and TshCompiler.FLAG_UNDERLINE) != 0
            val isStrikethrough = (priority and TshCompiler.FLAG_STRIKETHROUGH) != 0

            val textDecoration = when {
                isUnderline && isStrikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                isUnderline -> TextDecoration.Underline
                isStrikethrough -> TextDecoration.LineThrough
                else -> TextDecoration.None
            }

            builder.addStyle(
                style = SpanStyle(
                    color = color,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = textDecoration
                ),
                start = startByte,
                end = endByte
            )

            // 如果携带交互动作，打上 Compose URL / Annotation 标记
            if (actionId > 0 && actionResolver != null) {
                val actionTag = actionResolver(actionId)
                if (actionTag != null) {
                    builder.addStringAnnotation(
                        tag = "IDE_ACTION",
                        annotation = actionTag,
                        start = startByte,
                        end = endByte
                    )
                }
            }
        }

        return builder.toAnnotatedString()
    }
}