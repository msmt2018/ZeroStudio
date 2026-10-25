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
  kotlin("jvm")
    alias(libs.plugins.com.google.devtools.ksp)
}

kotlin { jvmToolchain(25) }

tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_25) }

dependencies {
  ksp(libs.google.auto.service)
  implementation(libs.google.auto.service.annotations)

  implementation(kotlin("stdlib"))

  implementation(projects.annotation.annotations)

  implementation(libs.androidx.annotation)
  implementation(libs.common.javapoet)
  implementation(libs.common.ksp)
}

sourceSets.main { java.srcDirs("src/main/kotlin") }
