package com.itsaky.androidide.projects.internal

import ch.epfl.scala.bsp4j.WorkspaceBuildTargetsParams
import com.itsaky.androidide.builder.model.DefaultProjectSyncIssues
import com.itsaky.androidide.projects.GradleProject
import com.itsaky.androidide.tooling.api.bsp.BspBuildService
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Builds workspace model from BSP workspace/buildTargets response. */
internal object BspWorkspaceModelBuilder {

  fun build(projectDir: File, bspService: BspBuildService): WorkspaceImpl {
    val targets = bspService
      .workspaceBuildTargets(WorkspaceBuildTargetsParams())
      .get()
      .targets

    val modules =
      targets.map { target ->
        val targetName = target.displayName ?: target.id.uri.substringAfterLast('/')
        val targetPath = target.id.uri.substringAfterLast('/').ifEmpty { ":" }
        GradleProject(
          name = targetName,
          description = target.dataKind ?: "",
          path = if (targetPath.startsWith(":")) targetPath else ":$targetPath",
          projectDir = target.baseDirectory?.let { File(it.removePrefix("file://")) } ?: projectDir,
          buildDir = File(projectDir, "build"),
          buildScript = File(projectDir, "build.gradle"),
          tasks = CopyOnWriteArrayList(),
        )
      }

    val root = modules.firstOrNull() ?: GradleProject(
      name = projectDir.name,
      description = "BSP root project",
      path = ":",
      projectDir = projectDir,
      buildDir = File(projectDir, "build"),
      buildScript = File(projectDir, "build.gradle"),
      tasks = CopyOnWriteArrayList(),
    )

    return WorkspaceImpl(
      projectDir = projectDir,
      rootProject = root,
      subProjects = modules,
      projectSyncIssues = DefaultProjectSyncIssues(emptyList()),
    )
  }
}
