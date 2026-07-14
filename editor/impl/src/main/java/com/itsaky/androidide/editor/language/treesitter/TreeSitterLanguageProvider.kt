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

package com.itsaky.androidide.editor.language.treesitter

import android.content.Context
import java.io.File
import org.slf4j.LoggerFactory

/**
 * Provides instance of [TreeSitterLanguage] implementations.
 *
 * @author Akash Yadav
 */
object TreeSitterLanguageProvider {

  private val log = LoggerFactory.getLogger(TreeSitterLanguageProvider::class.java)

  fun hasTsLanguage(file: File): Boolean {
    return TSLanguageRegistry.instance.hasLanguage(file.extension)
  }

  fun forFile(file: File, context: Context): TreeSitterLanguage? {
    if (!hasTsLanguage(file)) {
      return null
    }

    return forType(file.extension, context)
  }

  fun forType(type: String, context: Context): TreeSitterLanguage? {
    return try {
      TSLanguageRegistry.instance.getFactory<TreeSitterLanguage>(type).create(context)
    } catch (e: TSLanguageRegistry.NotRegisteredException) {
      null
    } catch (e: Throwable) {
      // 必须 catch Throwable 而非 Exception —— System.loadLibrary("tree-sitter-xxx")
      // 失败时抛出 UnsatisfiedLinkError (extends Error), TSLanguageHtml 类初始化
      // 失败时抛出 ExceptionInInitializerError (extends Error). 这些 Error 不会被
      // catch (e: Exception) 捕获, 会传播到 IDEEditor 的 editorScope.launch 中
      // 导致 callback 永远不被调用, editor 永远不切换 language, 文件全黑字.
      log.error(
          "Failed to create TreeSitterLanguage for type='{}', falling back to null",
          type,
          e,
      )
      null
    }
  }
}
