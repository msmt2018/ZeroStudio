package com.zerostudio.idea.editor.lsp.completion

import android.graphics.drawable.Drawable
import com.zerostudio.idea.editor.core.snippet.ZeroSnippetController
import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.lsp.bridge.getRemoteServer
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.lang.completion.CompletionItem as SoraCompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind as SoraCompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionIconDrawer
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

class ZeroLspCompletionAdapter(
    private val connectorProvider: () -> ZeroLspConnector?,
    private val editor: ZeroCodeEditorView,
    private val snippetController: ZeroSnippetController
) {

    suspend fun requestAndPublish(
        position: CharPosition,
        publisher: CompletionPublisher
    ) = withContext(Dispatchers.IO) {
        val connector = connectorProvider() ?: return@withContext
        val server = connector.getRemoteServer() ?: return@withContext

        val params = CompletionParams(
            TextDocumentIdentifier(connector.fileUri),
            Position(position.line, position.column)
        )

        val response = runCatching {
            server.textDocumentService.completion(params).get()
        }.getOrNull() ?: return@withContext

        val lspItems: List<CompletionItem> = when {
            response.isLeft -> response.left
            response.isRight -> response.right.items
            else -> emptyList()
        }

        if (lspItems.isEmpty()) return@withContext

        val soraItems = mutableListOf<SoraCompletionItem>()
        for (item in lspItems) {
            val label = item.label
            val detail = item.detail ?: ""
            val desc = parseDocumentation(item)
            val isSnippet = item.insertTextFormat == InsertTextFormat.Snippet

            // 提取插入内容
            val insertText = item.insertText ?: item.textEdit?.let {
                if (it.isLeft) it.left.newText else null
            } ?: label

            val kind = mapLspKindToSoraKind(item.kind)
            val icon = SimpleCompletionIconDrawer.draw(kind)

            val soraItem = object : SoraCompletionItem(label, desc, icon) {
                init {
                    this.detail = detail
                    this.kind = kind
                    this.sortText = item.sortText ?: label
                    this.filterText = item.filterText ?: label
                }

                override fun performCompletion(editorView: CodeEditor, text: Content, pos: CharPosition) {
                    if (isSnippet) {
                        // 交付给 Snippet 控制器进行动态宏展开与 Tab-Stop 拓扑导航
                        snippetController.startSnippet(
                            rawTemplate = insertText,
                            insertOffset = pos.index,
                            fileName = connector.fileUri.substringAfterLast('/')
                        )
                    } else {
                        // 纯文本原子插入并重置光标
                        text.insert(pos.line, pos.column, insertText)
                        editorView.setSelection(pos.line, pos.column + insertText.length)
                    }
                }

                override fun performCompletion(editor: CodeEditor, text: Content, line: Int, column: Int) {
                    val pos = text.indexer.getCharPosition(line, column)
                    performCompletion(editor, text, pos)
                }
            }
            soraItems.add(soraItem)
        }

        withContext(Dispatchers.Main) {
            publisher.setComparator { a, b ->
                val sA = a.sortText ?: a.label.toString()
                val sB = b.sortText ?: b.label.toString()
                sA.compareTo(sB, ignoreCase = true)
            }
            publisher.addItems(soraItems)
            publisher.updateList(true)
        }
    }

    private fun parseDocumentation(item: CompletionItem): String {
        val doc = item.documentation ?: return ""
        return when {
            doc.isRight -> doc.right.value
            doc.isLeft -> doc.left
            else -> ""
        }
    }

    private fun mapLspKindToSoraKind(kind: CompletionItemKind?): SoraCompletionItemKind {
        return when (kind) {
            CompletionItemKind.Method -> SoraCompletionItemKind.Method
            CompletionItemKind.Function -> SoraCompletionItemKind.Function
            CompletionItemKind.Constructor -> SoraCompletionItemKind.Constructor
            CompletionItemKind.Field -> SoraCompletionItemKind.Field
            CompletionItemKind.Variable -> SoraCompletionItemKind.Variable
            CompletionItemKind.Class -> SoraCompletionItemKind.Class
            CompletionItemKind.Interface -> SoraCompletionItemKind.Interface
            CompletionItemKind.Module -> SoraCompletionItemKind.Module
            CompletionItemKind.Property -> SoraCompletionItemKind.Property
            CompletionItemKind.Unit -> SoraCompletionItemKind.Unit
            CompletionItemKind.Value -> SoraCompletionItemKind.Value
            CompletionItemKind.Enum -> SoraCompletionItemKind.Enum
            CompletionItemKind.Keyword -> SoraCompletionItemKind.Keyword
            CompletionItemKind.Snippet -> SoraCompletionItemKind.Snippet
            CompletionItemKind.Color -> SoraCompletionItemKind.Color
            CompletionItemKind.File -> SoraCompletionItemKind.File
            CompletionItemKind.Reference -> SoraCompletionItemKind.Reference
            CompletionItemKind.Folder -> SoraCompletionItemKind.Folder
            CompletionItemKind.EnumMember -> SoraCompletionItemKind.EnumMember
            CompletionItemKind.Constant -> SoraCompletionItemKind.Constant
            CompletionItemKind.Struct -> SoraCompletionItemKind.Struct
            CompletionItemKind.Event -> SoraCompletionItemKind.Event
            CompletionItemKind.Operator -> SoraCompletionItemKind.Operator
            CompletionItemKind.TypeParameter -> SoraCompletionItemKind.TypeParameter
            else -> SoraCompletionItemKind.Identifier
        }
    }
}