/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.plugin

import com.zerostudio.logplugin.plugin.util.PackageUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.PluginManager

/**
 * The init-script plugin that runs in the host project's Gradle build. It
 * applies the per-project plugin ([IdeLogPlugin]) and configures the
 * dependency resolution to find the local AAR and the [IdeLogInitScriptPlugin]'s
 * own classes.
 *
 * <p>Historically this code lived in the
 * {@code com.itsaky.androidide.gradle.AndroidIDEInitScriptPlugin} class; it
 * is moved here as part of PR-1 to consolidate the log + JDWP stack into
 * a single AAR.
 */
class IdeLogInitScriptPlugin : Plugin<Gradle> {

  companion object {
    private val logger = Logging.getLogger(IdeLogInitScriptPlugin::class.java)
  }

  override fun apply(target: Gradle) {
    initializeEncoding()

    target.settingsEvaluated { settings -> settings.addIdeRepositories() }

    target.rootProject { rootProject ->
      rootProject.buildscript.apply {
        repositories.addIdeRepositories()
        dependencies.apply {
          val pluginDep = rootProject.ideDependency(IDE_GROUP, "plugin")
          if (pluginDep is ProjectDependency) {
            // The plugin is built from a composite project; nothing to do.
          } else if (pluginDep is Dependency) {
            pluginDep.version ?: throw GradleException(
                "ide-log-plugin must declare a version")
          }
          add("classpath", pluginDep)
        }
      }
    }

    target.projectsLoaded { gradle ->
      gradle.rootProject.subprojects { sub ->
        if (!sub.buildFile.exists()) {
          return@subprojects
        }
        sub.afterEvaluate {
          logger.info("Applying ide-log-plugin to project '${sub.path}'")
          sub.pluginManager.apply(IDE_PLUGIN_ID)
        }
      }
    }
  }

  private fun initializeEncoding() {
    try {
      System.setProperty("file.encoding", "UTF-8")
      System.setProperty("sun.jnu.encoding", "UTF-8")
      System.setProperty("user.country", "US")
      System.setProperty("user.language", "en")
      logger.info("Platform encoding initialized to UTF-8")
    } catch (e: Exception) {
      logger.warn("Could not set encoding properties: ${e.message}")
    }
  }

  private fun Settings.addIdeRepositories() {
    dependencyResolutionManagement.repositories {
      it.google()
      it.mavenCentral()
      it.gradlePluginPortal()
    }
    pluginManagement.repositories {
      it.google()
      it.mavenCentral()
      it.gradlePluginPortal()
    }
  }

  private fun Project.ideDependency(group: String, name: String): Dependency {
    // Prefer a project dependency if the IDE side is using a composite
    // build; fall back to a normal module dependency otherwise.
    val root = rootProject
    val candidate = root.findProject(":$name")
    if (candidate != null) {
      return dependencies.project(mapOf("path" to ":$name"))
    }
    return dependencies.create("$group:$name:1.0.0")
  }

  companion object Constants {
    const val IDE_GROUP = "com.zerostudio"
    const val IDE_PLUGIN_ID = "com.zerostudio.ide-log-plugin"
  }
}
