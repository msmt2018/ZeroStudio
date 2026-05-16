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
  alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

android {
  namespace = "${BuildConfig.packageName}.compose.preview"

  buildFeatures {
    compose = true
    viewBinding = true
  }

  defaultConfig { consumerProguardFiles("proguard-rules.pro") }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {

  implementation("gnu.trove:trove:2.1.0")

  implementation(platform(libs.androidx.compose.bom))

  // Compose 基础依赖
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.activity.compose)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // AndroidX & 基础 UI
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.google.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.common.kotlin.coroutines.android)

  // AndroidIDE模块
  implementation(projects.core.common)
  implementation(projects.editor.impl)
  implementation(projects.editor.api)
  implementation(projects.core.resources)
  implementation(projects.logging.logger)
  implementation(projects.core.projects)
  implementation(projects.modules.kotlinc)

}
