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

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging

/**
 * The per-project plugin applied by [IdeLogInitScriptPlugin]. It is
 * responsible for:
 *
 * <ul>
 *   <li>Adding the AAR produced by this module to the host project's
 *       `implementation` configuration so that the IDE's log / JDWP code
 *       is available at runtime.
 *   <li>Wiring the ContentProvider that the AAR provides into the
 *       merged manifest (the manifest merge step is automatic because the
 *       AAR declares the provider in its own manifest).
 *   <li>Enabling core library desugaring so that the AAR can use modern
 *       Java APIs (java.time, java.util.concurrent.*, etc.) even on
 *       older Android API levels.
 * </ul>
 */
class IdeLogPlugin : Plugin<Project> {

  companion object {
    private val logger = Logging.getLogger(IdeLogPlugin::class.java)
  }

  override fun apply(target: Project) {
    val group = target.group.toString()
    if (group.startsWith("com.zerostudio")) {
      // Don't apply to ourselves.
      return
    }

    target.afterEvaluate {
      try {
        if (target.plugins.hasPlugin("com.android.application")) {
          configureApp(target)
        } else if (target.plugins.hasPlugin("com.android.library")) {
          configureLib(target)
        }
        target.dependencies.apply {
          add("implementation", "com.zerostudio:ide-log-plugin:1.0.0")
          add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:2.0.4")
        }
      } catch (t: Throwable) {
        logger.warn("Failed to configure ide-log-plugin on ${target.path}", t)
      }
    }
  }

  private fun configureApp(target: Project) {
    val ext = target.extensions.findByType(AppExtension::class.java) ?: return
    ext.compileOptions.isCoreLibraryDesugaringEnabled = true
  }

  private fun configureLib(target: Project) {
    val ext = target.extensions.findByType(LibraryExtension::class.java) ?: return
    ext.compileOptions.isCoreLibraryDesugaringEnabled = true
  }
}
