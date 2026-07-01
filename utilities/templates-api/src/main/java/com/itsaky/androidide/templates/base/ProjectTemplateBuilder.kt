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
import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ModuleTemplate
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.ProjectTemplateData
import com.itsaky.androidide.templates.ProjectTemplateRecipeResult
import com.itsaky.androidide.templates.TemplateRecipe
import com.itsaky.androidide.templates.base.models.Dependency
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
 * @author Akash Yadav (Historical contributors)
 * @author android_zero (Added TOML Support & logic optimization)
 */
class ProjectTemplateBuilder :
    ExecutorDataTemplateBuilder<ProjectTemplateRecipeResult, ProjectTemplateData>() {

  private var _defModule: ModuleTemplateData? = null

  // Flag to track if any modules use Compose
  var hasComposeModules = false
    private set

  // Flag to track if any modules use the baseline profile plugin
  var hasBaselineProfileModules = false
    private set

  @PublishedApi internal val defModuleTemplate: ModuleTemplate? = null

  @PublishedApi internal val modules = mutableListOf<ModuleTemplate>()

  @PublishedApi internal val moduleBuilders = mutableListOf<AndroidModuleTemplateBuilder>()

  @PublishedApi
  internal val defModule: ModuleTemplateData
    get() = checkNotNull(_defModule) { "Module template data not set" }

  /**
   * Set the template data that will be used to create the default application module in the
   * project.
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
   * Enable baseline profile support for the project. This will add the
   * `androidx.baselineprofile` and `com.android.test` plugins to the generated
   * `libs.versions.toml` so that modules can use them.
   */
  fun enableBaselineProfileSupport() {
    hasBaselineProfileModules = true
  }

  /**
   * Get the asset path for base root project template.
   *
   * @param path The path to the asset.
   * @see com.itsaky.androidide.templates.base.baseAsset
   */
  fun baseAsset(path: String) = com.itsaky.androidide.templates.base.util.baseAsset("root", path)

  /** Get the `build.gradle[.kts]` file for the project. */
  fun buildGradleFile(): File {
    return data.buildGradleFile()
  }

  /** Writes the `build.gradle[.kts]` file in the project root directory. */
  fun buildGradle() {
    executor.save(buildGradleSrc(), buildGradleFile())
  }

  /** Get the source for `build.gradle[.kts]` files. */
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

  /** Get the `settings.gradle[.kts]` file for this project. */
  fun settingsGradleFile(): File {
    return File(data.projectDir, data.optonallyKts("settings.gradle"))
  }

  /** Get the source for `settings.gradle[.kts]`. */
  fun settingsGradleSrc(): String {
    return settingsGradleSrcStr()
  }

  /** Writes the `gradle.properties` file in the root project. */
  fun gradleProps() {
    val name = "gradle.properties"
    val gradleProps = File(data.projectDir, name)
    executor.copyAsset(baseAsset(name), gradleProps)
  }

  /** Writes/copies the Gradle Wrapper related files in the project directory. */
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

          check(gradlew.exists()) { "'${gradlew}' does not exist!" }
          check(gradlewBat.exists()) { "'${gradlewBat}' does not exist!" }

          gradlew.setExecutable(true)
          gradlewBat.setExecutable(true)
        }

    gradleWrapperProps()
  }

  /** Writes the `.gitignore` file in the project directory. */
  fun gitignore() {
    val gitignore = File(data.projectDir, ".gitignore")
    executor.copyAsset(baseAsset("gitignore"), gitignore)
  }

  /**
   * 动态生成 TOML 配置文件。
   *
   * @author android_zero 功能：负责统一导出标准现代化的依赖版本集，解耦 build.gradle。
   */
  fun generateToml() {
    if (!data.useToml) return

    val versions = mutableMapOf<String, String>()
    val libraries = mutableMapOf<String, String>()
    val plugins = mutableMapOf<String, String>()

    // 注入基础版本和插件
    versions["agp"] = data.version.gradlePlugin
    versions["kotlin"] = data.version.kotlin
    plugins["android-application"] = "{ id = \"com.android.application\", version.ref = \"agp\" }"
    plugins["android-library"] = "{ id = \"com.android.library\", version.ref = \"agp\" }"

    if (data.language == Language.Kotlin) {
      plugins["kotlin-android"] =
          "{ id = \"org.jetbrains.kotlin.android\", version.ref = \"kotlin\" }"
    }
    if (hasComposeModules) {
      plugins["kotlin-compose"] =
          "{ id = \"org.jetbrains.kotlin.plugin.compose\", version.ref = \"kotlin\" }"
    }

    if (hasBaselineProfileModules) {
      // android-test 插件沿用 agp 版本
      plugins["android-test"] = "{ id = \"com.android.test\", version.ref = \"agp\" }"
      plugins["baselineprofile"] =
          "{ id = \"androidx.baselineprofile\", version.ref = \"baselineprofile\" }"
      // 若版本尚未注入（避免覆盖已有版本号），写入一个默认版本
      if (!versions.containsKey("baselineprofile")) {
        versions["baselineprofile"] = "1.2.4"
      }
    }

    // 收集子模块注册的所有依赖项并去重
    val allDeps = mutableSetOf<Dependency>()
    moduleBuilders.forEach { builder ->
      allDeps.addAll(builder.dependencies)
      allDeps.addAll(builder.platforms)
    }

    // 常见的通用顶级域名 (TLD)，在生成变量名时可以忽略它们，让命名更精简
    val commonTlds = setOf("com", "org", "net", "io", "dev")

    // 将这些依赖项动态转化为 TOML 语法
    allDeps.forEach { dep ->
      // 解析依赖的 Group 以获取有意义的名称部分
      val groupParts = dep.group.split(".")
      // 剔除无效的顶级域名如 "com", "org", "io" 等
      val sigGroupParts =
          if (groupParts.size > 1 && groupParts[0] in commonTlds) {
            groupParts.drop(1)
          } else {
            groupParts
          }

      // 生成库别名 alias
      // 如果预设了 tomlAlias 则使用预设；如果没有则智能拼接 (如 google-android-material)
      val alias =
          dep.tomlAlias
              ?: buildString {
                append(sigGroupParts.joinToString("-"))
                append("-")
                append(dep.artifact.replace(".", "-"))
              }

      // 跳过已经存在的别名（目标 sdk/库已写入 -> 跳过到下一个）
      if (libraries.containsKey(alias)) return@forEach

      if (dep.version != null) {
        // 生成 VersionRef 引用名称
        // 优先使用依赖自带的 versionRefName（与已有 toml 命名一致）；
        // 否则转为小驼峰命名，如：androidxCore, googleAndroidMaterial, jetbrainsKotlinxCoroutines
        var versionRef =
            dep.versionRefName
                ?: sigGroupParts
                    .mapIndexed { index, s ->
                      if (index == 0) s else s.replaceFirstChar { it.uppercase() }
                    }
                    .joinToString("")

        // 冲突处理 (Collision Resolution)：
        // 如果生成的 versionRef 已经存在，并且记录的版本号与当前依赖版本不同，则附加 artifact 的名称以作区分
        if (versions.containsKey(versionRef) && versions[versionRef] != dep.version) {
          val artifactCamel =
              dep.artifact
                  .split("-", ".")
                  .map { it.replaceFirstChar { c -> c.uppercase() } }
                  .joinToString("")
          versionRef += artifactCamel
        }

        versions[versionRef] = dep.version
        libraries[alias] =
            "{ group = \"${dep.group}\", name = \"${dep.artifact}\", version.ref = \"$versionRef\" }"
      } else {
        // 没有版本号（通常属于 BOM 平台依赖）
        libraries[alias] = "{ group = \"${dep.group}\", name = \"${dep.artifact}\" }"
      }
    }

    val tomlBuilder = StringBuilder()
    tomlBuilder.append("[versions]\n")
    versions.forEach { (k, v) -> tomlBuilder.append("$k = \"$v\"\n") }

    tomlBuilder.append("\n[libraries]\n")
    libraries.forEach { (k, v) -> tomlBuilder.append("$k = $v\n") }

    tomlBuilder.append("\n[plugins]\n")
    plugins.forEach { (k, v) -> tomlBuilder.append("$k = $v\n") }

    val tomlFile = File(data.projectDir, "gradle/libs.versions.toml")
    tomlFile.parentFile?.mkdirs()
    executor.save(tomlBuilder.toString(), tomlFile)
  }

  /**
   * 内部构建工程模板的核心方法。 此处运用匿名内部类拦截原生 recipe 执行机制，将根项目的依赖数据收集生成任务（TOML与根build.gradle）推迟到所有模块任务执行完毕之后执行。
   */
  override fun buildInternal(): ProjectTemplate {
    return object :
        ProjectTemplate(modules, templateName!!, thumb!!, description, widgets!!, recipe!!) {
      override val recipe: TemplateRecipe<ProjectTemplateRecipeResult>
        get() = TemplateRecipe { executor ->
          // 首先按框架既定流程完整执行父类的 recipe（这会先后执行项目自身的配置逻辑和所有注册的子模块的 recipe 构建过程）
          val result = super.recipe.execute(executor)

          // 执行到此阶段时，所有的子模块已经跑完 recipe 并在构建期动态向 builder 完成了全面和完整的依赖配置注入（包含自动分辨是否带有 Compose 支持）
          // 此时方可准确无遗漏地执行全局统一数据的生成以确保 TOML 和 根目录 BuildGradle 集合不出现空白。
          generateToml()
          buildGradle()

          result
        }
    }
  }
}
