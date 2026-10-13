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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  //noinspection JavaPluginLanguageLevel
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
}

// tasks.withType<KotlinCompile> {
// kotlinOptions.jvmTarget(17)
// }
tasks.withType<KotlinCompile>().configureEach { 
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    
    // 💡由于项目全局环境强制使用了较新的 2.3.20 编译器，
    // 这里必须显式将语言和 API 级别强拉到 2.0 或 2.1 以上，彻底越过过时的 1.8 语言后端限制
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
  } 
}

dependencies { api(gradleApi()) }
