package com.itsaky.androidide.plugins.conf

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
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
  val isLibModule = plugins.hasPlugin("com.android.library")
  assert(isAppModule || isLibModule) {
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

  if (isAppModule) {
    extensions.configure(ApplicationExtension::class.java) {
      compileSdk = BuildConfig.compileSdk

      defaultConfig {
        minSdk = BuildConfig.minSdk
        targetSdk = BuildConfig.targetSdk
        versionCode = projectVersionCode
        versionName = simpleVersionName
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }

      compileOptions {
        sourceCompatibility = BuildConfig.javaVersion17
        targetCompatibility = BuildConfig.javaVersion17
        isCoreLibraryDesugaringEnabled = !plugins.hasPlugin(NoDesugarPlugin::class.java)
      }

      lint {
        checkDependencies = true
      }

      packaging {
        resources {
          excludes.addAll(
              listOf(
                  "META-INF/CHANGES",
                  "META-INF/README.md",
              )
          )
          pickFirsts.addAll(
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
        }
      }

      testOptions {
        unitTests.isIncludeAndroidResources = true
      }

      buildFeatures {
        viewBinding = true
        buildConfig = true
      }

      buildTypes {
        getByName("debug") {
          isMinifyEnabled = false
        }
        getByName("release") {
          isMinifyEnabled = true
          proguardFiles(
              getDefaultProguardFile("proguard-android-optimize.txt"),
              "proguard-rules.pro",
          )
        }
      }

      if (project.plugins.hasPlugin("com.itsaky.androidide.core-app")) {
        packaging {
          jniLibs {
            useLegacyPackaging = true
          }
        }

        splits {
          abi {
            reset()
            isEnable = true
            isUniversalApk = false
            if (isFDroidBuild) {
              include(FDroidConfig.fDroidBuildArch!!)
            } else {
              include(*flavorsAbis.keys.toTypedArray())
            }
          }
        }
      }
    }
  } else if (isLibModule) {
    extensions.configure(LibraryExtension::class.java) {
      compileSdk = BuildConfig.compileSdk

      defaultConfig {
        minSdk = BuildConfig.minSdk
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
          abiFilters.clear()
          abiFilters += flavorsAbis.keys
        }
      }

      compileOptions {
        sourceCompatibility = BuildConfig.javaVersion17
        targetCompatibility = BuildConfig.javaVersion17
        isCoreLibraryDesugaringEnabled = !plugins.hasPlugin(NoDesugarPlugin::class.java)
      }

      lint {
        checkDependencies = true
      }

      packaging {
        resources {
          excludes.addAll(
              listOf(
                  "META-INF/CHANGES",
                  "META-INF/README.md",
              )
          )
          pickFirsts.addAll(
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
        }
      }

      testOptions {
        unitTests.isIncludeAndroidResources = true
      }

      buildFeatures {
        viewBinding = true
        buildConfig = true
      }

      buildTypes {
        getByName("debug") {
          isMinifyEnabled = false
        }
        getByName("release") {
          isMinifyEnabled = false
          proguardFiles(
              getDefaultProguardFile("proguard-android-optimize.txt"),
              "proguard-rules.pro",
          )
        }
      }
    }
  }

  configureCoreLibDesugaring(coreLibDesugDep)
}

private fun Project.configureCoreLibDesugaring(
    coreLibDesugDep: Provider<MinimalExternalModuleDependency>
) {
  if (!plugins.hasPlugin(NoDesugarPlugin::class.java)) {
    dependencies.add("coreLibraryDesugaring", coreLibDesugDep)
  }
}