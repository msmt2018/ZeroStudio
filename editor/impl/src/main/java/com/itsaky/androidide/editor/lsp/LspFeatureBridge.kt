package com.itsaky.androidide.editor.lsp

import com.itsaky.androidide.lsp.models.DocumentSymbol
import com.itsaky.androidide.lsp.models.DocumentSymbolsResult
import com.itsaky.androidide.lsp.models.LspRange
import com.itsaky.androidide.lsp.models.SemanticTokens
import com.itsaky.androidide.models.Range
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle

/** Bridges new core/lsp-models features into editor-friendly structures. */
object LspFeatureBridge {

  fun flattenSymbols(result: DocumentSymbolsResult): List<DocumentSymbol> {
    if (result.flatSymbols.isNotEmpty()) {
      return result.flatSymbols.map {
        DocumentSymbol(
            name = it.name,
            kindValue = it.kindValue,
            lspRange = LspRange.fromIdeRange(it.location.range),
            lspSelectionRange = LspRange.fromIdeRange(it.location.range),
        )
      }
    }

    val out = mutableListOf<DocumentSymbol>()
    fun walk(node: DocumentSymbol) {
      out += node
      node.children.forEach(::walk)
    }
    result.symbols.forEach(::walk)
    return out
  }

  /** Simple semantic token -> span conversion (placeholder mapping). */
  fun semanticTokensToSpans(tokens: SemanticTokens): List<Span> {
    if (tokens.data.isEmpty()) return emptyList()
    val spans = mutableListOf<Span>()
    var offset = 0
    for (i in tokens.data.indices step 5) {
      val length = tokens.data.getOrNull(i + 2) ?: 0
      spans += SpanFactory.obtain(offset, TextStyle.makeStyle(0xFF66BB6A.toInt()))
      offset += length
    }
    return spans
  }

  fun toOutlineRanges(result: DocumentSymbolsResult): List<Range> =
      flattenSymbols(result).map { it.range }
}
