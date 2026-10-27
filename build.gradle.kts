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
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("build-logic.root-project")

  // Android/Google 插件
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.protobuf) apply false
  alias(libs.plugins.com.google.devtools.ksp) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.firebase.crashlytics) apply false

  // Kotlin 相关插件
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.org.jetbrains.kotlin.plugin.compose) apply false
  alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization) apply false
  alias(libs.plugins.kotlin.sam.with.receiver) apply false 

  // Maven 插件
  alias(libs.plugins.maven.publish) apply false
  alias(libs.plugins.gradle.publish) apply false

  // 其它实用插件
  alias(libs.plugins.androidx.room) apply false
  alias(libs.plugins.benchmark) apply false
  alias(libs.plugins.baselineprofile) apply false
  // alias(libs.plugins.version.catalog.update) apply false
}

buildscript {
  dependencies {
    classpath(libs.nav.safe.args.gradle.plugin)
    classpath("io.realm:realm-gradle-plugin:10.19.0")
  }
}

// 根项目以 BuildConfig.packageName 作为默认组 ID
project.group = BuildConfig.packageName

subprojects {
  if (project != rootProject) {
    var group = project.parent!!.group
    if (project.parent != rootProject) {
      group = "${group}.${project.parent!!.name}"
    }
    project.group = group
  }

  // 始终加载 F-Droid 配置
  FDroidConfig.load(project)

  afterEvaluate { apply { plugin(AndroidIDEPlugin::class.java) } }

  project.version = rootProject.version

  configurations.all {
    resolutionStrategy.dependencySubstitution {
      substitute(module("com.itsaky.androidide.treesitter:android-tree-sitter"))
        .using(project(":editor:tree-sitter-ndk:android-tree-sitter"))
      substitute(module("com.itsaky.androidide.treesitter:annotations"))
        .using(project(":editor:tree-sitter-ndk:annotations"))
    }

     resolutionStrategy {
        force(libs.kotlin.metadata.jvm.get().toString())
        force(libs.slf4j.api.get().toString())
     }
  }

  plugins.withId("com.android.application") { configureAndroidModule(libs.androidx.libDesugaring) }
  plugins.withId("com.android.library") { configureAndroidModule(libs.androidx.libDesugaring) }
  plugins.withId("java-library") { configureJavaModule() }
  plugins.withId("com.vanniktech.maven.publish.base") { configureMavenPublish() }

  plugins.withId("com.gradle.plugin-publish") {
    configure<GradlePluginDevelopmentExtension> { 
      version = project.publishingVersion 
    }
  }

  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(BuildConfig.javaVersion.toString()))
      freeCompilerArgs.add("-Xstring-concat=inline")
    }
  }
  

}



// 根目录清理任务
tasks.register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }