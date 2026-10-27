package com.zerostudio.idea.editor.ui.minimap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.zerostudio.idea.editor.ui.view.VirtualCodeEditorView

/**
 * 极简硬件加速小地图光栅化引擎
 */
class ZeroMinimapRenderer(private val editor: VirtualCodeEditorView) {

    private val minimapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val viewportBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#804A90E2")
    }
    private val viewportFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1A4A90E2")
    }

    private var backingBitmap: Bitmap? = null
    private var pixelCanvas: Canvas? = null
    private val dstRect = RectF()
    private val viewportRect = RectF()

    companion object {
        const val CHAR_WIDTH_PX = 2
        const val CHAR_HEIGHT_PX = 4
        const val MINIMAP_WIDTH_DP = 70f
    }

    /**
     * 重建小地图点阵（低频或大修改时异步触发）
     */
    fun updateMinimapBitmap(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return

        if (backingBitmap == null || backingBitmap!!.width != widthPx || backingBitmap!!.height != heightPx) {
            backingBitmap?.recycle()
            backingBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            pixelCanvas = Canvas(backingBitmap!!)
        }

        val canvas = pixelCanvas ?: return
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        val text = editor.text
        val maxLines = minOf(text.lineCount, heightPx / CHAR_HEIGHT_PX)
        minimapPaint.color = Color.parseColor("#55888888")

        // 将每行代码以微色块方式投影在位图上
        for (lineIdx in 0 until maxLines) {
            val lineLength = minOf(text.getColumnCount(lineIdx), widthPx / CHAR_WIDTH_PX)
            val top = (lineIdx * CHAR_HEIGHT_PX).toFloat()
            val bottom = top + (CHAR_HEIGHT_PX - 1)

            for (colIdx in 0 until lineLength) {
                val ch = text.charAt(lineIdx, colIdx)
                if (ch != ' ' && ch != '\t') {
                    val left = (colIdx * CHAR_WIDTH_PX).toFloat()
                    canvas.drawRect(left, top, left + CHAR_WIDTH_PX, bottom, minimapPaint)
                }
            }
        }
    }

    /**
     * 实时绘制至宿主 Canvas 并投影当前屏幕视口窗口
     */
    fun draw(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        val bitmap = backingBitmap ?: return
        val minimapWidth = (MINIMAP_WIDTH_DP * editor.dpUnit).toInt()
        val left = (viewWidth - minimapWidth).toFloat()

        dstRect.set(left, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        canvas.drawBitmap(bitmap, null, dstRect, null)

        // 计算当前主视口可见区间指示方框
        val totalLines = maxOf(1, editor.text.lineCount)
        val visibleLines = editor.height / editor.rowHeight
        val firstVisibleLine = editor.firstVisibleLine

        val factor = viewHeight.toFloat() / (totalLines * CHAR_HEIGHT_PX).coerceAtLeast(viewHeight)
        val indicatorTop = (firstVisibleLine * CHAR_HEIGHT_PX) * factor
        val indicatorHeight = (visibleLines * CHAR_HEIGHT_PX) * factor

        viewportRect.set(left, indicatorTop, viewWidth.toFloat(), indicatorTop + indicatorHeight)
        canvas.drawRoundRect(viewportRect, 4f, 4f, viewportFillPaint)
        canvas.drawRoundRect(viewportRect, 4f, 4f, viewportBorderPaint)
    }

    fun release() {
        backingBitmap?.recycle()
        backingBitmap = null
        pixelCanvas = null
    }
}