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

import com.android.builder.model.v2.ide.SyncIssue
import com.android.builder.model.v2.models.BasicAndroidProject
import com.itsaky.androidide.builder.model.DefaultProjectSyncIssues
import com.itsaky.androidide.builder.model.DefaultSyncIssue
import com.itsaky.androidide.builder.model.IDESyncIssue
import com.itsaky.androidide.builder.model.shouldBeIgnored
import com.itsaky.androidide.tooling.api.IAndroidProject
import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.util.AgpGradleVersionChecker
import com.itsaky.androidide.tooling.api.util.AndroidModulePropertyCopier
import com.itsaky.androidide.tooling.impl.Main
import com.itsaky.androidide.tooling.impl.Main.finalizeLauncher
import com.itsaky.androidide.tooling.impl.internal.ProjectImpl
import java.io.File
import java.io.Serializable
import org.gradle.tooling.ConfigurableLauncher
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaProject
import org.slf4j.LoggerFactory

/**
 * Utility class to build the project models.
 *
 * @author Akash Yadav
 */
class RootModelBuilder(initializationParams: InitializeProjectParams) :
    AbstractModelBuilder<RootProjectModelBuilderParams, IProject>(initializationParams),
    Serializable {

  private val serialVersionUID = 1L

  private val log = LoggerFactory.getLogger(RootModelBuilder::class.java)

  override fun build(param: RootProjectModelBuilderParams): IProject {

    val (projectConnection, cancellationToken) = param

    // do not reference the 'initializationParams' field in the
    val initializationParams = initializationParams

    val executor = projectConnection.action { controller ->
      val ideaProject = controller.getModelAndLog(IdeaProject::class.java)
      val buildEnvironment = runCatching { controller.getModel(BuildEnvironment::class.java) }.getOrNull()
      val gradleBuild = runCatching { controller.getModel(GradleBuild::class.java) }.getOrNull()

      val ideaModules = ideaProject.modules
      val modulePaths = mapOf(*ideaModules.map { it.name to it.gradleProject.path }.toTypedArray())
      val rootModule =
          ideaModules.find { it.gradleProject.parent == null }
              ?: throw ModelBuilderException("Unable to find root project")

      val rootProjectVersions = getAndroidVersions(rootModule, controller)
      
      // For AGP 9.x, the Versions model might not be available, so we also try
      // to fetch BasicAndroidProject as a fallback to detect Android projects
      val isAndroidProject = rootProjectVersions != null || getBasicAndroidProject(rootModule, controller) != null

      val syncIssues = hashSetOf<DefaultSyncIssue>()
      val syncIssueReporter = ISyncIssueReporter {
        if (it.shouldBeIgnored()) {
          // this SyncIssue should not be shown to the user
          return@ISyncIssueReporter
        }

        val issue = it as? DefaultSyncIssue ?: AndroidModulePropertyCopier.copy(it)
        syncIssues.add(issue)
      }

      // Check AGP and Gradle version compatibility for Android projects
      if (isAndroidProject) {
        val detectedAgpVersion = rootProjectVersions?.agp ?: detectAgpVersion(rootModule.gradleProject)
        val gradleVersion = buildEnvironment?.gradle?.gradleVersion
        
        if (detectedAgpVersion != null && gradleVersion != null) {
          checkAgpGradleCompatibility(detectedAgpVersion, gradleVersion, syncIssueReporter)
        }
      }

      val rootProject =
          if (isAndroidProject) {
            // Root project is an Android project
            if (rootProjectVersions != null) {
              checkAgpVersion(rootProjectVersions, syncIssueReporter)
            }
            AndroidProjectModelBuilder(initializationParams)
                .build(
                    AndroidProjectModelBuilderParams(
                        controller,
                        rootModule,
                        rootProjectVersions,
                        syncIssueReporter,
                    )
                )
          } else {
            GradleProjectModelBuilder(initializationParams)
                .build(rootModule.gradleProject, buildEnvironment, gradleBuild)
          }

      val projects = ideaModules.map { ideaModule ->
        ModuleProjectModelBuilder(initializationParams)
            .build(
                ModuleProjectModelBuilderParams(
                    controller,
                    ideaProject,
                    ideaModule,
                    modulePaths,
                    syncIssueReporter,
                )
            )
      }

      return@action ProjectImpl(
          rootProject,
          rootModule.gradleProject.path,
          projects,
          DefaultProjectSyncIssues(syncIssues),
      )
    }

    finalizeLauncher(executor)
    applyAndroidModelBuilderProps(executor)

    if (cancellationToken != null) {
      executor.withCancellationToken(cancellationToken)
    }

    val logger = LoggerFactory.getLogger("RootModelBuilder")
    logger.warn("Starting build. See build output for more details...")

    val clientRef = Main.client
    if (clientRef != null) {
      clientRef.logOutput("Starting build...")
    }

    return executor.run().also { logger.debug("Build action executed. Result: {}", it) }
  }

  private fun applyAndroidModelBuilderProps(launcher: ConfigurableLauncher<*>) {
    launcher.addProperty(IAndroidProject.PROPERTY_BUILD_MODEL_ONLY, true)
    launcher.addProperty(IAndroidProject.PROPERTY_INVOKED_FROM_IDE, true)
  }

  private fun ConfigurableLauncher<*>.addProperty(property: String, value: Any) {
    addArguments(String.format("-P%s=%s", property, value))
  }
  
  /**
   * Detect the AGP version from the Gradle project's build file.
   */
  private fun detectAgpVersion(gradleProject: org.gradle.tooling.model.GradleProject): String? {
    return try {
      val projectDirectory = gradleProject.projectDirectory
      val buildFileKts = File(projectDirectory, "build.gradle.kts")
      val buildFileGroovy = File(projectDirectory, "build.gradle")
      val buildFile = when {
        buildFileKts.exists() -> buildFileKts
        buildFileGroovy.exists() -> buildFileGroovy
        else -> return null
      }

      val content = buildFile.readText()

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
   * Check AGP and Gradle version compatibility and report any issues to syncIssueReporter.
   */
  private fun checkAgpGradleCompatibility(
      agpVersion: String,
      gradleVersion: String,
      syncIssueReporter: ISyncIssueReporter
  ) {
    val result = AgpGradleVersionChecker.checkCompatibility(agpVersion, gradleVersion)
    
    // Report compatibility issues
    result.issues.forEach { issueMessage ->
      val syncIssue = DefaultSyncIssue(
        data = "$agpVersion:$gradleVersion",
        message = issueMessage,
        multiLineMessage = null,
        severity = SyncIssue.SEVERITY_ERROR,
        type = IDESyncIssue.TYPE_AGP_VERSION_TOO_NEW
      )
      syncIssueReporter.report(syncIssue)
    }
    
    // Report compatibility warnings
    result.warnings.forEach { warningMessage ->
      val syncIssue = DefaultSyncIssue(
        data = "$agpVersion:$gradleVersion",
        message = warningMessage,
        multiLineMessage = null,
        severity = SyncIssue.SEVERITY_WARNING,
        type = SyncIssue.TYPE_UNRESOLVED_DEPENDENCY
      )
      syncIssueReporter.report(syncIssue)
    }
    
    log.info("AGP and Gradle version compatibility check: ${if (result.isCompatible) "compatible" else "incompatible"}")
  }
}
