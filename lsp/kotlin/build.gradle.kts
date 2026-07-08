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

import com.itsaky.androidide.build.config.BuildConfig

plugins {
  id("com.android.library")
  id("kotlin-android")
  id("kotlin-kapt")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "${BuildConfig.packageName}.lsp.kotlin"

  sourceSets {
    getByName("androidTest") { assets.srcDirs(rootProject.file("utilities/framework-stubs/libs")) }
  }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

  buildFeatures { compose = true }
}

kapt {
  arguments { arg("eventBusIndex", "${BuildConfig.packageName}.events.LspKotlinEventsIndex") }
}

dependencies {
  kapt(projects.annotation.processors)
  kapt(libs.google.auto.service)

  implementation(projects.editor.treeSitterNdk.androidTreeSitter)
  implementation(libs.androidide.ts.java)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  api(libs.common.editor)
  implementation(libs.common.javaparser)
  implementation(libs.common.utilcode)

  implementation(libs.bundles.io.markwon)
  // UI/UX
  implementation(libs.bundles.compose) // androidx compose
  implementation(libs.androidx.core.ktx)

  implementation(libs.google.auto.service.annotations)
  implementation(libs.google.guava)
  implementation(libs.google.gson)
  implementation(libs.google.material)

  api(projects.core.actions)
  api(projects.core.common)
  api(projects.core.lspApi)
  api(projects.core.resources)
  api(projects.editor.api)
  api(projects.java.javacServices)
  api(projects.java.lsp)
  api(projects.termux.shell)
  implementation(projects.termux.shared)
  api(projects.event.eventbusEvents)

  implementation(libs.composite.javac)
  implementation(libs.composite.javapoet)
  implementation(libs.composite.jaxp)
  implementation(libs.composite.jdkJdeps)
  implementation(libs.composite.jdt)
  implementation(libs.composite.googleJavaFormat)

  implementation(libs.androidx.core.ktx)
  implementation(libs.common.kotlin)

  // implementation(projects.modules.kotlinc)
  implementation(libs.common.asm)

  api(libs.common.org.eclipse.lsp4j)
  api(libs.common.lsp4j.jsonrpc)
}
