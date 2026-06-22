package com.itsaky.androidide.repository.materials

import com.itsaky.androidide.projects.materials.MaterialSourceType
import com.itsaky.androidide.projects.materials.ProjectMaterialItem
import java.io.File

/**
 * Top level grouping used by [ProjectMaterialsFragment] to organise the
 * materials tree into a manageable set of tabs/dropdown entries.
 *
 * @author android_zero
 */
enum class ProjectMaterialCategory(
    val id: String,
    val displayName: String,
    private val matcher: (ProjectMaterialItem) -> Boolean,
) {
  ALL("all", "All Materials", { true }),
  BUILD_CACHE("build_cache", "Build Cache", { it.sourceType == MaterialSourceType.BUILD_CACHE }),
  GRADLE_CACHE("gradle_cache", "Gradle Cache", { item ->
    item.sourceType == MaterialSourceType.PROJECT_FILE &&
        item.path?.contains("${File.separator}.gradle${File.separator}") == true
  }),
  PROJECT_DEPS(
      "project_deps",
      "Project Sources & Deps",
      { item ->
        item.sourceType == MaterialSourceType.PROJECT_FILE && item.path != null &&
            !item.path.contains("${File.separator}.gradle${File.separator}")
      },
  ),
  MAVEN_SDK(
      "maven_sdk",
      "Maven & SDK",
      { item ->
        item.sourceType == MaterialSourceType.AGP_BUILDER_MODEL ||
            item.sourceType == MaterialSourceType.SDK_TOOLING
      },
  ),
  TOOLING("tooling", "Tooling API", { it.sourceType == MaterialSourceType.GRADLE_TOOLING_API });

  fun matches(item: ProjectMaterialItem): Boolean = matcher(item)

  companion object {
    /** Categories shown as quick-access tabs in the top toolbar. */
    val TAB_CATEGORIES: List<ProjectMaterialCategory> = listOf(ALL, BUILD_CACHE, GRADLE_CACHE, PROJECT_DEPS, MAVEN_SDK)

    fun fromId(id: String?): ProjectMaterialCategory = entries.firstOrNull { it.id == id } ?: ALL
  }
}
