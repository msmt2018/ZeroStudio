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
  id("com.vanniktech.maven.publish.base")
  id("android-tree-sitter.ts")
}

description = "Android Java bindings for Tree Sitter."

android {
  namespace = "com.itsaky.androidide.treesitter"

  defaultConfig {
    // 将 consumer-rules.pro 合并到消费者（TinaIDE app）的 R8 规则中。
    // 缺少此声明会导致 R8 删除/重命名 JNI 依赖的类（如 TreeSitter.loadLibrary()），
    // 引发运行时 UnsatisfiedLinkError。
    consumerProguardFiles("consumer-rules.pro")
  }

  // 关键：确保生成并打包 `libandroid-tree-sitter.so`。
  // 上层（TinaIDE）会在运行时调用 `System.loadLibrary("android-tree-sitter")`，
  // 若未配置 externalNativeBuild，则只会编译 Java/Kotlin 代码，APK 中不会包含 JNI so。
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  defaultConfig {
    externalNativeBuild {
      cmake {
        // 额外参数由 `android-tree-sitter.ts` 插件注入（AUTOGEN_HEADERS）。
        // 此处仅声明存在，避免在某些 AGP 版本/配置下被视为“未启用 native build”。
      }
    }
  }
}

dependencies {
  implementation(projects.annotations)
  annotationProcessor(projects.annotationProcessors)

  testImplementation(projects.treeSitterAidl)
  testImplementation(projects.treeSitterJava)
  testImplementation(projects.treeSitterJson)
  testImplementation(projects.treeSitterKotlin)
  testImplementation(projects.treeSitterLog)
  testImplementation(projects.treeSitterXml)
  testImplementation(projects.treeSitterPython)
  testImplementation(libs.tests.google.truth)
  testImplementation(libs.tests.junit)
  testImplementation(libs.tests.robolectric)
  testImplementation(libs.tests.mockito)
}
