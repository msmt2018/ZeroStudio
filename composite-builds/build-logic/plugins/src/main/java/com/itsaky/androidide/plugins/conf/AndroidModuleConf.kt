/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.plugins.conf

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ApplicationExtension
import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.build.config.FDroidConfig
import com.itsaky.androidide.build.config.isFDroidBuild
import com.itsaky.androidide.build.config.projectVersionCode
import com.itsaky.androidide.build.config.simpleVersionName
import com.itsaky.androidide.plugins.NoDesugarPlugin
import com.itsaky.androidide.plugins.util.SdkUtils.getAndroidJar
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * ABIs for which the product flavors will be created. The keys in this map are the names of the
 * product flavors; the value is no longer used to compute per-ABI versionCode (since the new
 * versionCode is shared across all ABIs) and is kept only for the [flavorsAbis] consumer
 * (`splits { abi { ... } }`).
 */
internal val flavorsAbis = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

fun Project.configureAndroidModule(coreLibDesugDep: Provider<MinimalExternalModuleDependency>) {
  val isAppModule = plugins.hasPlugin("com.android.application")
  assert(isAppModule || plugins.hasPlugin("com.android.library")) {
    "${javaClass.simpleName} can only be applied to Android projects"
  }

  val androidJar =
      extensions
          .getByType(AndroidComponentsExtension::class.java)
          .getAndroidJar(assertExists = true)
  val frameworkStubsJar =
      findProject(":utilities:framework-stubs")!!.file("libs/android.jar").also {
        it.parentFile.mkdirs()
      }

  if (!(frameworkStubsJar.exists() && frameworkStubsJar.isFile)) {
    androidJar.copyTo(frameworkStubsJar)
  }

  tasks.withType(KotlinCompile::class.java).configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
  }

  val android = extensions.getByType(CommonExtension::class.java)

  // AGP 9 moved DSL block methods from CommonExtension to concrete extension types.
  // The common properties remain available directly on CommonExtension.
  android.packaging.resources.excludes.addAll(
      listOf(
          "META-INF/CHANGES",
          "META-INF/README.md",
      )
  )
  android.packaging.resources.pickFirsts.addAll(
      listOf(
          "META-INF/eclipse.inf",
          "META-INF/LICENSE.md",
          "META-INF/AL2.0",
          "META-INF/LGPL2.1",
          "META-INF/INDEX.LIST",
          "about_files/LICENSE-2.0.txt",
          "plugin.xml",
          "plugin.properties",
          "about.mappings",
          "about.properties",
          "about.ini",
          "modeling32.png",
      )
  )

  android.compileSdk = BuildConfig.compileSdk
  android.ndkVersion = BuildConfig.ndkVersion

  android.defaultConfig.apply {
    minSdk = BuildConfig.minSdk
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // These properties belong to ApplicationDefaultConfig, not the shared library DSL.
  // Keep library modules free of application-only SDK/version/multidex settings.
  if (isAppModule) {
    extensions.getByType(ApplicationExtension::class.java).defaultConfig.apply {
      targetSdk = BuildConfig.targetSdk
      // versionCode = YYYYMMDDC (C 为当天打包递增计数器, 不补 0; 例 202606131)
      // versionName = vYYYYMMDD-NN-sha7 (NN 为 2 位 0 填充的当天打包递增计数器)
      // 两者均来自 ProjectConfig, 由 -Pide.build.dailyCounter / -Pide.build.gitShortSha 注入.
      versionCode = projectVersionCode
      versionName = simpleVersionName
      multiDexEnabled = true
    }
  }

  android.compileOptions.apply {
    sourceCompatibility = BuildConfig.javaVersion17
    targetCompatibility = BuildConfig.javaVersion17
  }

  configureCoreLibDesugaring(coreLibDesugDep)

  if (project.plugins.hasPlugin("com.itsaky.androidide.core-app")) {
    android.packaging.jniLibs.useLegacyPackaging = true
    android.splits.abi.apply {
      reset()
      isEnable = true
      isUniversalApk = false
      if (isFDroidBuild) {
        include(FDroidConfig.fDroidBuildArch!!)
      } else {
        include(*flavorsAbis.keys.toTypedArray())
      }
    }
    // All ABIs share the same versionCode; ABI is represented by the APK file name.
  } else {
    android.defaultConfig.ndk.abiFilters.apply {
      clear()
      addAll(flavorsAbis.keys)
    }
  }

  android.buildTypes.getByName("debug").isMinifyEnabled = false
  android.buildTypes.getByName("release").apply {
    isMinifyEnabled = isAppModule
    proguardFiles(
        android.getDefaultProguardFile("proguard-android-optimize.txt"),
        project.file("proguard-rules.pro"),
    )
  }

  android.testOptions.unitTests.isIncludeAndroidResources = true
  android.buildFeatures.viewBinding = true
  android.buildFeatures.buildConfig = true
  // buildFeatures.compose = true
}

private fun Project.configureCoreLibDesugaring(
    coreLibDesugDep: Provider<MinimalExternalModuleDependency>
) {
  if (!plugins.hasPlugin(NoDesugarPlugin::class.java)) {
    dependencies.add("coreLibraryDesugaring", coreLibDesugDep)
  }
}

// private fun Project.configureCoreLibDesugaring(
  // baseExtension: BaseExtension,
  // coreLibDesugDep: Provider<MinimalExternalModuleDependency>
// ) {
  // val coreLibDesugaringEnabled = !project.plugins.hasPlugin(NoDesugarPlugin::class.java)

  // // baseExtension.compileOptions.isCoreLibraryDesugaringEnabled = coreLibDesugaringEnabled

  // if (coreLibDesugaringEnabled) {
    // project.dependencies.add("coreLibraryDesugaring", coreLibDesugDep)
  // }
// }
