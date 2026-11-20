package com.itsaky.androidide.plugins

import com.android.build.api.dsl.CommonExtension
import com.itsaky.androidide.build.config.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies the repository-wide Android SDK and NDK versions to standalone composite builds.
 *
 * The main ZeroStudio build configures its Android modules through AndroidModuleConf.
 * This convention plugin exposes the same BuildConfig values to independent included builds.
 */
class AndroidSdkConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.withId("com.android.library") { configureAndroidSdk(target) }
    target.plugins.withId("com.android.application") { configureAndroidSdk(target) }
  }

  private fun configureAndroidSdk(project: Project) {
    val extension =
        project.extensions.findByType(CommonExtension::class.java)
            ?: error("Android DSL extension is unavailable for ${project.path}")

    extension.apply {
      compileSdk = BuildConfig.compileSdk
      ndkVersion = BuildConfig.ndkVersion
      defaultConfig {
        minSdk = BuildConfig.minSdk
        targetSdk = BuildConfig.targetSdk
      }
    }
  }
}
