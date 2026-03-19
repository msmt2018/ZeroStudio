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

package com.itsaky.androidide.templates.base.root

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.base.ProjectTemplateBuilder

private const val compose_kotlinExtraPlugin = "org.jetbrains.kotlin.plugin.compose"

/**
 * @author android_zero (优化：支持 TOML Version Catalogs 语法动态生成)
 */
internal fun ProjectTemplateBuilder.buildGradleSrcKts(hasCompose: Boolean = false): String {
  if (data.useToml) {
    return """
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    ${if (data.language == Language.Kotlin) "alias(libs.plugins.kotlin.android) apply false" else ""}
    ${if (hasCompose) "alias(libs.plugins.kotlin.compose) apply false" else ""}
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
    """.trimIndent()
  } else {
    return """
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "${data.version.gradlePlugin}" apply false
    id("com.android.library") version "${data.version.gradlePlugin}" apply false
    ${ktPluginKts(hasCompose)}     
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
    """.trimIndent()
  }
}

internal fun ProjectTemplateBuilder.buildGradleSrcGroovy(hasCompose: Boolean = false): String {
  if (data.useToml) {
    return """
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias libs.plugins.android.application apply false
    alias libs.plugins.android.library apply false
    ${if (data.language == Language.Kotlin) "alias libs.plugins.kotlin.android apply false" else ""}
    ${if (hasCompose) "alias libs.plugins.kotlin.compose apply false" else ""}
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
    """.trimIndent()
  } else {
    return """
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id 'com.android.application' version '${data.version.gradlePlugin}' apply false
    id 'com.android.library' version '${data.version.gradlePlugin}' apply false
    ${ktPluginGroovy(hasCompose)}     
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
    """.trimIndent()
  }
}
private fun ProjectTemplateBuilder.ktPlugin(hasCompose: Boolean = false) =
    if (data.language == Language.Kotlin) {
      if (data.useKts) ktPluginKts(hasCompose) else ktPluginGroovy(hasCompose)
    } else ""

private fun ProjectTemplateBuilder.ktPluginKts(hasCompose: Boolean = false): String {
  if (data.language != Language.Kotlin) return ""
  val kotlinPlugin = """id("org.jetbrains.kotlin.android") version "${data.version.kotlin}" apply false"""
  val composePlugin = if (hasCompose) {
    "\n    id(\"$compose_kotlinExtraPlugin\") version \"${data.version.kotlin}\" apply false"
  } else ""

  return kotlinPlugin + composePlugin
}

private fun ProjectTemplateBuilder.ktPluginGroovy(hasCompose: Boolean = false): String {
  if (data.language != Language.Kotlin) return ""
  val kotlinPlugin = """id 'org.jetbrains.kotlin.android' version '${data.version.kotlin}' apply false"""
  val composePlugin = if (hasCompose) {
    "\n    id '$compose_kotlinExtraPlugin' version '${data.version.kotlin}' apply false"
  } else ""

  return kotlinPlugin + composePlugin
}
