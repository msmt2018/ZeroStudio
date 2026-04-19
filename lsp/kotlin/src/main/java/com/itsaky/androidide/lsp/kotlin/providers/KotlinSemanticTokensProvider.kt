/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.lsp.kotlin.providers

import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.kotlin.KotlinLanguageServerImpl
import com.itsaky.androidide.lsp.models.HighlightToken
import com.itsaky.androidide.lsp.models.HighlightTokenKind
import com.itsaky.androidide.lsp.models.SemanticTokensParams
import com.itsaky.androidide.lsp.models.TextDocumentIdentifier
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.utils.Logger
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** @author android_zero */
class KotlinSemanticTokensProvider {

  companion object {
    private val log = Logger.instance("KotlinSemanticTokensProvider")

    private val KOTLIN_TOKEN_TYPES =
        arrayOf(
            "keyword",
            "variable",
            "function",
            "property",
            "parameter",
            "enumMember",
            "class",
            "interface",
            "enum",
            "type",
            "string",
            "number",
        )
  }

  fun canProvideSemanticTokens(file: Path?): Boolean {
    if (file == null) return false
    val pathStr = file.toString()
    return pathStr.endsWith(".kt", true) || pathStr.endsWith(".kts", true)
  }

  fun computeSemanticTokens(file: Path): List<HighlightToken> {
    val server =
        ILanguageServerRegistry.getDefault().getServer("kotlin-lsp") as? KotlinLanguageServerImpl
    if (server == null) {
      log.warn("Kotlin LSP Server not found. Skipping semantic tokens.")
      return emptyList()
    }

    return runBlocking {
      try {
        val params =
            SemanticTokensParams(
                TextDocumentIdentifier(com.itsaky.androidide.lsp.rpc.UriConverter.pathToUri(file))
            )

        withContext(Dispatchers.IO) {
          val semanticTokens = server.semanticTokensFull(params)
          decodeTokens(semanticTokens.data)
        }
      } catch (e: Exception) {
        log.error("Failed to compute semantic tokens", e)
        emptyList()
      }
    }
  }

  private fun decodeTokens(data: List<Int>): List<HighlightToken> {
    val tokens = mutableListOf<HighlightToken>()
    var currentLine = 0
    var currentChar = 0

    for (i in 0 until data.size step 5) {
      val deltaLine = data[i]
      val deltaStartChar = data[i + 1]
      val length = data[i + 2]
      val tokenType = data[i + 3]
      val tokenModifiers = data[i + 4]

      if (deltaLine > 0) {
        currentLine += deltaLine
        currentChar = deltaStartChar
      } else {
        currentChar += deltaStartChar
      }

      val kind = mapToHighlightTokenKind(tokenType, tokenModifiers)
      val range =
          Range(Position(currentLine, currentChar), Position(currentLine, currentChar + length))

      tokens.add(HighlightToken(range, kind))
    }

    return tokens
  }

  private fun mapToHighlightTokenKind(
      tokenTypeIndex: Int,
      tokenModifiers: Int,
  ): HighlightTokenKind {
    val typeStr =
        KOTLIN_TOKEN_TYPES.getOrNull(tokenTypeIndex) ?: return HighlightTokenKind.TEXT_NORMAL

    return when (typeStr) {
      "keyword" -> HighlightTokenKind.KEYWORD
      "variable" -> HighlightTokenKind.LOCAL_VARIABLE
      "function" -> HighlightTokenKind.METHOD_INVOCATION
      "property" -> HighlightTokenKind.FIELD
      "parameter" -> HighlightTokenKind.PARAMETER
      "enumMember" -> HighlightTokenKind.ENUM
      "class",
      "type" -> HighlightTokenKind.TYPE_NAME
      "interface" -> HighlightTokenKind.INTERFACE
      "enum" -> HighlightTokenKind.ENUM_TYPE
      "string" -> HighlightTokenKind.LITERAL
      "number" -> HighlightTokenKind.LITERAL
      else -> HighlightTokenKind.TEXT_NORMAL
    }
  }
}
