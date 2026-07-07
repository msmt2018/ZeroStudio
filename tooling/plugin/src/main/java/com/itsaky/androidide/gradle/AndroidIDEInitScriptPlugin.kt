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

package com.itsaky.androidide.gradle

import com.itsaky.androidide.buildinfo.BuildInfo
import com.itsaky.androidide.tooling.api.LogSenderConfig._PROPERTY_IS_TEST_ENV
import com.itsaky.androidide.tooling.api.LogSenderConfig._PROPERTY_MAVEN_LOCAL_REPOSITORY
import java.io.File
import java.net.URI
import org.gradle.StartParameter
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging

/**
 * Plugin for the AndroidIDE's Gradle Init Script.
 *
 * @author Akash Yadav
 */
class AndroidIDEInitScriptPlugin : Plugin<Gradle> {

  companion object {

    private val logger = Logging.getLogger(AndroidIDEInitScriptPlugin::class.java)
  }

  override fun apply(target: Gradle) {
    initializeEncoding()

    target.settingsEvaluated { settings -> settings.addDependencyRepositories() }

    target.rootProject { rootProject ->
      rootProject.buildscript.apply {
        dependencies.apply {
          val gradlePluginDep = rootProject.ideDependency(LIB_GROUP_TOOLING, "plugin")
          if (gradlePluginDep is ExternalModuleDependency) {
            gradlePluginDep.isChanging = false
          }
          add("classpath", gradlePluginDep)
        }
        repositories.addDependencyRepositories(rootProject.gradle.startParameter)
      }
    }

    target.projectsLoaded { gradle ->
      // 【修复】原来只对 rootProject.subprojects 应用,单 module 项目
      // (即根目录就是 app module 的情况) 完全被漏掉,LogSenderPlugin /
      // IdeLogInitScriptPlugin 都不会跑,LogReceiverService 永远收不到
      // host 端日志。
      //
      // 现在先 apply 到 rootProject(单 module 场景),再统一遍历
      // subprojects(多 module 场景),跳过 root 自身避免重复 apply。
      fun applyIdePlugin(project: Project) {
        if (!project.buildFile.exists()) return
        project.afterEvaluate {
          logger.info("Applying plugin '${BuildInfo.PACKAGE_NAME}' to project '${project.path}'")
          project.pluginManager.apply(BuildInfo.PACKAGE_NAME)
        }
      }
      applyIdePlugin(gradle.rootProject)
      gradle.rootProject.subprojects { sub -> applyIdePlugin(sub) }
    }
  }

  private fun initializeEncoding() {
    try {
      // Set system properties for encoding
      System.setProperty("file.encoding", "UTF-8")
      System.setProperty("sun.jnu.encoding", "UTF-8")
      System.setProperty("user.country", "US")
      System.setProperty("user.language", "en")

      logger.info("Platform encoding initialized to UTF-8")
    } catch (e: Exception) {
      logger.warn("Could not set encoding properties: ${e.message}")
    }
  }

  private fun Settings.addDependencyRepositories() {
    val (isTestEnv, mavenLocalRepos) = getTestEnvProps(startParameter)
    addDependencyRepositories(isTestEnv, mavenLocalRepos)
  }

  @Suppress("UnstableApiUsage")
  private fun Settings.addDependencyRepositories(
      isMavenLocalEnabled: Boolean,
      mavenLocalRepo: String,
  ) {
    dependencyResolutionManagement.run {
      repositories.configureRepositories(isMavenLocalEnabled, mavenLocalRepo)
    }

    pluginManagement.apply {
      repositories.configureRepositories(isMavenLocalEnabled, mavenLocalRepo)
    }
  }

  private fun RepositoryHandler.addDependencyRepositories(startParams: StartParameter) {
    val (isTestEnv, mavenLocalRepos) = getTestEnvProps(startParams)
    configureRepositories(isTestEnv, mavenLocalRepos)
  }

  private fun getTestEnvProps(startParameter: StartParameter): Pair<Boolean, String> {
    return startParameter.run {
      val isTestEnv =
          projectProperties.containsKey(_PROPERTY_IS_TEST_ENV) &&
              projectProperties[_PROPERTY_IS_TEST_ENV].toString().toBoolean()
      val mavenLocalRepos =
          projectProperties.getOrDefault(_PROPERTY_MAVEN_LOCAL_REPOSITORY, "").toString()

      isTestEnv to mavenLocalRepos
    }
  }

  private fun RepositoryHandler.configureRepositories(
      isMavenLocalEnabled: Boolean,
      mavenLocalRepos: String,
  ) {
    // Always add standard repositories first
    google()
    mavenCentral()
    gradlePluginPortal()

    if (isMavenLocalEnabled && mavenLocalRepos.isNotBlank()) {
      logger.info("Using local maven repository for classpath resolution...")

      mavenLocalRepos.split(':').forEach { mavenLocalRepo ->
        if (mavenLocalRepo.isBlank()) {
          mavenLocal()
        } else {
          logger.info("Local repository path: $mavenLocalRepo")
          val repo = File(mavenLocalRepo)
          if (repo.exists() && repo.isDirectory) {
            maven { repository -> repository.url = repo.toURI() }
          }
        }
      }
    } else {
      // Add JitPack for GitHub packages
      maven { repository ->
        repository.name = "JitPack"
        repository.setUrl("https://jitpack.io")
      }
    }

    // Add custom repositories if configured
    addCustomRepositories()
  }

  private fun RepositoryHandler.addCustomRepositories() {
    try {
      val snapshotsRepo = BuildInfo.SNAPSHOTS_REPOSITORY
      if (isValidRepository(snapshotsRepo)) {
        logger.info("Adding snapshots repository: $snapshotsRepo")
        maven { repository ->
          repository.name = "AndroidIDE Snapshots"
          repository.url = URI.create(snapshotsRepo)
        }
      }

      val publicRepo = BuildInfo.PUBLIC_REPOSITORY
      if (isValidRepository(publicRepo)) {
        logger.info("Adding public repository: $publicRepo")
        maven { repository ->
          repository.name = "AndroidIDE Public"
          repository.setUrl(publicRepo)
        }
      }
    } catch (e: Exception) {
      logger.warn("Error configuring custom repositories: ${e.message}")
    }
  }

  private fun isValidRepository(repoUrl: String?): Boolean {
    return !repoUrl.isNullOrBlank() &&
        repoUrl != "null" &&
        !repoUrl.startsWith("@@") &&
        !repoUrl.endsWith("@@") &&
        (repoUrl.startsWith("http://") || repoUrl.startsWith("https://"))
  }
}
