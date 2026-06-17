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
  implementation(platform(libs.androidx.compose.bom))

  // Compose 基础依赖
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  // Material Icons Extended: v2.1 工具栏/设备选择器需要 Smartphone/Tablet/Watch/
  // Visibility/VisibilityOff/BugReport/AspectRatio/Brightness6/ZoomIn/ZoomOut
  // 等 extended-only icon, 这些不在 material-icons-core (49 个核心 icon) 中.
  // 版本由 compose-bom 1.6.0 统一管理, 与 compose 1.6.0 一致.
  implementation("androidx.compose.material:material-icons-extended")
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

  // 反射读取 LayoutNode 等需要 kotlin-reflect
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
}
