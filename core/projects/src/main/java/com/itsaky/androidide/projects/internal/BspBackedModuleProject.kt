package com.itsaky.androidide.projects.internal

import com.itsaky.androidide.projects.GradleProject
import com.itsaky.androidide.tooling.api.models.GradleTask
import java.io.File

/** GradleProject enriched with BSP source/dependency/variant metadata. */
internal class BspBackedModuleProject(
  name: String,
  description: String,
  path: String,
  projectDir: File,
  buildDir: File,
  buildScript: File,
  tasks: List<GradleTask>,
  val languages: List<String>,
  val sourceRoots: Set<File>,
  val dependencyUris: Set<String>,
  val variants: List<String>,
) : GradleProject(name, description, path, projectDir, buildDir, buildScript, tasks)
