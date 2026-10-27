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

package com.itsaky.androidide.plugins

import com.itsaky.androidide.build.config.isFDroidBuild
import com.itsaky.androidide.plugins.util.isAndroidModule
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * -------------------------------------------------------------------------------------------------
 * 类名：AndroidIDEPlugin
 * 用途与功能：
 *   管理应用在 IDE 各个子模块上所施加的核心插件逻辑。
 *   适配 AGP 9.4.1、Kotlin 2.4.10+ 及 Gradle 9 构建规范。
 * 工作流程线路图：
 *   1. apply() -> 拦截根项目应用，确保只作用于子模块。
 *   2. 检查 buildFile 是否存在。
 *   3. 如果是 Android 模块且非 F-Droid 构建，自动应用 `SigningConfigPlugin` 签名插件。
 *   4. 如果是 F-Droid 构建且包含 core-app 插件，通过安全反射打印应用基础配置（AppId, VersionName, VersionCode）。
 *   5. 根据模块类型动态注册 `runTestsInCI` 统一测试任务。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 * 历史贡献者：Akash Yadav
 */
class AndroidIDEPlugin : Plugin<Project> {

  override fun apply(target: Project) = target.run {
    if (project.path == rootProject.path) {
      throw GradleException("Cannot apply ${AndroidIDEPlugin::class.simpleName} to root project")
    }

    if (!project.buildFile.exists() || !project.buildFile.isFile) {
      return@run
    }

    if (isAndroidModule && !isFDroidBuild) {
      // 设置签名配置插件
      plugins.apply(SigningConfigPlugin::class.java)
    }

    if (isFDroidBuild && project.plugins.hasPlugin("com.itsaky.androidide.core-app")) {
      logger.warn("Building for F-Droid with configuration:")
      
      val androidExt = extensions.findByName("android")
      if (androidExt != null) {
          try {
              val defaultConfig = androidExt.javaClass.getMethod("getDefaultConfig").invoke(androidExt)
              val appId = defaultConfig?.javaClass?.getMethod("getApplicationId")?.invoke(defaultConfig)
              val vName = defaultConfig?.javaClass?.getMethod("getVersionName")?.invoke(defaultConfig)
              val vCode = defaultConfig?.javaClass?.getMethod("getVersionCode")?.invoke(defaultConfig)
              
              logger.warn("applicationId = ${appId ?: ""}")
              logger.warn("versionName = ${vName ?: ""}")
              logger.warn("versionCode = ${vCode ?: ""}")
          } catch (e: Exception) {
              logger.warn("Could not read android configuration dynamically: ${e.message}")
          }
      }
      logger.warn("--- x --- x ---")
    }

    val taskName =
        when {
          isAndroidModule -> "testDebugUnitTest"
          else -> "test"
        }

    logger.info("${project.path} will run task '$taskName' for tests in CI")

    project.tasks.register("runTestsInCI") { dependsOn(taskName) }
  }
}