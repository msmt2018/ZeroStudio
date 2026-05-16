/**
 * ****************************************************************************
 * sora-editor - the awesome code editor for Android https://github.com/Rosemoe/sora-editor
 * Copyright (C) 2020-2024 Rosemoe
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 * Please contact Rosemoe by email 2073412493@qq.com if you need additional information or have any
 * questions
 * ****************************************************************************
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.library")
}

android {
  namespace = "io.github.rosemoe.sora.lsp"

  defaultConfig { consumerProguardFiles("consumer-rules.pro") }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin { jvmToolchain(17) }

tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }

// 这个publishAllPublicationsToBuildMavenLocalRepository空任务用于解决gradle构建时找不到publishAllPublicationsToBuildMavenLocalRepository
tasks.register("publishAllPublicationsToBuildMavenLocalRepository") {
  group = "null"
  description = "null."
  enabled = false
}

dependencies {
  compileOnly(libs.common.editor)
  implementation(libs.common.org.eclipse.lsp4j)
  implementation(libs.common.lsp4j.jsonrpc)
  implementation(libs.kotlinx.coroutines.core)
}
