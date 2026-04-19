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

import com.itsaky.androidide.lsp.api.ConfigurableServiceProvider
import com.itsaky.androidide.lsp.api.ICompletionProvider
import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.kotlin.KotlinLanguageServerImpl
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import com.itsaky.androidide.lsp.models.MatchLevel
import com.itsaky.androidide.lsp.util.DefaultServerSettings
import com.itsaky.androidide.utils.Logger

/** @author android_zero */
class KotlinCompletionProvider : ICompletionProvider, ConfigurableServiceProvider {

  companion object {
    private val log = Logger.instance("KotlinCompletionProvider")
  }

  private var settings: IServerSettings = DefaultServerSettings()

  override fun applySettings(settings: IServerSettings) {
    this.settings = settings
  }

  override fun canComplete(file: java.nio.file.Path?): Boolean {
    if (file == null) return false
    val pathStr = file.toString()
    return super<ICompletionProvider>.canComplete(file) &&
        (pathStr.endsWith(".kt", true) || pathStr.endsWith(".kts", true))
  }

  override fun complete(params: CompletionParams): CompletionResult {
    abortCompletionIfCancelled()

    if (!settings.completionsEnabled()) {
      return CompletionResult.EMPTY
    }

    try {
      val server =
          ILanguageServerRegistry.getDefault().getServer("kotlin-lsp") as? KotlinLanguageServerImpl
              ?: return CompletionResult.EMPTY

      val result = server.complete(params)
      val prefix = params.prefix ?: ""

      if (prefix.isNotEmpty()) {
        return CompletionResult.mapAndFilter(result, prefix) { item ->
          val strictMode = prefix.length < 1 || item.ideLabel.contains(" ")

          item.matchLevel =
              if (strictMode) {
                if (item.insertText.startsWith(prefix, ignoreCase = true)) {
                  MatchLevel.CASE_INSENSITIVE_PREFIX
                } else {
                  MatchLevel.NO_MATCH
                }
              } else {
                matchLevel(item.insertText, prefix)
              }
        }
      }

      return result
    } catch (e: Exception) {
      log.error("Exception occurred during Kotlin completion resolution", e)
      return CompletionResult.EMPTY
    }
  }
}
