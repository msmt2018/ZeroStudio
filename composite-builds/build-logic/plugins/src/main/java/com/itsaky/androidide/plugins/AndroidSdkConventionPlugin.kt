package com.itsaky.androidide.plugins

import com.android.build.gradle.BaseExtension
import com.itsaky.androidide.build.config.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies the repository-wide Android SDK and NDK versions to standalone composite builds.
 *
 * Use the same stable AGP BaseExtension contract as AndroidModuleConf. The newer
 * ApplicationExtension/LibraryExtension defaultConfig DSL does not expose targetSdk
 * consistently across the AGP versions used by this repository.
 */
class AndroidSdkConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.withId("com.android.library") { configureAndroid(target) }
    target.plugins.withId("com.android.application") { configureAndroid(target) }
  }

  private fun configureAndroid(project: Project) {
    project.extensions.configure(BaseExtension::class.java) {
      compileSdkVersion(BuildConfig.compileSdk)
      ndkVersion = BuildConfig.ndkVersion
      defaultConfig {
        minSdk = BuildConfig.minSdk
        targetSdk = BuildConfig.targetSdk
      }
    }
  }
}
