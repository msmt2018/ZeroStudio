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

package com.itsaky.androidide.plugins.conf

import com.itsaky.androidide.build.config.BuildConfig
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/**
 * -------------------------------------------------------------------------------------------------
 * 文件名：configureJavaModule.kt
 * 用途与功能：
 *   为纯 Java 库项目提供标准化的编译目标和源码兼容性版本配置。
 *   适配 AGP 9.4.1、Kotlin 2.4.10+ 及 Gradle 9 构建缓存规范。
 * 工作流程线路图：
 *   1. 断言检查：验证当前 Project 是否正确应用了 "java-library" 插件。
 *   2. 通过 `configure<JavaPluginExtension>` 统一指定 sourceCompatibility 与 targetCompatibility。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 */
fun Project.configureJavaModule() {
  assert(plugins.hasPlugin("java-library")) {
    "${javaClass.simpleName} can only be applied to Java library projects."
  }

  configure<JavaPluginExtension> {
    sourceCompatibility = BuildConfig.javaVersion
    targetCompatibility = BuildConfig.javaVersion
  }
}