/*
 *  This file is part of android-tree-sitter.
 *
 *  android-tree-sitter library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  android-tree-sitter library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *  along with android-tree-sitter.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
  id("com.android.library")
}

description = "Android Java bindings for Tree Sitter."

android {
  namespace = "com.itsaky.androidide.treesitter"
  ndkVersion = "27.1.12297006"

  defaultConfig {
    // 将 consumer-rules.pro 合并到消费者（TinaIDE app）的 R8 规则中。
    // 缺少此声明会导致 R8 删除/重命名 JNI 依赖的类（如 TreeSitter.loadLibrary()），
    // 引发运行时 UnsatisfiedLinkError。
    consumerProguardFiles("consumer-rules.pro")

    ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")) }

    externalNativeBuild { cmake { arguments("-DCMAKE_CXX_FLAGS=-std=c++17") } }
  }

  buildFeatures { buildConfig = false }

  // 关键：确保生成并打包 `libandroid-tree-sitter.so`。
  // 上层（TinaIDE）会在运行时调用 `System.loadLibrary("android-tree-sitter")`，
  // 若未配置 externalNativeBuild，则只会编译 Java/Kotlin 代码，APK 中不会包含 JNI so。
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
}

dependencies {
  implementation(projects.editor.treeSitterNdk.annotations)
  annotationProcessor(projects.editor.treeSitterNdk.annotationProcessors)

  // 语法包保持远程 Maven 依赖（本地仅核心 android-tree-sitter 已本地化）
  testImplementation(projects.editor.treeSitterNdk.aidl)
  testImplementation(libs.androidide.ts.java)
  testImplementation(libs.androidide.ts.json)
  testImplementation(libs.androidide.ts.kotlin)
  testImplementation(libs.androidide.ts.log)
  testImplementation(libs.androidide.ts.xml)
  testImplementation(libs.androidide.ts.python)
  testImplementation(libs.tests.google.truth)
  testImplementation(libs.tests.junit)
  testImplementation(libs.tests.robolectric)
  testImplementation(libs.tests.mockito)
}
