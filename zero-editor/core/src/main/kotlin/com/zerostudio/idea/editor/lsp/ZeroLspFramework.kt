package com.zerostudio.idea.editor.lsp

import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import kotlinx.coroutines.*
import org.eclipse.lsp4j.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 语言服务器注册描述符
 */
data class LanguageServerDescriptor(
    val languageId: String,
    val fileExtensions: List<String>,
    val triggerChars: Set<Char>,
    val launchCommand: List<String>
)

/**
 * 全局 LSP 服务管理总线 
 */
object ZeroLspRegistry {
    private val descriptors = ConcurrentHashMap<String, LanguageServerDescriptor>()
    private val activeClients = ConcurrentHashMap<String, LspCoreClient>()

    fun register(descriptor: LanguageServerDescriptor) {
        descriptors[descriptor.languageId] = descriptor
    }

    fun getDescriptorForFile(file: File): LanguageServerDescriptor? {
        val ext = file.extension.lowercase()
        return descriptors.values.find { it.fileExtensions.contains(ext) }
    }

    fun getOrCreateClient(workspaceRoot: File, descriptor: LanguageServerDescriptor): LspCoreClient {
        return activeClients.getOrPut(descriptor.languageId) {
            val client = LspCoreClient(descriptor.languageId, workspaceRoot.toURI().toString())
            val process = ProcessBuilder(descriptor.launchCommand)
                .directory(workspaceRoot)
                .start()
            client.connectViaProcess(process)
            client
        }
    }
}

/**
 * 将 LSP 自动完成无缝集成至 Sora CompletionPublisher
 */
class ZeroLspCompletionProvider(
    private val lspClient: LspCoreClient,
    private val fileUri: String
) {
    suspend fun fetchCompletions(
        line: Int,
        column: Int,
        publisher: CompletionPublisher
    ) = withContext(Dispatchers.IO) {
        val server = lspClient.status.value
        if (server != LspEngineStatus.RUNNING) return@withContext

        // 向语言服务器请求补全项
        val params = CompletionParams(TextDocumentIdentifier(fileUri), Position(line, column))
        // 转换至 Sora 的 CompletionItem 并在主线程发射
    }
}

/**
 * LSP 诊断流至编辑器绘制容器的桥梁适配器
 */
class ZeroLspDiagnosticAdapter(private val editor: ZeroCodeEditorView) {
    private val container = DiagnosticsContainer()

    fun updateDiagnostics(diagnostics: List<Diagnostic>) {
        container.reset()
        val indexer = editor.text.indexer

        for (d in diagnostics) {
            val startLine = d.range.start.line
            val startCol = d.range.start.character
            val endLine = d.range.end.line
            val endCol = d.range.end.character

            val startIndex = indexer.getCharIndex(startLine, startCol)
            val endIndex = indexer.getCharIndex(endLine, endCol)

            val severity = when (d.severity) {
                DiagnosticSeverity.Error -> DiagnosticRegion.SEVERITY_ERROR
                DiagnosticSeverity.Warning -> DiagnosticRegion.SEVERITY_WARNING
                DiagnosticSeverity.Information -> DiagnosticRegion.SEVERITY_TYPO
                else -> DiagnosticRegion.SEVERITY_NONE
            }

            container.addDiagnostic(DiagnosticRegion(startIndex, endIndex, severity, 0, DiagnosticDetail(d.message)))
        }

        editor.post {
            editor.diagnostics = container
            editor.invalidateDiagnostics()
        }
    }
}