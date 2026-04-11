package com.itsaky.androidide.lsp.kotlin.lsp.context

import com.itsaky.androidide.projects.IWorkspace
import java.io.File
import java.nio.file.Path

/** Collects module/dependency/source context for Kotlin LSP features. */
class KotlinWorkspaceContextService {

  data class ModuleDescriptor(
      val name: String,
      val modulePath: Path,
      val sourceRoots: List<Path>,
      val dependencyNotations: List<String>,
  )

  data class WorkspaceContext(
      val root: Path,
      val modules: List<ModuleDescriptor>,
  )

  private val dependencyRegex =
      Regex("""\b(?:implementation|api|compileOnly|runtimeOnly|kapt|testImplementation)\s*\(([^\)]+)\)""")

  fun build(workspace: IWorkspace): WorkspaceContext {
    val modules = workspace.getSubProjects().map { project ->
      val moduleDir = project.projectDir.toPath()
      val gradleFiles = listOf(
          moduleDir.resolve("build.gradle").toFile(),
          moduleDir.resolve("build.gradle.kts").toFile(),
      )
      val dependencies = gradleFiles.flatMap(::parseDependencies)
      val sourceRoots =
          listOf(
              moduleDir.resolve("src/main/kotlin"),
              moduleDir.resolve("src/main/java"),
              moduleDir.resolve("src/commonMain/kotlin"),
          ).filter { it.toFile().exists() }

      ModuleDescriptor(project.name, moduleDir, sourceRoots, dependencies)
    }

    return WorkspaceContext(workspace.getProjectDir().toPath(), modules)
  }

  private fun parseDependencies(file: File): List<String> {
    if (!file.exists() || !file.isFile) return emptyList()
    return dependencyRegex.findAll(file.readText()).map { it.groupValues[1].trim() }.toList()
  }
}
