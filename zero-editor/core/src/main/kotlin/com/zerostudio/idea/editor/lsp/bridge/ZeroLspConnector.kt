package com.zerostudio.idea.editor.lsp.bridge

import com.zerostudio.idea.editor.lsp.model.LspServerInstance
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.lang.completion.CompletionItem as SoraCompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.text.CharPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer

class ZeroLspConnector(
    val instance: LspServerInstance,
    val editorView: ZeroCodeEditorView,
    val fileUri: String
) {
    private var languageServer: LanguageServer? = null
    private val diagnosticsContainer = DiagnosticsContainer()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        instance.start()
        val connection = instance.activeConnection ?: return@withContext

        val client = object : LanguageClient {
            override fun telemetryEvent(`object`: Any?) {}
            override fun publishDiagnostics(params: PublishDiagnosticsParams) {
                if (params.uri == fileUri) {
                    applyDiagnostics(params.diagnostics)
                }
            }
            override fun showMessage(messageParams: MessageParams) {
                instance.addLog(com.zerostudio.idea.editor.lsp.model.MessageSource.LSP, messageParams.type, messageParams.message)
            }
            override fun showMessageRequest(requestParams: ShowMessageRequestParams) = null
            override fun logMessage(message: MessageParams) {
                instance.addLog(com.zerostudio.idea.editor.lsp.model.MessageSource.LSP, message.type, message.message)
            }
        }

        val launcher = Launcher.createLauncher(client, LanguageServer::class.java, connection.inputStream, connection.outputStream)
        languageServer = launcher.remoteProxy
        launcher.startListening()

        val initParams = InitializeParams().apply {
            rootUri = instance.projectRoot.toURI().toString()
            capabilities = ClientCapabilities().apply {
                textDocument = TextDocumentClientCapabilities().apply {
                    formatting = FormattingCapabilities(true)
                    rename = RenameCapabilities(true).apply { prepareSupport = true }
                    definition = DefinitionCapabilities()
                    references = ReferencesCapabilities()
                    completion = CompletionCapabilities()
                }
            }
        }
        languageServer?.initialize(initParams)?.get()
        languageServer?.initialized(InitializedParams())
    }

    // 1. 跳转定义
    suspend fun getDefinitions(line: Int, column: Int): List<Location> = withContext(Dispatchers.IO) {
        val server = languageServer ?: return@withContext emptyList()
        val params = DefinitionParams(TextDocumentIdentifier(fileUri), Position(line, column))
        val result = server.textDocumentService.definition(params).get()
        return@withContext if (result.isLeft) result.left else result.right.map { Location(it.targetUri, it.targetRange) }
    }

    // 2. 查找所有引用
    suspend fun getReferences(line: Int, column: Int): List<Location> = withContext(Dispatchers.IO) {
        val server = languageServer ?: return@withContext emptyList()
        val params = ReferenceParams().apply {
            textDocument = TextDocumentIdentifier(fileUri)
            position = Position(line, column)
            context = ReferenceContext(true)
        }
        return@withContext server.textDocumentService.references(params).get() ?: emptyList()
    }

    // 3. 重命名符号与应用 WorkspaceEdit
    suspend fun renameSymbol(line: Int, column: Int, newName: String) = withContext(Dispatchers.IO) {
        val server = languageServer ?: return@withContext
        val params = RenameParams(TextDocumentIdentifier(fileUri), Position(line, column), newName)
        val edit = server.textDocumentService.rename(params).get() ?: return@withContext

        withContext(Dispatchers.Main) {
            val changes = edit.changes ?: emptyMap()
            val textEdits = changes[fileUri] ?: return@withContext
            // 逆序执行，确保偏移有效
            textEdits.sortedByDescending { it.range.start.line * 10000 + it.range.start.character }.forEach { textEdit ->
                val sLine = textEdit.range.start.line
                val sCol = textEdit.range.start.character
                val eLine = textEdit.range.end.line
                val eCol = textEdit.range.end.character
                editorView.text.replace(sLine, sCol, eLine, eCol, textEdit.newText)
            }
        }
    }

    // 4. 文档全量格式化
    suspend fun formatDocument() = withContext(Dispatchers.IO) {
        val server = languageServer ?: return@withContext
        val options = FormattingOptions(editorView.tabWidth, !editorView.editorLanguage.useTab())
        val params = DocumentFormattingParams(TextDocumentIdentifier(fileUri), options)
        val edits = server.textDocumentService.formatting(params).get() ?: return@withContext

        withContext(Dispatchers.Main) {
            edits.sortedByDescending { it.range.start.line * 10000 + it.range.start.character }.forEach { textEdit ->
                editorView.text.replace(
                    textEdit.range.start.line,
                    textEdit.range.start.character,
                    textEdit.range.end.line,
                    textEdit.range.end.character,
                    textEdit.newText
                )
            }
        }
    }

    // 5. 自动补全映射至 Sora CompletionPublisher
    suspend fun requestCompletions(pos: CharPosition, publisher: CompletionPublisher) = withContext(Dispatchers.IO) {
        val server = languageServer ?: return@withContext
        val params = CompletionParams(TextDocumentIdentifier(fileUri), Position(pos.line, pos.column))
        val response = server.textDocumentService.completion(params).get() ?: return@withContext

        val items: List<CompletionItem> = when {
            response.isLeft -> response.left
            response.isRight -> response.right.items
            else -> emptyList()
        }

        val soraItems = items.map { item ->
            val insert = item.insertText ?: item.textEdit?.let {
                if (it.isLeft) it.left.newText else null
            } ?: item.label

            SimpleCompletionItem(item.label, item.detail ?: "", null, insert)
        }

        withContext(Dispatchers.Main) {
            publisher.addItems(soraItems)
            publisher.updateList(true)
        }
    }

    // 6. 诊断映射
    private fun applyDiagnostics(diagnostics: List<Diagnostic>) {
        diagnosticsContainer.reset()
        val indexer = editorView.text.indexer

        for (d in diagnostics) {
            val sLine = d.range.start.line
            val sCol = d.range.start.character
            val eLine = d.range.end.line
            val eCol = d.range.end.character

            val sIdx = indexer.getCharIndex(sLine, sCol)
            val eIdx = indexer.getCharIndex(eLine, eCol)

            val severity = when (d.severity) {
                DiagnosticSeverity.Error -> DiagnosticRegion.SEVERITY_ERROR
                DiagnosticSeverity.Warning -> DiagnosticRegion.SEVERITY_WARNING
                DiagnosticSeverity.Information -> DiagnosticRegion.SEVERITY_TYPO
                else -> DiagnosticRegion.SEVERITY_NONE
            }

            diagnosticsContainer.addDiagnostic(
                DiagnosticRegion(sIdx, eIdx, severity, 0, DiagnosticDetail(d.message))
            )
        }

        editorView.post {
            editorView.diagnostics = diagnosticsContainer
            editorView.invalidateDiagnostics()
        }
    }

    fun disconnect() {
        languageServer?.shutdown()
        languageServer?.exit()
        instance.stop()
    }
}