package com.itsaky.androidide.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.itsaky.androidide.build.config.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies the repository-wide Android SDK and NDK versions to standalone composite builds.
 *
 * The main ZeroStudio build configures Android modules through AndroidModuleConf.
 * This convention plugin exposes the same BuildConfig values to independent included builds.
 */
class AndroidSdkConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.withId("com.android.library") { configureLibrary(target) }
    target.plugins.withId("com.android.application") { configureApplication(target) }
  }

  private fun configureLibrary(project: Project) {
    project.extensions.configure(LibraryExtension::class.java) {
      compileSdk = BuildConfig.compileSdk
      ndkVersion = BuildConfig.ndkVersion
      defaultConfig {
        minSdk = BuildConfig.minSdk
        targetSdk = BuildConfig.targetSdk
      }
    }
  }

  private fun configureApplication(project: Project) {
    project.extensions.configure(ApplicationExtension::class.java) {
      compileSdk = BuildConfig.compileSdk
      ndkVersion = BuildConfig.ndkVersion
      defaultConfig {
        minSdk = BuildConfig.minSdk
        targetSdk = BuildConfig.targetSdk
      }
    }
  }
}
