package com.itsaky.androidide.plugins

import com.android.build.gradle.BaseExtension
import com.itsaky.androidide.build.config.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies repository SDK/NDK configuration to independent composite-build Android modules.
 * Uses the same BaseExtension configuration surface as AndroidModuleConf.
 */
class AndroidSdkConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.withId("com.android.library") { configureAndroidSdk(target) }
    target.plugins.withId("com.android.application") { configureAndroidSdk(target) }
  }

  private fun configureAndroidSdk(project: Project) {
    project.extensions.getByType(BaseExtension::class.java).run {
      compileSdkVersion(BuildConfig.compileSdk)
      ndkVersion = BuildConfig.ndkVersion
      defaultConfig {
        minSdk = BuildConfig.minSdk
        targetSdk = BuildConfig.targetSdk
      }
    }
  }
}
