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

package com.itsaky.androidide.lsp.java.compiler

import com.itsaky.androidide.javac.services.compiler.ReusableContext
import com.itsaky.androidide.javac.services.compiler.ReusableJavaCompiler
import com.itsaky.androidide.projects.FileManager
import com.itsaky.androidide.utils.VMUtils
import com.itsaky.androidide.utils.withStopWatch
import jdkx.tools.JavaFileObject
import jdkx.tools.JavaFileObject.Kind.SOURCE
import kotlin.io.path.name
import openjdk.tools.javac.api.ClientCodeWrapper
import openjdk.tools.javac.tree.JCTree.JCCompilationUnit
import openjdk.tools.javac.util.Context

class JavaCompilerImpl(context: Context?) : ReusableJavaCompiler(context) {

  override fun parse(filename: JavaFileObject?, content: CharSequence?): JCCompilationUnit {
    return super.parse(filename, content)
  }

  companion object {
    @JvmStatic
    fun preRegister(context: ReusableContext, replace: Boolean = false) {
      if (replace) {
        context.drop(compilerKey)
      }
      context.put(compilerKey, Context.Factory { JavaCompilerImpl(it) })
    }
  }
}
