package com.itsaky.androidide.editor.lsp

import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.models.CompletionItem
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.progress.ICancelChecker
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.text.CharPosition
import org.slf4j.LoggerFactory

class LspCompletionProvider(private val editor: IDEEditor, private val server: ILanguageServer) {
  private val log = LoggerFactory.getLogger(LspCompletionProvider::class.java)

  fun fetchCompletions(position: CharPosition, publisher: CompletionPublisher, prefix: String) {
    val file = editor.file?.toPath() ?: return
    val params =
        CompletionParams(
            position = com.itsaky.androidide.models.Position(position.line, position.column),
            file = file,
            cancelChecker = ICancelChecker.NOOP,
        ).apply {
          content = editor.text.toString()
          this.prefix = prefix
        }

    try {
      val result = server.complete(params)
      publisher.addItems(result.items.map { createSoraItem(it, prefix.length) })
    } catch (e: Exception) {
      log.error("LSP Completion Failed", e)
    }
  }

  private fun createSoraItem(
      item: CompletionItem,
      prefixLen: Int,
  ): io.github.rosemoe.sora.lang.completion.CompletionItem {
    return SimpleCompletionItem(item.ideLabel, item.detail, prefixLen, item.insertText)
  }
}

