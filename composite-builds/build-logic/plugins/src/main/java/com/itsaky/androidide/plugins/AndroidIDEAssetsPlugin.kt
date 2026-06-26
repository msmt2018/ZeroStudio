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

package com.itsaky.androidide.plugins

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.build.config.downloadVersion
import com.itsaky.androidide.plugins.tasks.AddAndroidJarToAssetsTask
import com.itsaky.androidide.plugins.tasks.AddFileToAssetsTask
import com.itsaky.androidide.plugins.tasks.GenerateInitScriptTask
import com.itsaky.androidide.plugins.tasks.GradleWrapperGeneratorTask
import com.itsaky.androidide.plugins.tasks.SetupAapt2Task
import com.itsaky.androidide.plugins.util.SdkUtils.getAndroidJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar

/**
 * Handles asset copying and generation.
 *
 * @author Akash Yadav
 */
class AndroidIDEAssetsPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.run {
      val wrapperGeneratorTaskProvider =
          tasks.register("generateGradleWrapper", GradleWrapperGeneratorTask::class.java)

      val androidComponentsExtension =
          extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

      val setupAapt2TaskTaskProvider = tasks.register("setupAapt2", SetupAapt2Task::class.java)

      val addAndroidJarTaskProvider =
          tasks.register("addAndroidJarToAssets", AddAndroidJarToAssetsTask::class.java) {
            androidJar = androidComponentsExtension.getAndroidJar(assertExists = true)
          }

      androidComponentsExtension.onVariants { variant ->
        val variantNameCapitalized = variant.name.capitalized()

        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            setupAapt2TaskTaskProvider,
            SetupAapt2Task::outputDirectory,
        )

        variant.sources.assets?.addGeneratedSourceDirectory(
            wrapperGeneratorTaskProvider,
            GradleWrapperGeneratorTask::outputDirectory,
        )

        variant.sources.assets?.addGeneratedSourceDirectory(
            addAndroidJarTaskProvider,
            AddAndroidJarToAssetsTask::outputDirectory,
        )

        // Init script generator
        val generateInitScript =
            tasks.register(
                "generate${variantNameCapitalized}InitScript",
                GenerateInitScriptTask::class.java,
            ) {
              mavenGroupId.set(BuildConfig.packageName)
              downloadVersion.set(this@run.downloadVersion)
            }

        variant.sources.assets?.addGeneratedSourceDirectory(
            generateInitScript,
            GenerateInitScriptTask::outputDir,
        )

        // Debugger/log host plugin AAR copier. The init script resolves this
        // file from ~/.androidide/init via flatDir, and GradleBuildService
        // extracts it from assets into that directory before launching builds.
        val copyIdeLogPluginAar =
            tasks.register(
                "copy${variantNameCapitalized}IdeLogPluginAar",
                AddFileToAssetsTask::class.java,
            ) {
              val pluginPath = ":ide-log-plugin"
              val pluginProject =
                  checkNotNull(rootProject.findProject(pluginPath)) {
                    "Cannot find the IDE log plugin module with project path: '$pluginPath'"
                  }
              dependsOn(pluginProject.tasks.getByName("assembleRelease"))
              inputFile.set(
                  pluginProject.layout.buildDirectory.file(
                      "outputs/aar/ide-log-plugin-release.aar"
                  )
              )
              fileName.set("ide-log-plugin-1.0.0.aar")
              baseAssetsPath.set("data/common")
            }

        variant.sources.assets?.addGeneratedSourceDirectory(
            copyIdeLogPluginAar,
            AddFileToAssetsTask::outputDirectory,
        )

        data class RuntimeArtifact(
            val taskName: String,
            val projectPath: String,
            val buildOutput: String,
            val assetName: String,
        )

        listOf(
            RuntimeArtifact(
                "LogsenderAar",
                ":logging:logsender",
                "outputs/aar/logsender-release.aar",
                "logsender.aar",
            ),
            RuntimeArtifact(
                "ToolingPluginJar",
                ":tooling:plugin",
                "libs/androidide-plugin.jar",
                "androidide-plugin.jar",
            ),
            RuntimeArtifact(
                "PluginConfigJar",
                ":tooling:plugin-config",
                "libs/plugin-config.jar",
                "plugin-config.jar",
            ),
        ).forEach { artifact ->
          val copyRuntimeArtifact =
              tasks.register(
                  "copy${variantNameCapitalized}${artifact.taskName}ToAssets",
                  AddFileToAssetsTask::class.java,
              ) {
                val artifactProject =
                    checkNotNull(rootProject.findProject(artifact.projectPath)) {
                      "Cannot find required log plugin runtime module: '${artifact.projectPath}'"
                    }
                dependsOn(artifactProject.tasks.getByName("assemble"))
                inputFile.set(artifactProject.layout.buildDirectory.file(artifact.buildOutput))
                fileName.set(artifact.assetName)
                baseAssetsPath.set("data/common")
              }

          variant.sources.assets?.addGeneratedSourceDirectory(
              copyRuntimeArtifact,
              AddFileToAssetsTask::outputDirectory,
          )
        }

        // Logger runtime JAR copier. Keep this separate from the generic runtime
        // artifacts so it can use the actual Jar task output and avoid hardcoded
        // archive paths while still writing logger.jar to assets/data/common.
        val copyLoggerJar =
            tasks.register(
                "copy${variantNameCapitalized}LoggerRuntimeJarToAssets",
                AddFileToAssetsTask::class.java,
            ) {
              val loggerPath = ":logging:logger"
              val loggerProject =
                  checkNotNull(rootProject.findProject(loggerPath)) {
                    "Cannot find the Logger module with project path: '$loggerPath'"
                  }
              val loggerJar = loggerProject.tasks.named("jar", Jar::class.java)
              dependsOn(loggerJar)

              inputFile.set(loggerJar.flatMap { it.archiveFile })
              fileName.set("logger.jar")
              baseAssetsPath.set("data/common")
            }

        variant.sources.assets?.addGeneratedSourceDirectory(
            copyLoggerJar,
            AddFileToAssetsTask::outputDirectory,
        )

        // Tooling API JAR copier
        val copyToolingApiJar =
            tasks.register(
                "copy${variantNameCapitalized}ToolingApiJar",
                AddFileToAssetsTask::class.java,
            ) {
              val implPath = ":tooling:impl"
              val toolingApi =
                  checkNotNull(rootProject.findProject(implPath)) {
                    "Cannot find the Tooling Impl module with project path: '$implPath'"
                  }
              dependsOn(toolingApi.tasks.getByName("copyJar"))

              val toolingApiJar = toolingApi.layout.buildDirectory.file("libs/tooling-api-all.jar")

              inputFile.set(toolingApiJar)
              baseAssetsPath.set("data/common")
            }

        variant.sources.assets?.addGeneratedSourceDirectory(
            copyToolingApiJar,
            AddFileToAssetsTask::outputDirectory,
        )

      }
    }
  }
}
