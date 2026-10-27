package com.zerostudio.idea.editor.lsp.highlight

import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import kotlinx.coroutines.*
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

class LspDocumentHighlightController(
    private val editor: ZeroCodeEditorView,
    private val connectorProvider: () -> ZeroLspConnector?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private var debounceJob: Job? = null
    private val highlightContainer = HighlightTextContainer()

    // 读访问引用：半透明蓝绿底色
    private val readBgColor = ConstColor(0x334CAF50)
    private val readBorderColor = ConstColor(0x664CAF50)

    // 写访问引用：半透明橙红底色（强调赋值/修改点）
    private val writeBgColor = ConstColor(0x44FF5722)
    private val writeBorderColor = ConstColor(0x88FF5722)

    fun attach() {
        editor.subscribeAlways(SelectionChangeEvent::class.java) { event ->
            if (event.isSelected || editor.isInMouseMode) {
                clearHighlights()
                return@subscribeAlways
            }
            val pos = event.left
            triggerHighlightDebounced(pos.line, pos.column)
        }
    }

    private fun triggerHighlightDebounced(line: Int, column: Int) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(250) // 250ms 防抖，避免打字移动光标时发起海量 RPC
            val connector = connectorProvider() ?: return@launch
            val server = connector.getRemoteServer() ?: return@launch

            val highlights = withContext(Dispatchers.IO) {
                val params = DocumentHighlightParams(
                    TextDocumentIdentifier(connector.fileUri),
                    Position(line, column)
                )
                runCatching {
                    server.textDocumentService.documentHighlight(params).get()
                }.getOrNull() ?: emptyList()
            }

            applyHighlights(highlights)
        }
    }

    private fun applyHighlights(highlights: List<DocumentHighlight>) {
        highlightContainer.clear()
        if (highlights.isEmpty()) {
            editor.setHighlightTexts(highlightContainer)
            editor.invalidateHighlightTexts()
            return
        }

        for (h in highlights) {
            val isWrite = h.kind == DocumentHighlightKind.Write
            val bg = if (isWrite) writeBgColor else readBgColor
            val border = if (isWrite) writeBorderColor else readBorderColor

            highlightContainer.add(
                HighlightTextContainer.HighlightText(
                    startLine = h.range.start.line,
                    startColumn = h.range.start.character,
                    endLine = h.range.end.line,
                    endColumn = h.range.end.character,
                    color = bg,
                    borderColor = border
                )
            )
        }

        editor.setHighlightTexts(highlightContainer)
        editor.invalidateHighlightTexts()
        editor.invalidate()
    }

    fun clearHighlights() {
        debounceJob?.cancel()
        if (!highlightContainer.isEmpty()) {
            highlightContainer.clear()
            editor.setHighlightTexts(highlightContainer)
            editor.invalidateHighlightTexts()
            editor.invalidate()
        }
    }

    fun detach() {
        debounceJob?.cancel()
        scope.cancel()
        clearHighlights()
    }
}