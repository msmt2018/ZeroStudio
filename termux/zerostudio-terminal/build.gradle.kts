@file:Suppress("UnstableApiUsage")

import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.TerminalBootstrapPackagesPlugin

plugins {
  id("com.android.library")
  id("kotlin-android")
}

apply { plugin(TerminalBootstrapPackagesPlugin::class.java) }

val packageVariant =
    System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7" // Default: "apt-android-7"

android {
  namespace = "com.termux"
  ndkVersion = BuildConfig.ndkVersion

  defaultConfig {
    buildConfigField(
        "String",
        "TERMUX_PACKAGE_VARIANT",
        "\"" + packageVariant + "\"",
    ) // Used by TermuxApplication class

    manifestPlaceholders["TERMUX_PACKAGE_NAME"] = BuildConfig.packageName
    manifestPlaceholders["TERMUX_APP_NAME"] = "AndroidIDE"

    externalNativeBuild { cmake { arguments += "-DPROJECT_BUILD_DIR=${project.buildDir}" } }
    externalNativeBuild {
      ndkBuild {
        cFlags(
            "-std=c11",
            "-Wall",
            "-Wextra",
            "-Werror",
            "-Os",
            "-fno-stack-protector",
            "-Wl,--gc-sections",
        )
      }
    }
  }

  externalNativeBuild {
    ndkBuild { path = file("src/main/cpp/Android.mk") }

    lint.disable += "ProtectedPermissions"

    testOptions { unitTests { isIncludeAndroidResources = true } }

    packaging.jniLibs.useLegacyPackaging = true
  }
}

dependencies {
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.core)
  implementation(libs.androidx.drawer)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.viewpager)
  implementation(libs.google.material)
  implementation(libs.google.guava)
  implementation(libs.common.markwon.core)
  implementation(libs.common.markwon.extStrikethrough)
  implementation(libs.common.markwon.linkify)
  implementation(libs.common.markwon.recycler)

  api(projects.core.common)
  api(projects.core.resources)
  implementation(projects.core.projects)
  implementation(projects.termux.view)
  implementation(projects.termux.shared)
  api(projects.utilities.preferences)
  // implementation(projects.core.actions)
  implementation(project(":core:actions"))

  implementation("com.google.android.material:material:1.12.0")

  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("androidx.recyclerview:recyclerview-selection:1.1.0")
  // 协程与生命周期
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.core:core-animation:1.0.0")
  implementation("androidx.compose.material3:material3-window-size-class:1.3.0")

  implementation("androidx.interpolator:interpolator:1.0.0")

  testImplementation(projects.testing.unitTest)
}

tasks.register("versionName") { doLast { print(project.rootProject.version) } }
