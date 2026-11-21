package com.itsaky.androidide.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.itsaky.androidide.build.config.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies the repository-wide Android SDK and NDK versions to standalone composite builds.
 *
 * AGP 9 exposes the new public DSL interfaces. Configure their properties directly rather
 * than relying on the removed legacy "android" receiver.
 */
class AndroidSdkConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.withId("com.android.library") {
      target.extensions.getByType(LibraryExtension::class.java).apply {
        compileSdk = BuildConfig.compileSdk
        ndkVersion = BuildConfig.ndkVersion
        defaultConfig.minSdk = BuildConfig.minSdk
        defaultConfig.targetSdk = BuildConfig.targetSdk
      }
    }
    target.plugins.withId("com.android.application") {
      target.extensions.getByType(ApplicationExtension::class.java).apply {
        compileSdk = BuildConfig.compileSdk
        ndkVersion = BuildConfig.ndkVersion
        defaultConfig.minSdk = BuildConfig.minSdk
        defaultConfig.targetSdk = BuildConfig.targetSdk
      }
    }
  }
}
