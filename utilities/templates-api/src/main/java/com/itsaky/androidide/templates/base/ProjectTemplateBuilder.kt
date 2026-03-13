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

package com.itsaky.androidide.templates.base

import com.itsaky.androidide.managers.ToolsManager
import com.itsaky.androidide.templates.ModuleTemplate
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.ProjectTemplateData
import com.itsaky.androidide.templates.ProjectTemplateRecipeResult
import com.itsaky.androidide.templates.base.root.buildGradleSrcGroovy
import com.itsaky.androidide.templates.base.root.buildGradleSrcKts
import com.itsaky.androidide.templates.base.root.gradleWrapperProps
import com.itsaky.androidide.templates.base.root.settingsGradleSrcStr
import com.itsaky.androidide.templates.base.util.optonallyKts
import com.itsaky.androidide.utils.transferToStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Builder for building project templates.
 *
 * @author Akash Yadav
 * @author android_zero
 */
class ProjectTemplateBuilder :
  ExecutorDataTemplateBuilder<ProjectTemplateRecipeResult, ProjectTemplateData>() {

  private var _defModule: ModuleTemplateData? = null

  // Add flag to track if any modules use Compose
  var hasComposeModules = false
    private set

  @PublishedApi internal val defModuleTemplate: ModuleTemplate? = null

  @PublishedApi internal val modules = mutableListOf<ModuleTemplate>()

  @PublishedApi
  internal val defModule: ModuleTemplateData
    get() = checkNotNull(_defModule) { "Module template data not set" }

  /**
   * Set the template data that will be used to create the default application module in the project.
   *
   * @param data The module template data to use.
   */
  fun setDefaultModuleData(data: ModuleTemplateData) {
    _defModule = data
  }

  /**
   * Enable Compose support for the project. This will add the Compose Compiler plugin to the root
   * build.gradle
   */
  fun enableComposeSupport() {
    hasComposeModules = true
  }

  /**
   * Get the asset path for base root project template.
   *
   * @param path The path to the asset.
   * @see com.itsaky.androidide.templates.base.baseAsset
   */
  fun baseAsset(path: String) =
    com.itsaky.androidide.templates.base.util.baseAsset("root", path)

  /**
   * Get the `build.gradle[.kts] file for the project.
   */
  fun buildGradleFile(): File {
    return data.buildGradleFile()
  }

  /**
   * Writes the `build.gradle[.kts]` file in the project root directory.
   */
  fun buildGradle() {
    executor.save(buildGradleSrc(), buildGradleFile())
  }

  /**
   * Get the source for `build.gradle[.kts]` files.
   */
  fun buildGradleSrc(): String {
    // Check multiple signals to robustly detect if any module uses Compose
    val composeMarkerFile = File(data.projectDir, ".compose_enabled")
    val composeDetectedByScan = checkForComposeInProject()
    val shouldIncludeCompose =
        hasComposeModules || composeMarkerFile.exists() || composeDetectedByScan
    return if (data.useKts) buildGradleSrcKts(shouldIncludeCompose)
    else buildGradleSrcGroovy(shouldIncludeCompose)
  }

  /** Check if any modules in the project use Compose by scanning the project structure */
  private fun checkForComposeInProject(): Boolean {
    // Check if the default module uses Compose
    _defModule?.let { moduleData ->
      // Look for Compose-related files or settings
      val buildGradleFile = moduleData.buildGradleFile()
      if (buildGradleFile.exists()) {
        val content = buildGradleFile.readText()
        if (content.contains("compose") || content.contains("androidx.compose")) {
          return true
        }
      }
    }

    // Alternative: Check for a flag file or other indicator
    val composeMarkerFile = File(data.projectDir, ".compose_enabled")
    return composeMarkerFile.exists()
  }

  /** Writes the `settings.gradle[.kts]` file in the project root directory. */
  fun settingsGradle() {
    executor.save(settingsGradleSrc(), settingsGradleFile())
  }

  /**
   * Get the `settings.gradle[.kts]` file for this project.
   */
  fun settingsGradleFile(): File {
    return File(data.projectDir, data.optonallyKts("settings.gradle"))
  }

  /**
   * Get the source for `settings.gradle[.kts]`.
   */
  fun settingsGradleSrc(): String {
    return settingsGradleSrcStr()
  }

  /**
   * Writes the `gradle.properties` file in the root project.
   */
  fun gradleProps() {
    val name = "gradle.properties"
    val gradleProps = File(data.projectDir, name)
    executor.copyAsset(baseAsset(name), gradleProps)
  }

  /**
   * Writes/copies the Gradle Wrapper related files in the project directory.
   */
  fun gradleWrapper() {

    ZipInputStream(executor.openAsset(ToolsManager.getCommonAsset("gradle-wrapper.zip")).buffered())
        .use { zipIn ->
          val entriesToCopy = arrayOf("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar")

          var zipEntry: ZipEntry? = zipIn.nextEntry
          while (zipEntry != null) {
            if (zipEntry.name in entriesToCopy) {
              val fileOut = File(data.projectDir, zipEntry.name)
              fileOut.parentFile!!.mkdirs()

              fileOut.outputStream().buffered().use { outStream ->
                zipIn.transferToStream(outStream)
                outStream.flush()
              }
            }

            zipEntry = zipIn.nextEntry
          }

          val gradlew = File(data.projectDir, "gradlew")
          val gradlewBat = File(data.projectDir, "${gradlew.name}.bat")

          check(gradlew.exists()) { "'$gradlew' does not exist!" }
          check(gradlewBat.exists()) { "'$gradlew' does not exist!" }

          gradlew.setExecutable(true)
          gradlewBat.setExecutable(true)
        }

    gradleWrapperProps()
  }

  /**
   * Writes the `.gitignore` file in the project directory.
   */
  fun gitignore() {
    val gitignore = File(data.projectDir, ".gitignore")
    executor.copyAsset(baseAsset("gitignore"), gitignore)
  }

  /**
   * @author android_zero
   *
   * Updated to pass the 'description' to the ProjectTemplate constructor.
   */
  override fun buildInternal(): ProjectTemplate {
    return ProjectTemplate(modules, templateName!!, thumb!!, description, widgets!!, recipe!!)
  }
}

fun ProjectTemplateBuilder.generateTomlFile() {
    val tomlFile = File(data.projectDir, "gradle/libs.versions.toml")
    tomlFile.parentFile.mkdirs()
    
    val tomlContent = """
[versions]
agp = "8.13.0"
kotlin = "2.1.0"
coreKtx = "1.17.0"
appcompat = "1.7.1"
material = "1.13.0"
constraintlayout = "2.2.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
android-test = { id = "com.android.test", version.ref = "agp" }

    """.trimIndent()
    
    executor.save(tomlContent, tomlFile)
}

fun AndroidModuleTemplateBuilder.dependenciesBlock(isKts: Boolean): String {
    val sb = java.lang.StringBuilder()
    sb.append("dependencies {\n")
    
    if (data.useToml) {
        // 如果开启了 TOML，使用 Version Catalog 语法
        sb.append("    implementation(libs.androidx.core.ktx)\n")
        sb.append("    implementation(libs.androidx.appcompat)\n")
        sb.append("    implementation(libs.material)\n")
        sb.append("    implementation(libs.androidx.constraintlayout)\n")
    } else {
        // 否则使用传统的硬编码字符串
        val q = if (isKts) "\"" else "'"
        sb.append("    implementation(${q}androidx.core:core-ktx:1.17.0${q})\n")
        sb.append("    implementation(${q}androidx.appcompat:appcompat:1.7.1${q})\n")
        sb.append("    implementation(${q}com.google.android.material:material:1.13.0${q})\n")
        sb.append("    implementation(${q}androidx.constraintlayout:constraintlayout:2.2.1${q})\n")
    }
    
    sb.append("}\n")
    return sb.toString()
}