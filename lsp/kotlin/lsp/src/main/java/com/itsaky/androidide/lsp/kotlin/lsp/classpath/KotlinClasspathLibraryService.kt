package com.itsaky.androidide.lsp.kotlin.lsp.classpath

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import org.javacs.kt.classpath.ClassPathEntry
import org.javacs.kt.classpath.defaultClassPathResolver

/** Unified classpath/library resolver + lightweight dependency symbol index. */
class KotlinClasspathLibraryService {

  data class LibraryIndex(
      val classpathEntries: Set<ClassPathEntry>,
      val classNames: Set<String>,
  )

  fun resolve(workspaceRoots: Collection<Path>): LibraryIndex {
    val resolver = defaultClassPathResolver(workspaceRoots)
    val classpath = resolver.classpathOrEmpty
    val classNames = classpath.flatMap { scanClasses(it.compiledJar) }.toSet()
    return LibraryIndex(classpath, classNames)
  }

  private fun scanClasses(entry: Path): List<String> {
    return runCatching {
          when {
            Files.isRegularFile(entry) && entry.toString().endsWith(".jar") -> scanJar(entry)
            Files.isDirectory(entry) -> scanDirectory(entry)
            else -> emptyList()
          }
        }
        .getOrDefault(emptyList())
  }

  private fun scanJar(jar: Path): List<String> =
      JarFile(jar.toFile()).use { jf ->
        jf.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".class") }
            .map { it.name.removeSuffix(".class").replace('/', '.') }
            .toList()
      }

  private fun scanDirectory(dir: Path): List<String> =
      Files.walk(dir).use { stream ->
        val results = mutableListOf<String>()
        stream
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
            .forEach {
              results +=
                  dir.relativize(it).toString().removeSuffix(".class").replace('/', '.').replace('\\', '.')
            }
        results
      }
}
