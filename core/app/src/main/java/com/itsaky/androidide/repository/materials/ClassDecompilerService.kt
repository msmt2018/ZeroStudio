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
package com.itsaky.androidide.repository.materials

import com.itsaky.androidide.preferences.internal.GeneralPreferences
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Decompilation helpers used by the Project Materials tree. The IDE does not ship
 * with a real decompiler engine wired into Gradle, so we use the built-in
 * [ClassFileAnalyzer] which produces a structural rendering of the class file
 * (Java) and a smali-like disassembly of the bytecode.
 *
 * The functions are designed to be called from a background coroutine – they only
 * touch the file system and the [java.io.tmpdir] cache directory.
 *
 * @author android_zero
 */
internal object ClassDecompilerService {

  private val cacheDir: File by lazy {
    File(System.getProperty("java.io.tmpdir"), "materials-decompiled-cache").apply { mkdirs() }
  }

  /**
   * Produce a Java source rendering of the class entry inside [target]. The result
   * is cached on disk and re-used if the same entry is requested again. The function
   * returns the file that contains the decompiled text.
   *
   * @param target Description of the class entry to decompile.
   * @param onProgress Optional progress callback (e.g. for the dialog). The callback
   *   is invoked from whatever thread the decompiler is running on; callers that
   *   update UI state from the callback must marshal to the main thread themselves.
   */
  fun decompileToJava(target: ClassEntryTarget, onProgress: ((String) -> Unit)? = null): File {
    onProgress?.invoke("Reading class file…")
    val cached = cachedFile(target, "java")
    if (cached.exists() && cached.length() > 0) return cached
    val model = ClassFileAnalyzer.readZipEntry(target.archive, target.entryName)
        ?: error("Could not parse ${target.entryName} as a Java class file")
    onProgress?.invoke("Rendering Java source…")
    val engine = GeneralPreferences.decompilerEngine
    val text = ClassFileAnalyzer.renderJava(model, engine)
    FileOutputStream(cached).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    return cached
  }

  /**
   * Produce a Smali rendering of the class entry inside [target]. The result is
   * cached on disk and re-used if the same entry is requested again. The function
   * returns the file that contains the decompiled text.
   *
   * @param target Description of the class entry to decompile.
   * @param onProgress Optional progress callback (e.g. for the dialog). The callback
   *   is invoked from whatever thread the decompiler is running on; callers that
   *   update UI state from the callback must marshal to the main thread themselves.
   */
  fun decompileToSmali(target: ClassEntryTarget, onProgress: ((String) -> Unit)? = null): File {
    onProgress?.invoke("Reading class file…")
    val cached = cachedFile(target, "smali")
    if (cached.exists() && cached.length() > 0) return cached
    val model = ClassFileAnalyzer.readZipEntry(target.archive, target.entryName)
        ?: error("Could not parse ${target.entryName} as a Java class file")
    onProgress?.invoke("Disassembling bytecode…")
    val text = ClassFileAnalyzer.renderSmali(model)
    FileOutputStream(cached).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    return cached
  }

  /**
   * Extracts [target.entryName] from [target.archive] to a temporary file. Used for
   * non-class entries inside archives that the user wants to open as-is.
   */
  fun extractToTemp(target: ClassEntryTarget): File {
    ZipFile(target.archive).use { zip ->
      val entry = zip.getEntry(target.entryName)
          ?: error("Entry ${target.entryName} not found inside ${target.archive.name}")
      val out = File.createTempFile("material_", "_" + target.entryName.substringAfterLast('/'))
      zip.getInputStream(entry).use { input ->
        out.outputStream().use { input.copyTo(it) }
      }
      return out
    }
  }

  private fun cachedFile(target: ClassEntryTarget, extension: String): File {
    val key = (target.archive.absolutePath + "!" + target.entryName).hashCode().toString(16)
    val name = "${target.archive.nameWithoutExtension}_${target.entryName.replace('/', '_')}.$extension"
    val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(200)
    return File(cacheDir, "${key}_$safeName")
  }
}
