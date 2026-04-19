// FILE: editor/impl/src/main/java/com/itsaky/androidide/editor/lsp/LspSemanticTokenManager.kt
/*
 *  This file is part of AndroidIDE.
 *  @author android_zero
 */

package com.itsaky.androidide.editor.lsp

import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.lsp.rpc.UriConverter
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle
import org.slf4j.LoggerFactory
import com.itsaky.androidide.lsp.util.SemanticTokenDecoder

class LspSemanticTokenManager(
    private val editor: IDEEditor,
    private val server: ILanguageServer
) {
    private val log = LoggerFactory.getLogger(LspSemanticTokenManager::class.java)
    private var currentLegend: SemanticTokensLegend? = null

    fun setLegend(legend: SemanticTokensLegend?) {
        this.currentLegend = legend
    }

    fun refreshHighlighter() {
        val legend = currentLegend ?: return
        val fileUri = UriConverter.fileToUri(editor.file!!)
        
        val params = SemanticTokensParams(TextDocumentIdentifier(fileUri))

        server.semanticTokensFullAsync(params).thenAccept { result ->
            if (result == null) return@thenAccept

            val decodedTokens = SemanticTokenDecoder.decode(result)
            applyTokensToEditor(decodedTokens, legend)
        }.exceptionally {
            log.warn("Semantic tokens request failed", it)
            null
        }
    }

    private fun applyTokensToEditor(tokens: List<DecodedSemanticToken>, legend: SemanticTokensLegend) {
        editor.post {
            val styles = editor.styles ?: return@post
            val spanMap = styles.spans ?: return@post
            
            if (!spanMap.supportsModify()) return@post
            
            val modifier = spanMap.modify()
            
            tokens.groupBy { it.line }.forEach { (line, lineTokens) ->
                if (line >= editor.lineCount) return@forEach
                
                val newSpans = mutableListOf<Span>()
                lineTokens.forEach { token ->
                    val typeName = legend.tokenTypes.getOrNull(token.typeIndex) ?: "variable"
                    val colorId = LspThemeBridge.getStyleIdForType(typeName)
                    
                    newSpans.add(SpanFactory.obtain(
                        token.startChar,
                        TextStyle.makeStyle(colorId)
                    ))
                }
                
                if (newSpans.isNotEmpty()) {
                    modifier.setSpansOnLine(line, newSpans)
                }
            }
            editor.invalidate()
        }
    }
}
