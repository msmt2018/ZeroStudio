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
    } catch (e: Exception) {
      // TreeSitterLanguage 构造过程中任何异常（scm 资源缺失、query 编译失败、
      // ABI 不兼容等）都应被吞掉并回退到 null，让 editor 后续走 fallback 路径
      // （如 GroovyLanguage / CppLanguage / EmptyLanguage）显示文件，而不是让异常
      // 传播到 IDEEditor 的 editorScope.launch 中导致 callback 永远不被调用、
      // editor 永远不切换 language、文件全黑字。
      log.error(
          "Failed to create TreeSitterLanguage for type='{}', falling back to null",
          type,
          e,
      )
      null
    }
  }
}
