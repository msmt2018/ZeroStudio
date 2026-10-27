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

import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * -------------------------------------------------------------------------------------------------
 * 文件名：build.gradle.kts (Root Project)
 * 用途与功能：
 *   AndroidIDE 根构建脚本。统一管理子模块的通用编译配置、AGP 9.4.1 属性、
 *   Kotlin 2.4.10+ JVM 目标版本以及自定义构建类型（BuildTypes）。
 * 工作流程线路图：
 *   1. 声明根级插件引用（apply false 延迟加载）。
 *   2. subprojects 闭包拦截所有子项目：
 *      - 当应用 "com.android.library" 插件时，自动配置 LibraryExtension（CompileSdk、MinSdk、Java 17 兼容、dev 变体）。
 *      - 统一拦截所有 KotlinCompile 任务，将 jvmTarget 显式指定为 JVM_17。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 * 历史贡献者：AndroidIDE Team
 */

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
}

subprojects {
  plugins.withId("com.android.library") {
    extensions.configure<LibraryExtension> {
      compileSdk = 37

      defaultConfig {
        minSdk = 26
      }

      compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
      }


    }
  }

  tasks.withType<KotlinCompile>().configureEach { 
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21) 
  }
}