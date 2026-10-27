package com.zerostudio.idea.editor.lsp.inlay

import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.lsp.bridge.getRemoteServer
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.event.InlayHintClickEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import kotlinx.coroutines.*
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier

class LspInlayHintController(
    private val editor: ZeroCodeEditorView,
    private val connectorProvider: () -> ZeroLspConnector?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private var fetchJob: Job? = null
    private val container = InlayHintsContainer()

    fun attach() {
        // 滚动停顿与页面更新后自动请求当前可视范围的内联提示
        editor.subscribeAlways(ScrollEvent::class.java) {
            triggerInlayHintsDebounced()
        }

        // 监听内联提示点击事件
        editor.subscribeAlways(InlayHintClickEvent::class.java) { event ->
            val hint = event.inlayHint
            if (hint is TextInlayHint) {
                // 点击内联参数/类型提示时，弹出简短提示或阻断默认光标跳跃
                editor.toast("Inlay Hint: ${hint.text.trim()}")
                event.intercept()
            }
        }
    }

    fun triggerInlayHintsDebounced() {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            delay(300) // 300ms 防抖
            val connector = connectorProvider() ?: return@launch
            val server = connector.getRemoteServer() ?: return@launch

            val startLine = editor.firstVisibleLine.coerceAtLeast(0)
            val endLine = (editor.lastVisibleLine + 5).coerceAtMost(editor.lineCount)

            val hints = withContext(Dispatchers.IO) {
                val range = Range(Position(startLine, 0), Position(endLine, 0))
                val params = InlayHintParams(TextDocumentIdentifier(connector.fileUri), range)

                runCatching {
                    server.textDocumentService.inlayHint(params).get()
                }.getOrNull() ?: emptyList()
            }

            container.clear()
            for (hint in hints) {
                val label = when {
                    hint.label.isLeft -> hint.label.left
                    hint.label.isRight -> hint.label.right.joinToString("") { it.value }
                    else -> ""
                }
                container.add(TextInlayHint(hint.position.line, hint.position.character, ": $label"))
            }

            editor.setInlayHints(container)
            editor.invalidateInlayHints()
            editor.invalidate()
        }
    }

    fun detach() {
        fetchJob?.cancel()
        scope.cancel()
        container.clear()
    }
}