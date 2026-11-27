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

@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  includeBuild("../build-logic")

  repositories {
    google()
    maven("${mirror}")
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    google()
    maven("${mirror}")
    mavenCentral()
  }

  versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}

include(
  ":appintro",
  ":fuzzysearch",
  ":google-java-format",
  ":javac",
  ":javapoet",
  ":jaxp",
  ":java-compiler",
  ":jdk-compiler",
  ":jdk-jdeps",
  ":jdt",
  ":layoutlib-api",
  ":logback-core",
  ":compose-pullrefresh",
  "soraLanguageTextmate",
  ":editor",
  ":soraOnigurumaNative"
)

project(":soraOnigurumaNative").projectDir = file("../../modules/soraOnigurumaNative")

rootProject.name = "build-deps"

gradle.rootProject {
  project
      .file("../../local.properties")
      .takeIf { it.exists() }
      ?.copyTo(project.file("local.properties"), overwrite = true)
}
