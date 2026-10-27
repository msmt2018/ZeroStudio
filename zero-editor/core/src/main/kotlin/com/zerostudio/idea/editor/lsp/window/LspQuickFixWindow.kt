package com.zerostudio.idea.editor.lsp.window

import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.lsp.diagnostic.LspDiagnosticController
import com.zerostudio.idea.editor.lsp.model.LspCapabilitiesHandler
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.*
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class LspQuickFixWindow(
    private val editor: ZeroCodeEditorView,
    private val diagnosticController: LspDiagnosticController,
    private val capabilitiesHandlerProvider: () -> LspCapabilitiesHandler?
) : EditorPopupWindow(editor, FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED or FEATURE_HIDE_WHEN_FAST_SCROLL) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val listView: ListView

    init {
        listView = ListView(editor.context).apply {
            dividerHeight = 1
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = editor.dpUnit * 8
                setColor(editor.colorScheme.getColor(EditorColorScheme.COMPLETION_WND_BACKGROUND))
                setStroke((1 * editor.dpUnit).toInt(), editor.colorScheme.getColor(EditorColorScheme.COMPLETION_WND_CORNER))
            }
        }
        setContentView(listView)
    }

    /**
     * 在光标处主动触发快速修复菜单 (如快捷键 Alt + Enter)
     */
    fun showQuickFixMenuAtCursor() {
        val cursor = editor.cursor.left()
        val charIdx = cursor.index
        val diag = diagnosticController.findDiagnosticAt(charIdx)
        val range = diag?.range ?: Range(Position(cursor.line, cursor.column), Position(cursor.line, cursor.column))

        scope.launch {
            val handler = capabilitiesHandlerProvider() ?: return@launch
            val actions = handler.getCodeActions(range, if (diag != null) listOf(diag) else emptyList())

            if (actions.isNotEmpty()) {
                displayActions(actions)
            }
        }
    }

    private fun displayActions(actions: List<CodeAction>) {
        val titles = actions.map { "💡 ${it.title}" }
        val adapter = ArrayAdapter(editor.context, android.R.layout.simple_list_item_1, titles)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val action = actions[position]
            scope.launch {
                capabilitiesHandlerProvider()?.applyCodeAction(action)
                dismiss()
            }
        }

        val cursor = editor.cursor.left()
        val offset = editor.getCharLayoutOffset(cursor.line, cursor.column)
        val x = offset[1] + editor.measureTextRegionOffset()
        val y = offset[0] + (editor.dpUnit * 6)

        setSize((editor.width * 0.7f).toInt(), (actions.size * 45 * editor.dpUnit).toInt().coerceAtMost(300))
        setLocationAbsolutely(x.toInt().coerceAtMost(editor.width - width - 16), y.toInt())

        if (!isShowing) show()
    }

    fun release() {
        scope.cancel()
        dismiss()
    }
}