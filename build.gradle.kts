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

@file:Suppress("UnstableApiUsage")

import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.build.config.FDroidConfig
import com.itsaky.androidide.build.config.publishingVersion
import com.itsaky.androidide.plugins.AndroidIDEPlugin
import com.itsaky.androidide.plugins.conf.configureAndroidModule
import com.itsaky.androidide.plugins.conf.configureJavaModule
import com.itsaky.androidide.plugins.conf.configureMavenPublish
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  // 全局变量环境
  id("build-logic.root-project")

  // Android/Google
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.protobuf) apply false
  alias(libs.plugins.com.google.devtools.ksp) apply false
  // Hilt 插件必须在根项目与 KSP 同一作用域声明 (apply false),
  // 否则 Hilt 插件检测 KSP 时会因 classloader 不一致报错 (Dagger #3965)
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.firebase.crashlytics) apply false

  // kotlin相关
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.org.jetbrains.kotlin.plugin.compose) apply false
  alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization) apply false

  // maven插件
  alias(libs.plugins.maven.publish) apply false
  alias(libs.plugins.gradle.publish) apply false

  // 其它插件
  alias(libs.plugins.androidx.room) apply false
  alias(libs.plugins.benchmark) apply false
  alias(libs.plugins.baselineprofile) apply false
}

buildscript {
  dependencies {
    classpath(libs.kotlin.gradle.plugin)
    classpath(libs.nav.safe.args.gradle.plugin)
    classpath("io.realm:realm-gradle-plugin:10.19.0")
  }
}

// Root project has 'com.itsaky.androidide' as the group ID
project.group = BuildConfig.packageName

subprojects {
  if (project != rootProject) {
    var group = project.parent!!.group
    if (project.parent != rootProject) {
      group = "${group}.${project.parent!!.name}"
    }
    project.group = group
  }

  // Always load the F-Droid config
  FDroidConfig.load(project)

  afterEvaluate { apply { plugin(AndroidIDEPlugin::class.java) } }

  project.version = rootProject.version

  // 将 Maven 上的 android-tree-sitter / annotations 工件替换为本地项目模块。
  // Maven 语法包（如 tree-sitter-java:4.3.2）的 POM 声明了对 android-tree-sitter:4.3.2 的
  // compile-scope 传递依赖，该 Maven 旧工件不包含本地新增的 TSQueryProgressCallback /
  // execWithOptions 等 API，会遮蔽本地模块的新类导致编译失败。
  // 通过 dependencySubstitution 将所有对 Maven 工件的引用（含传递依赖）重定向到本地项目。
  configurations.all {
    resolutionStrategy.dependencySubstitution {
      substitute(module("com.itsaky.androidide.treesitter:android-tree-sitter"))
        .using(project(":editor:tree-sitter-ndk:android-tree-sitter"))
      substitute(module("com.itsaky.androidide.treesitter:annotations"))
        .using(project(":editor:tree-sitter-ndk:annotations"))
    }

    // 强制 kotlin-metadata-jvm 版本对齐项目 Kotlin 版本 (2.2.20)。
    // Hilt 2.59 (兼容 AGP 8.x) 自带的 kotlinx-metadata-jvm 只支持到 Kotlin metadata 2.1.0,
    // 与 Kotlin 2.2.20 生成的 metadata 2.2.0 不兼容, 会触发:
    //   error: [Hilt] Provided Metadata instance has version 2.2.0, while maximum supported version is 2.1.0
    // 利用 Dagger 2.57+ 已将 kotlin-metadata-jvm unshaded 的特性,
    // 显式强制升级到 2.2.20 让 Hilt 编译器能读取 metadata 2.2.0。
    // 必须放在 configurations.all 中以覆盖所有配置 (含 kapt / androidTest 等)。
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.2.20")
  }

  plugins.withId("com.android.application") { configureAndroidModule(libs.androidx.libDesugaring) }
  plugins.withId("com.android.library") { configureAndroidModule(libs.androidx.libDesugaring) }
  plugins.withId("java-library") { configureJavaModule() }
  plugins.withId("com.vanniktech.maven.publish.base") { configureMavenPublish() }

  plugins.withId("com.gradle.plugin-publish") {
    configure<GradlePluginDevelopmentExtension> { version = project.publishingVersion }
  }

  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(BuildConfig.javaVersion.toString()))
      freeCompilerArgs.add("-Xstring-concat=inline")
    }
  }
}

tasks.register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }
