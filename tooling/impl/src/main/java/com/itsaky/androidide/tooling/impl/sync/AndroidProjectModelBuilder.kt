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
package com.itsaky.androidide.tooling.impl.sync

import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.itsaky.androidide.tooling.api.IAndroidProject
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.impl.internal.AndroidProjectImpl
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.BuildController
import org.slf4j.LoggerFactory

/**
 * Builds model for Android application and library projects.
 *
 * Supports AGP 7.x, 8.x, and 9.x through proper version detection and fallback handling.
 *
 * @author Akash Yadav
 */
class AndroidProjectModelBuilder(initializationParams: InitializeProjectParams) :
    AbstractModelBuilder<AndroidProjectModelBuilderParams, IAndroidProject>(initializationParams) {

  private val log = LoggerFactory.getLogger(AndroidProjectModelBuilder::class.java)

  override fun build(param: AndroidProjectModelBuilderParams): IAndroidProject {
    val (controller, module, versions, syncIssueReporter) = param

    val androidParams = initializationParams.androidParams
    val projectPath = module.gradleProject.path

    // Detect AGP version from build file
    val detectedAgpVersion = detectAgpVersion(module.gradleProject)

    log("Detected AGP version: {}", detectedAgpVersion ?: "unknown")

    val basicModel = controller.getModelAndLog(module, BasicAndroidProject::class.java)
    val androidModel = controller.getModelAndLog(module, AndroidProject::class.java)
    val androidDsl = controller.getModelAndLog(module, AndroidDsl::class.java)

    // Use the provided Versions model (may be null for AGP < 9.x)
    val effectiveVersions = versions

    val variantNames = basicModel.variants.map { it.name }
    log("${variantNames.size} build variants found for project '$projectPath': $variantNames")

    var androidVariant = androidParams.variantSelections[projectPath]

    if (androidVariant != null && !variantNames.contains(androidVariant)) {
      log(
          "Configured variant '$androidVariant' not found for project '$projectPath'. Falling back to default variant."
      )
      androidVariant = null
    }

    val configurationVariant = androidVariant ?: variantNames.firstOrNull()
    if (configurationVariant.isNullOrBlank()) {
      throw ModelBuilderException(
          "No variant found for project '$projectPath'. providedVariant=$androidVariant"
      )
    }

    log("Selected build variant '$configurationVariant' for project '$projectPath'")

    try {
      log("Forcing dependency resolution for Android module: $projectPath")
      var downloadedCount = 0
      for (dependency in module.dependencies) {
        if (dependency is IdeaSingleEntryLibraryDependency) {
          try {
            val file = dependency.file
            if (file.exists()) {
              downloadedCount++
            }
          } catch (fileEx: Exception) {
            log("Failed to access dependency file: ${fileEx.message}")
          }
        }
      }
      log("Forced resolution of $downloadedCount dependencies for module: $projectPath")
    } catch (resEx: Exception) {
      log("Failed to pre-resolve dependencies: ${resEx.message}")
    }

    val variantDependencies =
        controller.getModelAndLog(
            module,
            VariantDependencies::class.java,
            ModelBuilderParameter::class.java,
        ) {
          it.variantName = configurationVariant
          it.additionalArtifactsInModel = true
          it.dontBuildRuntimeClasspath = false
          it.dontBuildUnitTestRuntimeClasspath = true
          it.dontBuildScreenshotTestRuntimeClasspath = true
          it.dontBuildAndroidTestRuntimeClasspath = true
          it.dontBuildTestFixtureRuntimeClasspath = true
          it.dontBuildHostTestRuntimeClasspath = emptyMap()
        }

    reportProjectSyncIssues(controller, module, syncIssueReporter)

    return AndroidProjectImpl(
        module.gradleProject,
        configurationVariant,
        basicModel,
        androidModel,
        variantDependencies,
        effectiveVersions,
        androidDsl,
        detectedAgpVersion
    )
  }

  /**
   * Fetch and report sync issues without failing the whole model build.
   *
   * AGP builds the ProjectSyncIssues model after the selected variant dependency model has run.
   * In some projects, especially after switching to a release variant, AGP can cancel this optional
   * model while the rest of the Android models are already available. Treat that as a best-effort
   * diagnostics step so variant initialization can continue.
   */
  private fun reportProjectSyncIssues(
      controller: BuildController,
      module: IdeaModule,
      syncIssueReporter: ISyncIssueReporter,
  ) {
    try {
      controller.findModel(module, ProjectSyncIssues::class.java)?.also { syncIssues ->
        syncIssueReporter.reportAll(syncIssues)
      }
    } catch (err: Exception) {
      log.warn(
          "Failed to fetch ProjectSyncIssues model for Android module '{}'; continuing without AGP sync issues.",
          module.gradleProject.path,
          err,
      )
    }
  }

  /**
   * Detect the AGP version from the Gradle project's build file.
   */
  private fun detectAgpVersion(gradleProject: org.gradle.tooling.model.GradleProject): String? {
    return try {
      val buildDir = gradleProject.projectDirectory
      val buildFileKts = java.io.File(buildDir, "build.gradle.kts")
      val buildFile = java.io.File(buildDir, "build.gradle")
      
      val buildFileToRead = when {
        buildFileKts.exists() -> buildFileKts
        buildFile.exists() -> buildFile
        else -> return null
      }

      val content = buildFileToRead.readText()

      // Look for android gradle plugin version in plugins block
      val pluginsPattern = Regex(
        """id\(["']com\.android\.(application|library|feature|dynamic-feature)["']\)\s*version\s*["'](.+?)["']"""
      )
      pluginsPattern.find(content)?.groupValues?.getOrNull(2)?.let { return it }

      // Look in buildscript classpath
      val buildscriptPattern = Regex(
        """classpath\s+["']com\.android\.tools\.build:gradle:(.+?)["']"""
      )
      buildscriptPattern.find(content)?.groupValues?.getOrNull(1)
    } catch (e: Exception) {
      log.debug("Failed to detect AGP version: {}", e.message)
      null
    }
  }

  /**
   * Fetch Versions model if available (AGP 9.x+).
   * For AGP 8.x and below, this always returns null.
   */
  private fun fetchVersionsIfAvailable(
    controller: BuildController,
    module: IdeaModule
  ): Versions? {
    return try {
      // AGP 9.x has Versions model, try to fetch it
      controller.getModel(module, Versions::class.java).also {
        log("Successfully fetched Versions model")
      }
    } catch (e: org.gradle.tooling.UnknownModelException) {
      log("Versions model not available (AGP 8.x or below): {}", e.message)
      null
    } catch (e: Exception) {
      log.warn("Failed to fetch Versions model: {}", e.message)
      null
    }
  }
}
