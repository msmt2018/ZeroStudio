/*
 *  ZeroStudio IDE - 自定义编辑器渲染器
 *
 *  不修改 sora-editor 源码 (composite-builds/build-deps/editor/.../EditorRenderer.java),
 *  而是在项目内 editor/impl 模块继承 EditorRenderer, 重写行号列相关方法,
 *  把断点列直接绘制到编辑器 Canvas 上 (与行号 1:1 同步)。
 *
 *  优势 (相比 BreakpointColumnView overlay View):
 *    - 完美同步: 行号、行高、滚动、缩放全部由 EditorRenderer 内部状态驱动,
 *      不需要手动 layoutToMatchLineColumn
 *    - 性能更好: 同一 Canvas 同一渲染 pass, 无额外 View invalidate
 *    - 无 overlay 吞事件问题: 触摸由 IDEEditor.onTouchEvent 统一路由
 *
 *  绘制位置:
 *    断点列位于行号列左侧, 宽度 = 行高 * 0.6。
 *    断点圆点绘制在断点列中心, 与行号垂直对齐。
 *
 *  数据来源:
 *    通过 [BreakpointGutterDelegate] 接口由 core/app 桥接断点数据,
 *    editor/impl 不依赖 core/app。
 */

package com.itsaky.androidide.editor.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorRenderer
import com.itsaky.androidide.editor.ui.gutter.BreakpointGutterDelegate
import com.itsaky.androidide.editor.ui.gutter.BreakpointGutterStates

/**
 * IDE 自定义渲染器, 在 [EditorRenderer] 基础上增加断点列绘制。
 *
 * @param editorRef CodeEditor 实例 (EditorRenderer.editor 是 private, 需自存引用)
 * @param delegateProvider 返回当前 [BreakpointGutterDelegate], null 时不绘制断点列
 */
class IDEEditorRenderer(
    private val editorRef: CodeEditor,
    private val delegateProvider: () -> BreakpointGutterDelegate?,
) : EditorRenderer(editorRef) {

    private val density = editorRef.resources.displayMetrics.density

    // 画笔
    private val bpFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bpOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.WHITE
    }
    private val bpHitRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val hitLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val columnBgPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val tmpRect = RectF()
    private var columnBgShader: Shader? = null

    /** 断点列宽度 = 行高 * 0.6 (随缩放同步) */
    private val bpColumnWidth: Float
        get() = editorRef.rowHeight * 0.6f

    /** 断点圆点半径 = 行高 * 0.18 (随缩放同步) */
    private val bpRadius: Float
        get() = editorRef.rowHeight * 0.18f

    /**
     * 断点列左边缘 X 坐标 (屏幕坐标)。
     * 断点列紧贴行号列左侧。
     * 当行号 pinned 时, 行号区域起点 = 0; 否则 = -offsetX。
     */
    fun getBreakpointColumnLeft(): Float {
        // 注意: CodeEditor.wordwrap 字段是 private, getter 是 isWordwrap()。
        // Kotlin 属性访问 .wordwrap 会尝试访问 private 字段而编译失败,
        // 必须显式调用 .isWordwrap() 方法。
        // 另外 getOffsetX()/getRowHeight() 返回 int, 需 toFloat() 参与浮点运算。
        val lnRegionStart = if (editorRef.isLineNumberPinned && !editorRef.isWordwrap()) {
            0f
        } else {
            -editorRef.offsetX.toFloat()
        }
        return Math.max(0f, lnRegionStart - bpColumnWidth)
    }

    /** 断点列右边缘 X 坐标 (= 行号区域左边缘)。 */
    fun getBreakpointColumnRight(): Float {
        return getBreakpointColumnLeft() + bpColumnWidth
    }

    /** 断点列是否可见 (宽度 > 0 且右边缘在屏幕内)。 */
    fun isBreakpointColumnVisible(): Boolean {
        return bpColumnWidth > 0f && getBreakpointColumnRight() > 0f
    }

    /**
     * 判断触摸点是否落在断点列区域内。
     * 供 IDEEditor.onTouchEvent 调用做事件路由。
     */
    fun isPointInBreakpointColumn(x: Float): Boolean {
        if (!isBreakpointColumnVisible()) return false
        val left = getBreakpointColumnLeft()
        val right = getBreakpointColumnRight()
        return x >= left && x <= right
    }

    // ---- 重写 EditorRenderer 方法 ----

    /**
     * 绘制行号列背景时, 同时绘制断点列背景 + 命中行高亮。
     * 断点列背景在行号列左侧, 使用淡渐变让列在视觉上分离。
     */
    override fun drawLineNumberBackground(
        canvas: Canvas,
        offsetX: Float,
        width: Float,
        color: Int,
    ) {
        super.drawLineNumberBackground(canvas, offsetX, width, color)

        val delegate = delegateProvider() ?: return
        if (delegate.currentFile() == null) return
        if (!isBreakpointColumnVisible()) return

        val bpLeft = getBreakpointColumnLeft()
        val bpRight = getBreakpointColumnRight()
        if (bpRight <= 0f) return

        // 1. 断点列淡渐变背景
        ensureColumnBgShader()
        columnBgShader?.let { shader ->
            columnBgPaint.shader = shader
            canvas.drawRect(bpLeft, 0f, bpRight, editorRef.height.toFloat(), columnBgPaint)
        }

        // 2. 命中行水平高亮
        val hitLine = delegate.hitLine()
        if (hitLine >= 0) {
            drawHitLineHighlight(canvas, hitLine, bpLeft, bpRight)
        }
    }

    /**
     * 绘制单个行号时, 同时绘制该行的断点状态图标。
     * 断点圆点位于断点列中心, 与行号垂直居中对齐。
     */
    override fun drawLineNumber(
        canvas: Canvas,
        line: Int,
        row: Int,
        offsetX: Float,
        width: Float,
        color: Int,
    ) {
        super.drawLineNumber(canvas, line, row, offsetX, width, color)

        val delegate = delegateProvider() ?: return
        if (delegate.currentFile() == null) return

        val state = delegate.breakpointStateForLine(line)
        if (state == BreakpointGutterStates.NONE) return

        if (!isBreakpointColumnVisible()) return

        // 断点圆点中心
        val cx = getBreakpointColumnLeft() + bpColumnWidth / 2f
        val rowTop = editorRef.getRowTop(row) - editorRef.offsetY
        val rowBottom = editorRef.getRowBottom(row) - editorRef.offsetY
        val cy = (rowTop + rowBottom) / 2f
        val r = bpRadius

        // 超出可见区域则跳过
        if (cy < -r || cy > editorRef.height + r) return

        drawBreakpointGlyph(canvas, cx, cy, r, state)
    }

    // ---- 内部绘制方法 ----

    private fun ensureColumnBgShader() {
        val h = editorRef.height
        if (h <= 0) return
        if (columnBgShader == null) {
            columnBgShader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                0x1A000000, 0x00000000, Shader.TileMode.CLAMP,
            )
        }
    }

    private fun drawHitLineHighlight(
        canvas: Canvas,
        line: Int,
        left: Float,
        right: Float,
    ) {
        // getRowHeight() 返回 int, 转 Float 以保证后续浮点运算 (tmpRect.set 需要 Float)
        val rowHeight = editorRef.rowHeight.toFloat()
        if (rowHeight <= 0f) return
        val firstRow = Math.max(0, editorRef.firstVisibleRow)
        if (line < firstRow) return

        val top = (line - firstRow) * rowHeight
        val bottom = top + rowHeight
        if (bottom < 0f || top > editorRef.height) return

        // 1. 整行淡色背景 (8% 透明橙)
        hitLinePaint.color = 0x22FF6F00
        tmpRect.set(left, top, right, bottom)
        canvas.drawRect(tmpRect, hitLinePaint)

        // 2. 整行贯穿水平条
        val barHeight = 2f * density
        tmpRect.set(
            left,
            top + (rowHeight - barHeight) / 2f,
            right,
            top + (rowHeight + barHeight) / 2f,
        )
        hitLinePaint.color = 0xFFFF6F00.toInt()
        canvas.drawRoundRect(tmpRect, density, density, hitLinePaint)
    }

    private fun drawBreakpointGlyph(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        state: Int,
    ) {
        // 填充色按状态
        val fillColor = when (state) {
            BreakpointGutterStates.HIT -> 0xFFE53935.toInt()
            BreakpointGutterStates.NORMAL -> 0xFFE53935.toInt()
            BreakpointGutterStates.VERIFIED -> 0xFF43A047.toInt()
            BreakpointGutterStates.CONDITION -> 0xFFFFB300.toInt()
            BreakpointGutterStates.LOG -> 0xFF8D6E63.toInt()
            BreakpointGutterStates.DISABLED -> 0xFF9E9E9E.toInt()
            BreakpointGutterStates.INVALID -> 0xFFB71C1C.toInt()
            BreakpointGutterStates.EXCEPTION -> 0xFFB71C1C.toInt()
            BreakpointGutterStates.FIELD_WATCHPOINT -> 0xFF43A047.toInt()
            BreakpointGutterStates.METHOD -> 0xFFFFB300.toInt()
            BreakpointGutterStates.DEPENDENT -> 0xFFAB47BC.toInt()
            BreakpointGutterStates.TEMPORARY -> 0xFFFF6F00.toInt()
            else -> 0xFFE53935.toInt()
        }
        bpFillPaint.color = fillColor
        canvas.drawCircle(cx, cy, r, bpFillPaint)

        // 白色描边
        bpOutlinePaint.color = adjustAlpha(Color.WHITE, 0.85f)
        canvas.drawCircle(cx, cy, r, bpOutlinePaint)

        when (state) {
            BreakpointGutterStates.HIT -> {
                // 命中: 外圈光环
                bpHitRingPaint.color = adjustAlpha(Color.WHITE, 0.6f)
                canvas.drawCircle(cx, cy, r + 3f * density, bpHitRingPaint)
            }
            BreakpointGutterStates.DISABLED -> {
                // 禁用: 斜线
                bpOutlinePaint.color = Color.WHITE
                bpOutlinePaint.strokeWidth = 2f * density
                canvas.drawLine(cx - r, cy + r, cx + r, cy - r, bpOutlinePaint)
            }
            BreakpointGutterStates.CONDITION -> {
                // 条件: 菱形
                bpOutlinePaint.color = Color.WHITE
                bpOutlinePaint.strokeWidth = 1.2f * density
                canvas.drawLine(cx, cy - r * 0.5f, cx + r * 0.5f, cy, bpOutlinePaint)
                canvas.drawLine(cx + r * 0.5f, cy, cx, cy + r * 0.5f, bpOutlinePaint)
                canvas.drawLine(cx, cy + r * 0.5f, cx - r * 0.5f, cy, bpOutlinePaint)
                canvas.drawLine(cx - r * 0.5f, cy, cx, cy - r * 0.5f, bpOutlinePaint)
            }
            BreakpointGutterStates.LOG -> {
                // 日志点: 三条横线 + 竖线
                bpOutlinePaint.color = Color.WHITE
                bpOutlinePaint.strokeWidth = 1.2f * density
                val armW = r * 0.6f
                val armH = r * 0.35f
                canvas.drawLine(cx - armW, cy - armH, cx + armW, cy - armH, bpOutlinePaint)
                canvas.drawLine(cx - armW, cy, cx + armW, cy, bpOutlinePaint)
                canvas.drawLine(cx, cy - r * 0.6f, cx, cy + r * 0.6f, bpOutlinePaint)
            }
            BreakpointGutterStates.TEMPORARY -> {
                // 临时: 十字
                bpOutlinePaint.color = Color.WHITE
                bpOutlinePaint.strokeWidth = 1.3f * density
                canvas.drawLine(cx, cy - r * 0.65f, cx, cy + r * 0.65f, bpOutlinePaint)
                canvas.drawLine(cx - r * 0.65f, cy, cx + r * 0.65f, cy, bpOutlinePaint)
            }
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = Math.round(Color.alpha(color) * factor)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
