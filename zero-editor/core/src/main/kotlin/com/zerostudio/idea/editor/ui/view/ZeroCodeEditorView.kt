package com.zerostudio.idea.editor.ui.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import com.zerostudio.idea.editor.core.buffer.paging.VirtualPageManager
import com.zerostudio.idea.editor.core.cursor.ZeroMultiCursorModel
import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.lsp.diagnostic.LspDiagnosticController
import com.zerostudio.idea.editor.lsp.highlight.LspDocumentHighlightController
import com.zerostudio.idea.editor.lsp.model.LspCapabilitiesHandler
import com.zerostudio.idea.editor.lsp.sync.LspDocumentSyncManager
import com.zerostudio.idea.editor.lsp.window.LspHoverTooltipWindow
import com.zerostudio.idea.editor.lsp.window.LspInlineRenameWindow
import com.zerostudio.idea.editor.lsp.window.LspQuickFixWindow
import com.zerostudio.idea.editor.lsp.window.LspSignatureHelpWindow
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.style.builtin.MoveCursorAnimator
import kotlinx.coroutines.*

class ZeroCodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CodeEditor(context, attrs, defStyleAttr) {

    val multiCursorModel = ZeroMultiCursorModel { text }
    var pageManager: VirtualPageManager? = null

    // --- LSP 寄宿子系统 ---
    var activeConnector: ZeroLspConnector? = null

    val diagnosticController by lazy { LspDiagnosticController(this) }
    val highlightController by lazy { LspDocumentHighlightController(this, { activeConnector }) }
    val signatureHelpWindow by lazy { LspSignatureHelpWindow(this, { activeConnector }) }
    val hoverTooltipWindow by lazy {
        LspHoverTooltipWindow(this, { activeConnector }) { line, col ->
            jumpToDefinition(line, col)
        }
    }
    val quickFixWindow by lazy {
        LspQuickFixWindow(this, diagnosticController) {
            activeConnector?.let { LspCapabilitiesHandler(it) }
        }
    }
    val inlineRenameWindow by lazy { LspInlineRenameWindow(this, { activeConnector }) }
    val syncManager by lazy { LspDocumentSyncManager(this, { activeConnector }) }

    init {
        setCursorAnimator(MoveCursorAnimator(this))
        isCursorAnimationEnabled = true
        props.deleteEmptyLineFast = true
        props.autoCompletionOnComposing = true

        // 挂载高亮、增量文档同步与诊断联动
        highlightController.attach()
        syncManager.attachIncrementalListener()
    }

    /**
     * 绑定语言服务器连接实例
     */
    fun attachLspConnector(connector: ZeroLspConnector, languageId: String) {
        this.activeConnector = connector
        syncManager.notifyDidOpen(languageId)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // F2: 原地符号重命名
            if (event.keyCode == KeyEvent.KEYCODE_F2) {
                inlineRenameWindow.startRenameAtCursor()
                return true
            }
            // Alt + Enter: 快速修复 Quick Fix
            if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.isAltPressed) {
                quickFixWindow.showQuickFixMenuAtCursor()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 覆写单行绘制尾部回调，注入不同色彩的波浪线光栅绘制
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawLspDiagnostics(canvas)
    }

    private fun drawLspDiagnostics(canvas: Canvas) {
        val rowHeight = rowHeight.toFloat()
        val visibleStartRow = firstVisibleRow
        val visibleEndRow = lastVisibleRow

        canvas.save()
        canvas.translate(-offsetX.toFloat(), -offsetY.toFloat())

        for (row in visibleStartRow..visibleEndRow) {
            if (row >= layout.rowCount) break
            val rowObj = layout.getRowAt(row)
            val lineIdx = rowObj.lineIndex
            val rowBottom = getRowBottom(row).toFloat()
            val baseline = rowBottom - (rowHeight * 0.2f)

            diagnosticController.drawDiagnosticWaveLines(
                canvas = canvas,
                lineIndex = lineIdx,
                contentStartCol = rowObj.startColumn,
                contentEndCol = rowObj.endColumn,
                baselineY = baseline,
                textRegionOffset = measureTextRegionOffset()
            )
        }
        canvas.restore()
    }

    fun jumpToDefinition(line: Int, column: Int) {
        val connector = activeConnector ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val defs = connector.getDefinitions(line, column)
            if (defs.isNotEmpty()) {
                val loc = defs.first()
                setSelectionRegion(
                    loc.range.start.line,
                    loc.range.start.character,
                    loc.range.end.line,
                    loc.range.end.character
                )
                ensurePositionVisible(loc.range.start.line, loc.range.start.character)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        signatureHelpWindow.release()
        hoverTooltipWindow.release()
        quickFixWindow.release()
        inlineRenameWindow.release()
        highlightController.detach()
        syncManager.release()
    }
}