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
 *  You should have received a `GNU General Public License`
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

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
 * -------------------------------------------------------------------------------------------------
 * 文件名：ProjectConfig.kt (Android Module Configuration)
 * 用途与功能：
 *   为项目中的 Android Application 模块及 Library 模块提供统一且标准化的 DSL 配置。
 *   适配 AGP 9.4.1、Kotlin 2.4.10+ 及 Gradle 9 构建缓存与签名规范。
 * 工作流程线路图：
 *   1. 校验当前 Project 是否合法应用了 Android 应用或库插件。
 *   2. 获取并同步 Android SDK 的 android.jar 至 :utilities:framework-stubs。
 *   3. 统一配置 KotlinCompile 的 JVM 17 目标。
 *   4. 根据模块类型（Application 还是 Library）：
 *      - 配置 compileSdk、minSdk、targetSdk、versionCode、versionName。
 *      - 配置 Java 17 兼容性及 Core Library Desugaring。
 *      - 配置 Lint 选项、资源排除/冲突合并策略（Packaging Options）。
 *      - 针对特定核心 App 模块注入 JniLibs 传统打包配置及 ABI Splits 架构过滤。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 * 历史贡献者：Akash Yadav
 */

/**
 * 为产品风味（Product Flavors）创建的 ABI 映射。
 * 键代表 ABI 名称；值保留给兼容 `splits { abi { ... } }` 的消费者使用。
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

  // 统一所有 Kotlin 编译任务的 JVM 目标为 21
  tasks.withType(KotlinCompile::class.java).configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
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
        sourceCompatibility = BuildConfig.javaVersion
        targetCompatibility = BuildConfig.javaVersion
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

        // AGP 9.4.1 兼容的 ABI Splits 策略
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
        sourceCompatibility = BuildConfig.javaVersion21
        targetCompatibility = BuildConfig.javaVersion21
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
        // compose = true //每个模块不是很统一，所以不要在这里添加compose
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
    }
  }

  configureCoreLibDesugaring(coreLibDesugDep)
}

/**
 * -------------------------------------------------------------------------------------------------
 * 函数名：configureCoreLibDesugaring
 * 用途与功能：
 *   为未声明 NoDesugarPlugin 的模块自动附加核心库 Desugaring 依赖。
 * -------------------------------------------------------------------------------------------------
 */
private fun Project.configureCoreLibDesugaring(
    coreLibDesugDep: Provider<MinimalExternalModuleDependency>
) {
  if (!plugins.hasPlugin(NoDesugarPlugin::class.java)) {
    dependencies.add("coreLibraryDesugaring", coreLibDesugDep)
  }
}