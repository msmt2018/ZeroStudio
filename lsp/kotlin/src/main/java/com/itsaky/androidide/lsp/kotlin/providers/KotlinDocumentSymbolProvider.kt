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
import com.itsaky.androidide.lsp.models.DocumentSymbol
import com.itsaky.androidide.lsp.models.LspRange
import com.itsaky.androidide.utils.Logger
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** @author android_zero */
class KotlinDocumentSymbolProvider {

  companion object {
    private val log = Logger.instance("KotlinDocumentSymbolProvider")
  }

  fun canProvideDocumentSymbols(file: Path?): Boolean {
    if (file == null) return false
    val pathStr = file.toString()
    return pathStr.endsWith(".kt", true) || pathStr.endsWith(".kts", true)
  }

  fun computeDocumentSymbols(file: Path): List<DocumentSymbol> {
    val server =
        ILanguageServerRegistry.getDefault().getServer("kotlin-lsp") as? KotlinLanguageServerImpl
    if (server == null) {
      log.warn("Kotlin LSP Server not found. Cannot provide document symbols.")
      return emptyList()
    }

    return runBlocking {
      try {
        withContext(Dispatchers.IO) {
          val result = server.documentSymbols(file)
          if (result.symbols.isNotEmpty()) {
            result.symbols
          } else {
            result.flatSymbols.map {
              DocumentSymbol(
                  name = it.name,
                  kindValue = it.kindValue,
                  lspRange = LspRange.fromIdeRange(it.location.range),
                  lspSelectionRange = LspRange.fromIdeRange(it.location.range),
              )
            }
          }
        }
      } catch (e: Exception) {
        log.error("Failed to fetch document symbols", e)
        emptyList()
      }
    }
  }
}
