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

// =============================================================
//  v3.1 依赖与构建配置 (2026-06 重构后)
// =============================================================
//
//  v3.1 完全摒弃了 v2.1 时代的"进程内 K2 + D8 + assets 预打包 jar" 链路:
//    - 不再下载 / 解压 `assets/compose/compose-jars.zip` (AssetsComposeBundles 删除)
//    - 不再调用 K2JVMCompiler 编译用户 Composable (BundledComposeCompiler 删除)
//    - 不再调用 R8 内置 D8 dex 用户 Composable (BundledD8Dexer 删除)
//    - 不再维护进程内 DexCache (DexCache 删除)
//
//  dex 全部来自 gradle assemble 产物 (BuildService.executeTasks), 通过
//  ProjectContextSource 解析, DexClassLoader(parent=context.classLoader) 加载.
//
//  因此 build.gradle.kts 不再需要:
//    - composeCompilerJars / composeAarsForPreview / kotlinCompilerJars / bundledD8Jars
//    - copyComposeCompilerPlugin / extractComposeClasses / copyKotlinCompilerJars /
//      copyBundledD8Jars / compileRuntimeDex / packageComposeJars
//    - compileOnly kotlin-compiler-embeddable / implementation kotlin-reflect
//
//  compose runtime / ui / foundation / material3 等类只需要作为 IDE module 自身
//  compile dependency, 由 IDE 主 APK 的 PathClassLoader 在加载用户 dex 时通过
//  parent 委托解析.
// =============================================================

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

  // === Compose 运行时依赖 (必须在 IDE 主 classpath 可见, 用户 dex 才能在反射时找到) ===
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  // material-icons-extended: PreviewToolbar 用了 extended-only icon
  // (Smartphone/Tablet/Watch/Visibility/BugReport/AspectRatio/Brightness6/ZoomIn/ZoomOut
  //  等), 不在 material-icons-core (49 个核心 icon) 中, 必须保留.
  implementation("androidx.compose.material:material-icons-extended")
  implementation(libs.androidx.activity.compose)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // === AndroidX & 基础 UI (Preview Toolbar / 设备选择器自身 UI) ===
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.google.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.common.kotlin.coroutines.android)

  // === 【v3.3】调试模式: 反射读 LayoutNode 私有字段, 拿子节点 + bounds. ===
  // kotlin-reflect 提供 kotlin-reflect API, 同时通过 setAccessible 拿私有字段.
  implementation(libs.org.jetbrains.kotlin.reflect)

  // === AndroidIDE 内部模块 ===
  implementation(projects.core.common)
  implementation(projects.editor.impl)
  implementation(projects.editor.api)
  implementation(projects.core.resources)
  implementation(projects.logging.logger)
  implementation(projects.core.projects)
}
