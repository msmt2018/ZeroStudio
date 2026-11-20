package com.itsaky.androidide.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.itsaky.androidide.build.config.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies repository SDK/NDK configuration to independent composite-build Android modules.
 *
 * AGP 9 exposes the public Android DSL extensions instead of the legacy BaseExtension.
 */
class AndroidSdkConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.withId("com.android.library") {
      target.extensions.configure(LibraryExtension::class.java) {
        compileSdk = BuildConfig.compileSdk
        ndkVersion = BuildConfig.ndkVersion
        defaultConfig {
          minSdk = BuildConfig.minSdk
          targetSdk = BuildConfig.targetSdk
        }
      }
    }
    target.plugins.withId("com.android.application") {
      target.extensions.configure(ApplicationExtension::class.java) {
        compileSdk = BuildConfig.compileSdk
        ndkVersion = BuildConfig.ndkVersion
        defaultConfig {
          minSdk = BuildConfig.minSdk
          targetSdk = BuildConfig.targetSdk
        }
      }
    }
  }
}
