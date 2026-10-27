package com.zerostudio.idea.editor.lsp

import com.zerostudio.idea.editor.ui.view.VirtualCodeEditorView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.eclipse.lsp4j.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 现代 LSP 与编辑器双向管道网桥
 */
class ZeroLspEditorBridge(
    val editor: VirtualCodeEditorView,
    val lspClient: LspCoreClient,
    val fileUri: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val documentVersion = AtomicInteger(1)
    private val triggerCharacters = setOf('.', ':', '>', '-', '/', '@')

    // 当前文件的诊断容器
    private val diagnosticsContainer = DiagnosticsContainer()

    fun attach() {
        // 发送 didOpen 协议
        scope.launch(Dispatchers.IO) {
            val textContent = editor.text.toString()
            val openParams = DidOpenTextDocumentParams(
                TextDocumentItem(fileUri, "plaintext", documentVersion.get(), textContent)
            )
            // lspClient.notify("textDocument/didOpen", openParams)
        }

        // 挂载编辑器内容修改事件，触发非阻塞增量 didChange
        editor.subscribeAlways(ContentChangeEvent::class.java) { event ->
            val version = documentVersion.incrementAndGet()
            val start = event.changeStart
            val end = event.changeEnd

            val range = Range(
                Position(start.line, start.column),
                Position(end.line, end.column)
            )

            val changeContent = event.changedText.toString()
            scope.launch(Dispatchers.IO) {
                val changeEvent = TextDocumentContentChangeEvent(range, 0, changeContent)
                val params = DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(fileUri, version),
                    listOf(changeEvent)
                )
                // lspClient.notify("textDocument/didChange", params)
            }
        }

        // 3. 挂载触发表符自动补全 (如键入 '.' 自动拉起建议)
        editor.subscribeAlways(ContentChangeEvent::class.java) { event ->
            if (event.action == ContentChangeEvent.ACTION_INSERT && event.changedText.length == 1) {
                val ch = event.changedText.first()
                if (triggerCharacters.contains(ch)) {
                    val pos = event.changeEnd
                    requestCompletion(pos.line, pos.column, CompletionTriggerKind.TriggerCharacter, ch.toString())
                }
            }
        }

        // 监听诊断报告流并映射至 Sora 绘制容器
        scope.launch {
            lspClient.diagnosticsFlow.collectLatest { report ->
                if (report.uri == fileUri) {
                    applyDiagnosticsToEditor(report.diagnostics)
                }
            }
        }
    }

    private fun requestCompletion(line: Int, column: Int, kind: CompletionTriggerKind, triggerChar: String? = null) {
        scope.launch(Dispatchers.IO) {
            val params = CompletionParams(
                TextDocumentIdentifier(fileUri),
                Position(line, column),
                CompletionContext(kind, triggerChar)
            )
            // 请求服务端自动完成并刷新浮层 UI...
        }
    }

    /**
     * 将 LSP 诊断等级映射并写入 Sora 引擎，实时绘制错误/警告下划线
     */
    private fun applyDiagnosticsToEditor(diagnostics: List<Diagnostic>) {
        diagnosticsContainer.reset()
        for (diag in diagnostics) {
            val startLine = diag.range.start.line
            val startCol = diag.range.start.character
            val endLine = diag.range.end.line
            val endCol = diag.range.end.character

            val indexer = editor.text.indexer
            val startIndex = indexer.getCharIndex(startLine, startCol)
            val endIndex = indexer.getCharIndex(endLine, endCol)

            val severity = when (diag.severity) {
                DiagnosticSeverity.Error -> DiagnosticRegion.SEVERITY_ERROR
                DiagnosticSeverity.Warning -> DiagnosticRegion.SEVERITY_WARNING
                DiagnosticSeverity.Information -> DiagnosticRegion.SEVERITY_TYPO
                else -> DiagnosticRegion.SEVERITY_NONE
            }

            val detail = DiagnosticDetail(diag.message)
            diagnosticsContainer.addDiagnostic(DiagnosticRegion(startIndex, endIndex, severity, 0, detail))
        }

        editor.diagnostics = diagnosticsContainer
        editor.invalidateDiagnostics()
    }

    fun detach() {
        scope.cancel()
    }
}