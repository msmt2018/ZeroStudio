package com.zerostudio.idea.editor.lsp.window

import android.graphics.Typeface
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.*
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier

class LspInlineRenameWindow(
    private val editor: ZeroCodeEditorView,
    private val connectorProvider: () -> ZeroLspConnector?
) : EditorPopupWindow(editor, FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val editInput: EditText
    private val applyBtn: Button
    private val rootLayout: LinearLayout

    private var targetLine: Int = 0
    private var targetCol: Int = 0

    init {
        val dp = editor.dpUnit
        rootLayout = LinearLayout(editor.context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(editor.colorScheme.getColor(EditorColorScheme.COMPLETION_WND_BACKGROUND))
                setStroke((1.5f * dp).toInt(), editor.colorScheme.getColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BORDER))
            }
            elevation = 16 * dp
        }

        editInput = EditText(editor.context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            setTextColor(editor.colorScheme.getColor(EditorColorScheme.TEXT_NORMAL))
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    commitRename()
                    true
                } else false
            }
        }

        applyBtn = Button(editor.context).apply {
            text = "重命名"
            textSize = 12f
            setOnClickListener { commitRename() }
        }

        rootLayout.addView(editInput)
        rootLayout.addView(applyBtn)
        setContentView(rootLayout)
    }

    fun startRenameAtCursor() {
        val cursor = editor.cursor.left()
        targetLine = cursor.line
        targetCol = cursor.column

        scope.launch {
            val connector = connectorProvider() ?: return@launch
            val server = connector.getRemoteServer() ?: return@launch

            // 1. prepareRename 预校验
            val isValid = withContext(Dispatchers.IO) {
                val params = PrepareRenameParams(TextDocumentIdentifier(connector.fileUri), Position(targetLine, targetCol))
                runCatching {
                    val result = server.textDocumentService.prepareRename(params).get()
                    result != null
                }.getOrDefault(true)
            }

            if (!isValid) return@launch

            // 提取当前光标下的词作为初始重命名值
            val wordRange = editor.getWordRange(targetLine, targetCol, true)
            val currentWord = if (wordRange != null) {
                val s = wordRange.start
                val e = wordRange.end
                editor.text.subSequence(s.index, e.index).toString()
            } else ""

            editInput.setText(currentWord)
            editInput.selectAll()

            val offset = editor.getCharLayoutOffset(targetLine, targetCol)
            val x = offset[1] + editor.measureTextRegionOffset()
            val y = offset[0] - editor.rowHeight - (editor.dpUnit * 52)

            setSize((editor.width * 0.75f).toInt(), (48 * editor.dpUnit).toInt())
            setLocationAbsolutely(maxOf(16, x.toInt() - (width / 3)), maxOf(16, y.toInt()))

            show()
            editInput.requestFocus()
        }
    }

    private fun commitRename() {
        val newName = editInput.text.toString().trim()
        if (newName.isBlank()) return

        scope.launch {
            connectorProvider()?.renameSymbol(targetLine, targetCol, newName)
            dismiss()
        }
    }

    fun release() {
        scope.cancel()
        dismiss()
    }
}