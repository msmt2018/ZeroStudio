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

plugins { `kotlin-dsl` }

repositories {
  google()
  gradlePluginPortal()
  mavenCentral()
}

tasks.withType<KotlinCompile>().configureEach { 
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    
    // 💡由于项目全局环境强制使用了较新的 2.3.20 编译器，
    // 这里必须显式将语言和 API 级别强拉到 2.0 或 2.1 以上，彻底越过过时的 1.8 语言后端限制
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
  } 
}

dependencies {
  implementation(projects.buildLogic.common)
  implementation(projects.buildLogic.desugaring)
  implementation(projects.buildLogic.propertiesParser)

  implementation("com.android.tools.build:gradle:${libs.versions.agp.asProvider().get()}")
  implementation(libs.maven.publish)

  implementation(kotlin("gradle-plugin"))

  implementation(libs.common.jkotlin)
  implementation(libs.common.antlr4)
  implementation(libs.google.gson)
  implementation(libs.google.java.format)
}

gradlePlugin {
  plugins {
    create("com.itsaky.androidide.build") {
      id = "com.itsaky.androidide.build"
      implementationClass = "com.itsaky.androidide.plugins.AndroidIDEPlugin"
    }
    create("com.itsaky.androidide.core-app") {
      id = "com.itsaky.androidide.core-app"
      implementationClass = "com.itsaky.androidide.plugins.AndroidIDECoreAppPlugin"
    }
    create("com.itsaky.androidide.build.propsparser") {
      id = "com.itsaky.androidide.build.propsparser"
      implementationClass = "com.itsaky.androidide.plugins.PropertiesParserPlugin"
    }
    create("com.itsaky.androidide.build.lexergenerator") {
      id = "com.itsaky.androidide.build.lexergenerator"
      implementationClass = "com.itsaky.androidide.plugins.LexerGeneratorPlugin"
    }
  }
}
