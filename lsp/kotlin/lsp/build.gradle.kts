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

  buildFeatures { compose = true 
                  buildConfig = false }
}

kapt {
  arguments { arg("eventBusIndex", "${BuildConfig.packageName}.events.LspKotlinEventsIndex") }
}

dependencies {
  kapt(projects.annotation.processors)
  kapt(libs.google.auto.service)

  api(projects.core.indexingApi)
  api(projects.core.lspApi)
  api(projects.core.lspModels)

  implementation(projects.lsp.kotlin.server)
  implementation(projects.lsp.kotlin.adapter)
  implementation(projects.lsp.kotlin.shared)

  implementation(libs.androidide.ts)
  implementation(libs.androidide.ts.java)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.common.editor)
  implementation(libs.common.javaparser)
  implementation(libs.common.utilcode)

  // UI/UX
  implementation(libs.bundles.compose) // androidx compose
  implementation(libs.androidx.core.ktx)

  implementation(libs.google.auto.service.annotations)
  implementation(libs.google.guava)
  implementation(libs.google.gson)
  implementation(libs.google.material)

  implementation(projects.core.actions)
  implementation(projects.core.common)
  implementation(projects.core.lspApi)
  implementation(projects.core.resources)
  implementation(projects.editor.api)
  implementation(projects.java.javacServices)
  implementation(projects.java.lsp)
  implementation(projects.termux.shell)
  implementation(projects.event.eventbusEvents)

  implementation(libs.composite.javac)
  implementation(libs.composite.javapoet)
  implementation(libs.composite.jaxp)
  implementation(libs.composite.jdkJdeps)
  implementation(libs.composite.jdt)
  implementation(libs.composite.googleJavaFormat)

  implementation(libs.androidx.core.ktx)
  implementation(libs.common.kotlin)

  // implementation(libs.org.jetbrains.kotlin.compiler.embeddable)
。 implementation(projects.modules.kotlinc)
  // implementation(libs.org.jetbrains.kotlin.scripting.compiler.embeddable)
  implementation(libs.common.asm)

  implementation(libs.common.org.eclipse.lsp4j)
  implementation(libs.common.lsp4j.jsonrpc)
}
