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

package com.itsaky.androidide.templates.base.models

import com.itsaky.androidide.templates.base.models.DependencyConfiguration.DebugImplementation

data class Dependency(
    val configuration: DependencyConfiguration,
    val group: String,
    val artifact: String,
    val version: String?,
    val tomlAlias: String? = null,
    val versionRefName: String? = null,
) {
  fun value(useToml: Boolean, useKts: Boolean): String {
    if (useToml && tomlAlias != null) {
      val aliasRef = tomlAlias.replace("-", ".")
      return if (useKts) "${configuration.configName}(libs.$aliasRef)"
      else "${configuration.configName} libs.$aliasRef"
    }
    return if (useKts) "${configuration.configName}(\"${group}:${artifact}${optionalVersion()}\")"
    else "${configuration.configName} '${group}:${artifact}${optionalVersion()}'"
  }

  // fun value(): String {
  // return """
  // ${configuration.configName}("${group}:${artifact}${optionalVersion()}")
  // """
  // .trimIndent()
  // }

  // fun platformValue(): String {
  // return """
  // ${configuration.configName}(platform("${group}:${artifact}${optionalVersion()}"))
  // """
  // .trimIndent()
  // }

  fun platformValue(useToml: Boolean, useKts: Boolean): String {
    if (useToml && tomlAlias != null) {
      val aliasRef = tomlAlias.replace("-", ".")
      return if (useKts) "${configuration.configName}(platform(libs.$aliasRef))"
      else "${configuration.configName} platform(libs.$aliasRef)"
    }
    return if (useKts)
        "${configuration.configName}(platform(\"${group}:${artifact}${optionalVersion()}\"))"
    else "${configuration.configName} platform('${group}:${artifact}${optionalVersion()}')"
  }

  private fun optionalVersion() = version?.let { ":${it}" } ?: ""

  object AndroidX {
    private const val lifecycleVersion = "2.9.4"
    private const val navigationVersion = "2.9.5"

    @JvmStatic
    val AppCompat =
        parseDependency("androidx.appcompat:appcompat:1.7.1", tomlAlias = "androidx-appcompat")
    @JvmStatic
    val ConstraintLayout =
        parseDependency(
            "androidx.constraintlayout:constraintlayout:2.2.1",
            tomlAlias = "androidx-constraintlayout",
        )
    @JvmStatic val Core = parseDependency("androidx.core:core:1.17.0", tomlAlias = "androidx-core")
    @JvmStatic
    val Core_Ktx = parseDependency("androidx.core:core-ktx:1.17.0", tomlAlias = "androidx-core-ktx")
    @JvmStatic
    val VectorDrawable =
        parseDependency(
            "androidx.vectordrawable:vectordrawable:1.2.0",
            tomlAlias = "androidx-vectordrawable",
        )
    @JvmStatic
    val LifeCycle_LiveData =
        parseDependency(
            "androidx.lifecycle:lifecycle-livedata:${lifecycleVersion}",
            tomlAlias = "androidx-lifecycle-livedata",
        )
    @JvmStatic
    val LifeCycle_LiveData_Ktx =
        parseDependency(
            "androidx.lifecycle:lifecycle-livedata-ktx:${lifecycleVersion}",
            tomlAlias = "androidx-lifecycle-livedata-ktx",
        )
    @JvmStatic
    val LifeCycle_ViewModel =
        parseDependency(
            "androidx.lifecycle:lifecycle-viewmodel:${lifecycleVersion}",
            tomlAlias = "androidx-lifecycle-viewmodel",
        )
    @JvmStatic
    val LifeCycle_ViewModel_Ktx =
        parseDependency(
            "androidx.lifecycle:lifecycle-viewmodel-ktx:${lifecycleVersion}",
            tomlAlias = "androidx-lifecycle-viewmodel-ktx",
        )
    @JvmStatic
    val Navigation_Fragment =
        parseDependency(
            "androidx.navigation:navigation-fragment:${navigationVersion}",
            tomlAlias = "androidx-navigation-fragment",
        )
    @JvmStatic
    val Navigation_Ui =
        parseDependency(
            "androidx.navigation:navigation-ui:${navigationVersion}",
            tomlAlias = "androidx-navigation-ui",
        )
    @JvmStatic
    val Navigation_Fragment_Ktx =
        parseDependency(
            "androidx.navigation:navigation-fragment-ktx:${navigationVersion}",
            tomlAlias = "androidx-navigation-fragment-ktx",
        )
    @JvmStatic
    val Navigation_Ui_Ktx =
        parseDependency(
            "androidx.navigation:navigation-ui-ktx:${navigationVersion}",
            tomlAlias = "androidx-navigation-ui-ktx",
        )

    object Compose {
      @JvmStatic
      val LifeCycle_Runtime_Ktx =
          parseDependency(
              "androidx.lifecycle:lifecycle-runtime-ktx:2.9.2",
              tomlAlias = "androidx-lifecycle-runtime-ktx",
          )
      @JvmStatic
      val Activity =
          parseDependency(
              "androidx.activity:activity-compose:1.11.0",
              tomlAlias = "androidx-activity-compose",
          )
      @JvmStatic
      val BOM =
          parseDependency(
              "androidx.compose:compose-bom:2025.10.01",
              isPlatform = true,
              tomlAlias = "androidx-compose-bom",
          )
      @JvmStatic val UI = parseDependency("androidx.compose.ui:ui", tomlAlias = "androidx-ui")
      @JvmStatic
      val UI_Graphics =
          parseDependency("androidx.compose.ui:ui-graphics", tomlAlias = "androidx-ui-graphics")
      @JvmStatic
      val UI_Tooling_Preview =
          parseDependency(
              "androidx.compose.ui:ui-tooling-preview",
              tomlAlias = "androidx-ui-tooling-preview",
          )
      @JvmStatic
      val Material3 =
          parseDependency("androidx.compose.material3:material3", tomlAlias = "androidx-material3")
      @JvmStatic
      val UI_Tooling =
          parseDependency(
              "androidx.compose.ui:ui-tooling",
              configuration = DebugImplementation,
              tomlAlias = "androidx-ui-tooling",
          )
      @JvmStatic
      val UI_Test_Manifest =
          parseDependency(
              "androidx.compose.ui:ui-test-manifest",
              configuration = DebugImplementation,
              tomlAlias = "androidx-ui-test-manifest",
          )
    }
  }

  object Facebook {
    private const val lithoVersion = "0.50.2"

    @JvmStatic
    val LithoCore =
        parseDependency(
            "com.facebook.litho:litho-core:${lithoVersion}",
            tomlAlias = "facebook-litho-core",
        )
    @JvmStatic
    val LithoWidget =
        parseDependency(
            "com.facebook.litho:litho-widget:${lithoVersion}",
            tomlAlias = "facebook-litho-widget",
        )
    @JvmStatic
    val LithoProcessor =
        parseDependency(
            "com.facebook.litho:litho-processor:${lithoVersion}",
            tomlAlias = "facebook-litho-processor",
        )
    @JvmStatic
    val LithoFresco =
        parseDependency(
            "com.facebook.litho:litho-fresco:${lithoVersion}",
            tomlAlias = "facebook-litho-fresco",
        )
    @JvmStatic
    val LithoTesting =
        parseDependency(
            "com.facebook.litho:litho-testing:${lithoVersion}",
            tomlAlias = "facebook-litho-testing",
        )
    @JvmStatic
    val LithoSectionsCore =
        parseDependency(
            "com.facebook.litho:litho-sections-core:${lithoVersion}",
            tomlAlias = "facebook-litho-sections-core",
        )
    @JvmStatic
    val LithoSectionsWidget =
        parseDependency(
            "com.facebook.litho:litho-sections-widget:${lithoVersion}",
            tomlAlias = "facebook-litho-sections-widget",
        )
    @JvmStatic
    val LithoSectionsAnnotations =
        parseDependency(
            "com.facebook.litho:litho-sections-annotations:${lithoVersion}",
            tomlAlias = "facebook-litho-sections-annotations",
        )
    @JvmStatic
    val LithoSectionsProcessor =
        parseDependency(
            "com.facebook.litho:litho-sections-processor:${lithoVersion}",
            tomlAlias = "facebook-litho-sections-processor",
        )
    @JvmStatic
    val SoLoader =
        parseDependency("com.facebook.soloader:soloader:0.10.5", tomlAlias = "facebook-soloader")
  }

  object Google {
    @JvmStatic
    val Material =
        parseDependency("com.google.android.material:material:1.13.0", tomlAlias = "material")
  }
}
