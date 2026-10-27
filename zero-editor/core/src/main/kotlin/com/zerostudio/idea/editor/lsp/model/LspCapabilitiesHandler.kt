package com.zerostudio.idea.editor.lsp.model

import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

class LspCapabilitiesHandler(private val connector: ZeroLspConnector) {

    /**
     * 1. 触发参数提示 (Signature Help: e.g. foo(int a, |String b))
     */
    suspend fun requestSignatureHelp(line: Int, column: Int): SignatureHelp? = withContext(Dispatchers.IO) {
        val server = connector.instance.activeConnection ?: return@withContext null
        val lsp = connector.getRemoteServer() ?: return@withContext null
        val params = SignatureHelpParams(
            TextDocumentIdentifier(connector.fileUri),
            Position(line, column)
        )
        return@withContext runCatching {
            lsp.textDocumentService.signatureHelp(params).get()
        }.getOrNull()
    }

    /**
     * 2. 获取 Inlay Hints 内联提示并直接映射入 Sora InlayHintsContainer
     */
    suspend fun fetchInlayHints(startLine: Int, endLine: Int): InlayHintsContainer = withContext(Dispatchers.IO) {
        val container = InlayHintsContainer()
        val lsp = connector.getRemoteServer() ?: return@withContext container
        val range = Range(Position(startLine, 0), Position(endLine, 0))
        val params = InlayHintParams(TextDocumentIdentifier(connector.fileUri), range)

        val hints = runCatching {
            lsp.textDocumentService.inlayHint(params).get()
        }.getOrNull() ?: emptyList()

        for (hint in hints) {
            val label = when {
                hint.label.isLeft -> hint.label.left
                hint.label.isRight -> hint.label.right.joinToString("") { it.value }
                else -> ""
            }
            container.add(TextInlayHint(hint.position.line, hint.position.character, ": $label"))
        }
        return@withContext container
    }

    /**
     * 3. 请求代码快速修复与重构意图 (Code Action / Quick Fix)
     */
    suspend fun getCodeActions(range: Range, diagnostics: List<Diagnostic>): List<CodeAction> = withContext(Dispatchers.IO) {
        val lsp = connector.getRemoteServer() ?: return@withContext emptyList()
        val context = CodeActionContext(diagnostics).apply {
            only = listOf(CodeActionKind.QuickFix, CodeActionKind.Refactor)
        }
        val params = CodeActionParams(TextDocumentIdentifier(connector.fileUri), range, context)
        val actions = runCatching {
            lsp.textDocumentService.codeAction(params).get()
        }.getOrNull() ?: emptyList()

        return@withContext actions.mapNotNull { either ->
            if (either.isRight) either.right else null
        }
    }

    /**
     * 4. 执行代码修复动作，自动将改动写回 Sora Editor
     */
    suspend fun applyCodeAction(action: CodeAction) = withContext(Dispatchers.Main) {
        val edit = action.edit ?: return@withContext
        val changes = edit.changes ?: emptyMap()
        val edits = changes[connector.fileUri] ?: return@withContext

        // 统一逆序应用更改
        edits.sortedByDescending { it.range.start.line * 100000 + it.range.start.character }.forEach { textEdit ->
            connector.editorView.text.replace(
                textEdit.range.start.line,
                textEdit.range.start.character,
                textEdit.range.end.line,
                textEdit.range.end.character,
                textEdit.newText
            )
        }
    }
}

// 拓展支持获取底层代理实例
internal fun ZeroLspConnector.getRemoteServer(): org.eclipse.lsp4j.services.LanguageServer? {
    val field = ZeroLspConnector::class.java.getDeclaredField("languageServer")
    field.isAccessible = true
    return field.get(this) as? org.eclipse.lsp4j.services.LanguageServer
}