package com.itsaky.androidide.projects.internal

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.DependencyModulesParams
import ch.epfl.scala.bsp4j.DependencySourcesParams
import ch.epfl.scala.bsp4j.SourcesParams
import ch.epfl.scala.bsp4j.WorkspaceBuildTargetsParams
import com.itsaky.androidide.builder.model.DefaultProjectSyncIssues
import com.itsaky.androidide.projects.GradleProject
import com.itsaky.androidide.tooling.api.bsp.BspBuildService
import java.io.File
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/** Builds workspace model from BSP workspace/buildTargets response. */
internal object BspWorkspaceModelBuilder {

  fun build(projectDir: File, bspService: BspBuildService): WorkspaceImpl {
    val targets = bspService.workspaceBuildTargets(WorkspaceBuildTargetsParams()).get().targets

    val modules = targets.map { target ->
      val id = target.id
      val sourceRoots = loadSourceRoots(bspService, id)
      val depUris = loadDependencyUris(bspService, id)
      val variants = inferVariants(target.tags)

      BspBackedModuleProject(
        name = target.displayName ?: id.uri.substringAfterLast('/'),
        description = buildString {
          append(target.dataKind ?: "bsp")
          if (target.tags.isNotEmpty()) append(" tags=").append(target.tags.joinToString(","))
        },
        path = normalizePath(id.uri),
        projectDir = target.baseDirectory?.let { fromUri(it) } ?: projectDir,
        buildDir = File(projectDir, "build"),
        buildScript = File(projectDir, "build.gradle"),
        tasks = CopyOnWriteArrayList(),
        languages = target.languageIds ?: emptyList(),
        sourceRoots = sourceRoots,
        dependencyUris = depUris,
        variants = variants,
      )
    }

    val root: GradleProject = modules.firstOrNull() ?: BspBackedModuleProject(
      name = projectDir.name,
      description = "BSP root project",
      path = ":",
      projectDir = projectDir,
      buildDir = File(projectDir, "build"),
      buildScript = File(projectDir, "build.gradle"),
      tasks = CopyOnWriteArrayList(),
      languages = emptyList(),
      sourceRoots = emptySet(),
      dependencyUris = emptySet(),
      variants = emptyList(),
    )

    return WorkspaceImpl(projectDir, root, modules, DefaultProjectSyncIssues(emptyList()))
  }

  private fun loadSourceRoots(service: BspBuildService, id: BuildTargetIdentifier): Set<File> {
    val params = SourcesParams(listOf(id))
    return service.buildTargetSources(params).get().items
      .flatMap { it.sources ?: emptyList() }
      .mapNotNull { runCatching { fromUri(it.uri) }.getOrNull() }
      .toSet()
  }

  private fun loadDependencyUris(service: BspBuildService, id: BuildTargetIdentifier): Set<String> {
    val sourceDeps = service.buildTargetDependencySources(DependencySourcesParams(listOf(id))).get()
      .items.flatMap { it.sources ?: emptyList() }
      .mapNotNull { it.uri }

    val moduleDeps = service.buildTargetDependencyModules(DependencyModulesParams(listOf(id))).get()
      .items.flatMap { it.modules ?: emptyList() }
      .mapNotNull { it.name }

    return (sourceDeps + moduleDeps).toSet()
  }

  private fun inferVariants(tags: List<String>?): List<String> {
    val normalized = (tags ?: emptyList()).map { it.lowercase() }
    val variants = mutableListOf<String>()
    if (normalized.any { it.contains("debug") }) variants += "debug"
    if (normalized.any { it.contains("release") }) variants += "release"
    if (variants.isEmpty()) variants += "default"
    return variants
  }

  private fun normalizePath(uri: String): String {
    val tail = uri.substringAfterLast('/').ifBlank { "root" }
    return if (tail.startsWith(":")) tail else ":$tail"
  }

  private fun fromUri(uri: String): File = File(URI(uri))
}
