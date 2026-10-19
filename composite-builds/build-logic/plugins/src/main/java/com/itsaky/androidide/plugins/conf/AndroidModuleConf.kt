package com.itsaky.androidide.plugins.conf

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
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

  // 配置 App 与 Library 通用 DSL
  @Suppress("UNCHECKED_CAST")
  val commonExtension =
      extensions.getByType(CommonExtension::class.java)
          as CommonExtension<*, *, *, *, *, *>

  commonExtension.run {
    compileSdk = BuildConfig.compileSdk

    defaultConfig {
      minSdk = BuildConfig.minSdk
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
  }

  // 配置 Application 模块专有 DSL
  extensions.findByType(ApplicationExtension::class.java)?.run {
    defaultConfig {
      targetSdk = BuildConfig.targetSdk
      versionCode = projectVersionCode
      versionName = simpleVersionName
      multiDexEnabled = true
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

  // 配置 Library 模块专有 DSL
  extensions.findByType(LibraryExtension::class.java)?.run {
    defaultConfig {
      multiDexEnabled = true
      ndk {
        abiFilters.clear()
        abiFilters += flavorsAbis.keys
      }
    }

    buildTypes {
      getByName("debug") {
        isMinifyEnabled = false
      }
      getByName("release") {
        // from AGP 8.4.0 onwards, enabling R8 on library projects results in missing class errors
        isMinifyEnabled = false
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
      }
    }
  }

  // 配置 coreLibraryDesugaring 依赖
  configureCoreLibDesugaring(coreLibDesugDep)
}

private fun Project.configureCoreLibDesugaring(
    coreLibDesugDep: Provider<MinimalExternalModuleDependency>
) {
  if (!plugins.hasPlugin(NoDesugarPlugin::class.java)) {
    dependencies.add("coreLibraryDesugaring", coreLibDesugDep)
  }
}