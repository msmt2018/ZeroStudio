package com.zerostudio.idea.editor.lsp.adapter

import android.graphics.drawable.Drawable
import com.zerostudio.idea.editor.lsp.LspCoreClient
import com.zerostudio.idea.editor.lsp.LspEngineStatus
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.lang.completion.CompletionItem as SoraCompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.text.CharPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * LSP 自动补全转换器
 */
class ZeroLspCompletionAdapter(
    private val lspClient: LspCoreClient,
    private val fileUri: String,
    private val editor: ZeroCodeEditorView
) {
    suspend fun requestAndPublishCompletions(
        position: CharPosition,
        publisher: CompletionPublisher
    ) = withContext(Dispatchers.IO) {
        if (lspClient.status.value != LspEngineStatus.RUNNING) return@withContext

        val server = lspClient.getLanguageServer() ?: return@withContext
        val params = CompletionParams(
            TextDocumentIdentifier(fileUri),
            Position(position.line, position.column)
        )

        val response = runCatching {
            server.textDocumentService.completion(params).get()
        }.getOrNull() ?: return@withContext

        val items: List<CompletionItem> = when {
            response.isLeft -> response.left
            response.isRight -> response.right.items
            else -> emptyList()
        }

        val soraItems = mutableListOf<SoraCompletionItem>()
        for (item in items) {
            val label = item.label
            val detail = item.detail ?: ""
            val insertText = item.insertText ?: item.textEdit?.let {
                if (it.isLeft) it.left.newText else null
            } ?: label

            val isSnippet = item.insertTextFormat == InsertTextFormat.Snippet

            // 构建符合 Sora 规范并支持 Snippet 注入的补全项
            val soraItem = object : SimpleCompletionItem(label, detail, null, insertText) {
                override fun performCompletion(editorView: io.github.rosemoe.sora.widget.CodeEditor, position: CharPosition) {
                    if (isSnippet) {
                        // 委托给自研的 Snippet 控制器处理动态占位符
                        editor.snippetController.startSnippet(insertText, position.index)
                    } else {
                        super.performCompletion(editorView, position)
                    }
                }
            }
            soraItems.add(soraItem)
        }

        withContext(Dispatchers.Main) {
            publisher.setComparator(Comparator { a, b ->
                a.label.toString().compareTo(b.label.toString(), ignoreCase = true)
            })
            publisher.addItems(soraItems)
            publisher.finish()
        }
    }
}

/**
 * LSP 光标悬停文档提取器
 */
class ZeroLspHoverAdapter(
    private val lspClient: LspCoreClient,
    private val fileUri: String
) {
    suspend fun getHoverDocumentation(line: Int, column: Int): String? = withContext(Dispatchers.IO) {
        if (lspClient.status.value != LspEngineStatus.RUNNING) return@withContext null
        val server = lspClient.getLanguageServer() ?: return@withContext null

        val params = HoverParams(TextDocumentIdentifier(fileUri), Position(line, column))
        val hover = runCatching {
            server.textDocumentService.hover(params).get()
        }.getOrNull() ?: return@withContext null

        val contents: Either<List<Either<String, MarkedString>>, MarkupContent> = hover.contents ?: return@withContext null

        return@withContext if (contents.isRight) {
            contents.right.value
        } else if (contents.isLeft) {
            contents.left.joinToString("\n") { either ->
                if (either.isLeft) either.left else either.right.value
            }
        } else null
    }
}