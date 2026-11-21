package com.itsaky.androidide.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.itsaky.androidide.build.config.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies the repository-wide Android SDK and NDK versions to standalone composite builds.
 *
 * AGP 9 no longer registers the legacy com.android.build.gradle.BaseExtension type.
 * Configure the public Android DSL extension that is actually registered by each plugin.
 */
class AndroidSdkConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.withId("com.android.library") { configureLibrary(target) }
    target.plugins.withId("com.android.application") { configureApplication(target) }
  }

  private fun configureLibrary(project: Project) {
    project.extensions.configure(LibraryExtension::class.java) { android ->
      android.compileSdk = BuildConfig.compileSdk
      android.ndkVersion = BuildConfig.ndkVersion
      android.defaultConfig.minSdk = BuildConfig.minSdk
      android.defaultConfig.targetSdk = BuildConfig.targetSdk
    }
  }

  private fun configureApplication(project: Project) {
    project.extensions.configure(ApplicationExtension::class.java) { android ->
      android.compileSdk = BuildConfig.compileSdk
      android.ndkVersion = BuildConfig.ndkVersion
      android.defaultConfig.minSdk = BuildConfig.minSdk
      android.defaultConfig.targetSdk = BuildConfig.targetSdk
    }
  }
}
