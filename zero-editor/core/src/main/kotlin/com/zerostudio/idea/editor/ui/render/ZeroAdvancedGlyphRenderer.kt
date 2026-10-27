package com.zerostudio.idea.editor.ui.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import io.github.rosemoe.sora.graphics.Paint as SoraPaint

/**
 * 工业级高密微图形渲染器：负责所有非打印字符、控制符微标签与缩进引导线的纯硬件 Canvas 光栅化
 */
class ZeroAdvancedGlyphRenderer(private val prefs: EditorPreferencesState) {

    private val tagBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33808080") // 半透明弱化底色
    }

    private val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B0B0B0")
        textSize = 22f
        typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true
    }

    private val whitespaceDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#44888888")
    }

    private val indentGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        color = Color.parseColor("#26888888")
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val tempRect = RectF()

    /**
     * 判断是否为 Unicode 特殊不可见符号
     */
    fun getSpecialUnicodeLabel(c: Char): String? = when (c) {
        '\u200B' -> "ZWSP" // Zero-Width Space
        '\u200C' -> "ZWNJ" // Zero-Width Non-Joiner
        '\u200D' -> "ZWJ"  // Zero-Width Joiner
        '\u00A0' -> "NBSP" // Non-Breaking Space
        '\u200E' -> "LRM"  // Left-to-Right Mark
        '\u200F' -> "RLM"  // Right-to-Left Mark
        '\uFEFF' -> "BOM"  // Byte Order Mark
        else -> null
    }

    /**
     * 渲染控制字符与特殊字符的圆角标签（例如 [NUL], [ZWSP]）
     */
    fun drawControlTag(
        canvas: Canvas,
        label: String,
        left: Float,
        baselineY: Float,
        rowHeight: Float
    ): Float {
        val textWidth = tagTextPaint.measureText(label)
        val paddingHorizontal = 6f
        val tagWidth = textWidth + paddingHorizontal * 2
        val tagHeight = rowHeight * 0.65f
        val top = baselineY - tagHeight + 4f
        val bottom = baselineY + 4f

        tempRect.set(left, top, left + tagWidth, bottom)
        canvas.drawRoundRect(tempRect, 4f, 4f, tagBackgroundPaint)

        // 垂直居中绘制文字
        val textY = top + (tagHeight / 2f) - ((tagTextPaint.descent() + tagTextPaint.ascent()) / 2f)
        canvas.drawText(label, left + paddingHorizontal, textY, tagTextPaint)

        return tagWidth
    }

    /**
     * 智能空白符渲染：根据连续性与设置决定是否跳过孤立空格
     */
    fun drawWhitespace(
        canvas: Canvas,
        chars: CharSequence,
        index: Int,
        x: Float,
        centerY: Float,
        charWidth: Float
    ) {
        if (!prefs.showWhitespaceCharacters) return

        val ch = chars[index]
        if (ch == ' ') {
            // 检查“隐藏单个空格”策略：前后若无连续空白符，则静默不画
            if (prefs.hideIsolatedSingleSpaces) {
                val hasLeftSpace = index > 0 && chars[index - 1] == ' '
                val hasRightSpace = index + 1 < chars.length && chars[index + 1] == ' '
                if (!hasLeftSpace && !hasRightSpace) return
            }

            // 绘制居中小圆点
            val radius = charWidth * 0.12f
            canvas.drawCircle(x + charWidth / 2f, centerY, radius, whitespaceDotPaint)
        } else if (ch == '\t') {
            // 制表符绘制微箭头横虚线
            val startX = x + 4f
            val endX = x + charWidth - 4f
            canvas.drawLine(startX, centerY, endX, centerY, whitespaceDotPaint)
            // 绘制箭头折线
            canvas.drawLine(endX - 4f, centerY - 3f, endX, centerY, whitespaceDotPaint)
            canvas.drawLine(endX - 4f, centerY + 3f, endX, centerY, whitespaceDotPaint)
        }
    }

    /**
     * 绘制缩进参考虚线
     */
    fun drawIndentGuides(
        canvas: Canvas,
        leadingSpaces: Int,
        tabWidthPx: Float,
        lineTop: Float,
        lineBottom: Float,
        contentStartX: Float
    ) {
        if (!prefs.showIndentGuides || leadingSpaces <= 0) return

        val indentLevel = leadingSpaces / prefs.tabStopSize
        for (level in 1..indentLevel) {
            val guideX = contentStartX + (level * prefs.tabStopSize * tabWidthPx)
            canvas.drawLine(guideX, lineTop, guideX, lineBottom, indentGuidePaint)
        }
    }
}