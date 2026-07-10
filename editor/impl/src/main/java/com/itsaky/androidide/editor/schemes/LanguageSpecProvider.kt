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

package com.itsaky.androidide.editor.schemes

import android.content.Context
import com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguageSpec
import com.itsaky.androidide.editor.language.treesitter.predicates.AnyOfPredicate
import com.itsaky.androidide.editor.language.treesitter.predicates.EqualPredicate
import com.itsaky.androidide.editor.language.treesitter.predicates.MatchPredicate
import com.itsaky.androidide.editor.language.treesitter.predicates.NotEqualPredicate
import com.itsaky.androidide.editor.language.treesitter.predicates.NotMatchPredicate
import com.itsaky.androidide.treesitter.TSLanguage
import io.github.rosemoe.sora.editor.ts.LocalsCaptureSpec
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec
import java.io.FileNotFoundException
import org.slf4j.LoggerFactory

/**
 * Provides language spec instances for tree sitter languages.
 *
 * @author Akash Yadav
 */
object LanguageSpecProvider {

  private const val BASE_SPEC_PATH = "editor/treesitter"

  // 共享同一 grammar 的语言：当某 type 的 scm 缺失时，回退到此 type 的 scm。
  // 例如 kts 与 kt 共用 Kotlin grammar，避免 TSQuery.create 因空 query 抛
  // IllegalArgumentException 导致 language 初始化失败（"全黑字" regression）。
  private val SHARED_SCM_FALLBACKS = mapOf(
    "kts" to "kt",
  )

  private val log = LoggerFactory.getLogger(LanguageSpecProvider::class.java)

  @JvmStatic
  @JvmOverloads
  fun getLanguageSpec(
      context: Context,
      type: String,
      lang: TSLanguage,
      localsCaptureSpec: LocalsCaptureSpec = LocalsCaptureSpec.DEFAULT,
  ): TreeSitterLanguageSpec {
    val editorLangSpec =
        TsLanguageSpec(
            language = lang,
            highlightScmSource = readScheme(context, type, "highlights"),
            localsScmSource = readScheme(context, type, "locals"),
            codeBlocksScmSource = readScheme(context, type, "blocks"),
            bracketsScmSource = readScheme(context, type, "brackets"),
            localsCaptureSpec = localsCaptureSpec,
            predicates =
                listOf(
                    MatchPredicate,
                    NotMatchPredicate,
                    EqualPredicate,
                    NotEqualPredicate,
                    AnyOfPredicate,
                ),
        )
    return TreeSitterLanguageSpec(
        spec = editorLangSpec,
        indentsQueryScm = readScheme(context, type, "indents"),
    )
  }

  private fun readScheme(context: Context, type: String, name: String): String {
    return try {
      // 使用 .use {} 确保 InputStream 在异常时也能被关闭
      context.assets.open("${BASE_SPEC_PATH}/${type}/${name}.scm").use { it.reader().readText() }
    } catch (e: Exception) {
      if (e !is FileNotFoundException) {
        // log everything except FileNotFoundException
        log.error("Failed to read scheme file {} for type {}", name, type, e)
      }
      // 共享 grammar 回退：kts 等共用同 grammar 的语言可能没有自己的 scm 资源。
      // 若 type 存在 fallback，尝试从 fallback type 读同名 scm。
      val fallbackType = SHARED_SCM_FALLBACKS[type]
      if (fallbackType != null) {
        try {
          return context.assets.open("${BASE_SPEC_PATH}/${fallbackType}/${name}.scm").use {
            it.reader().readText()
          }
        } catch (e2: Exception) {
          if (e2 !is FileNotFoundException) {
            log.error("Failed to read fallback scheme file {}/{} for type {}",
              fallbackType, name, type, e2)
          }
        }
      }
      ""
    }
  }
}
