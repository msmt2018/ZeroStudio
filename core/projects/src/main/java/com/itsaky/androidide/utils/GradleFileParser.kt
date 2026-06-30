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

package com.itsaky.androidide.utils

import java.io.File

/** @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null */
object GradleFileParser {

  fun parseModuleBuildGradle(moduleDir: File): GradleBuildInfo? {
    val buildGradleKts = File(moduleDir, "build.gradle.kts")
    val buildGradle = File(moduleDir, "build.gradle")

    val gradleFile =
        when {
          buildGradleKts.exists() -> buildGradleKts
          buildGradle.exists() -> buildGradle
          else -> return null
        }

    return try {
      val content = gradleFile.readText()
      val isKotlin = gradleFile.name.endsWith(".kts")

      val versionName = extractVersionName(content, isKotlin)
      val versionCode = extractVersionCode(content, isKotlin)
      val minSdk = extractMinSdk(content, isKotlin)
      val targetSdk = extractTargetSdk(content, isKotlin)
      val compileSdk = extractCompileSdk(content, isKotlin)

      GradleBuildInfo(
          versionName = versionName,
          versionCode = versionCode,
          minSdk = minSdk,
          targetSdk = targetSdk,
          compileSdk = compileSdk,
      )
    } catch (e: Exception) {
      null
    }
  }

  private fun extractVersionName(content: String, isKotlin: Boolean): String? {
    val patterns =
        listOf(
            Regex("""versionName\s*=\s*"([^"]+)""""),
            Regex("""versionName\s*=\s*'([^']+)'"""),
            Regex("""versionName\("([^"]+)"\)"""),
            Regex("""versionName\s+["']([^"']+)["']"""),
        )

    for (pattern in patterns) {
      pattern.find(content)?.groupValues?.getOrNull(1)?.let {
        return it
      }
    }
    return null
  }

  private fun extractVersionCode(content: String, isKotlin: Boolean): Int? {
    val patterns =
        listOf(
            Regex("""versionCode\s*=\s*(\d+)"""),
            Regex("""versionCode\((\d+)\)"""),
            Regex("""versionCode\s+(\d+)"""),
        )

    for (pattern in patterns) {
      pattern.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it
      }
    }
    return null
  }

  private fun extractMinSdk(content: String, isKotlin: Boolean): Int? {
    val patterns =
        listOf(
            Regex("""minSdk\s*=\s*(\d+)"""),
            Regex("""minSdkVersion\s*=\s*(\d+)"""),
            Regex("""minSdk\((\d+)\)"""),
            Regex("""minSdkVersion\((\d+)\)"""),
            Regex("""minSdkVersion\s+(\d+)"""),
            Regex("""minSdk\s+(\d+)"""),
        )

    for (pattern in patterns) {
      pattern.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it
      }
    }
    return null
  }

  private fun extractTargetSdk(content: String, isKotlin: Boolean): Int? {
    val patterns =
        listOf(
            Regex("""targetSdk\s*=\s*(\d+)"""),
            Regex("""targetSdkVersion\s*=\s*(\d+)"""),
            Regex("""targetSdk\((\d+)\)"""),
            Regex("""targetSdkVersion\((\d+)\)"""),
            Regex("""targetSdkVersion\s+(\d+)"""),
            Regex("""targetSdk\s+(\d+)"""),
        )

    for (pattern in patterns) {
      pattern.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it
      }
    }
    return null
  }

  private fun extractCompileSdk(content: String, isKotlin: Boolean): Int? {
    val patterns =
        listOf(
            Regex("""compileSdk\s*=\s*(\d+)"""),
            Regex("""compileSdkVersion\s*=\s*(\d+)"""),
            Regex("""compileSdk\((\d+)\)"""),
            Regex("""compileSdkVersion\((\d+)\)"""),
            Regex("""compileSdkVersion\s+(\d+)"""),
            Regex("""compileSdk\s+(\d+)"""),
            Regex("""compileSdk\s*\{[\s\S]*?version\s*=\s*release\((\d+)\)"""),
            Regex("""compileSdk\s*\{[\s\S]*?version\s*=\s*release\((\d+)\)"""),
        )

    for (pattern in patterns) {
      pattern.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it
      }
    }
    return null
  }

  data class GradleBuildInfo(
      val versionName: String?,
      val versionCode: Int?,
      val minSdk: Int?,
      val targetSdk: Int?,
      val compileSdk: Int?,
  )
}
