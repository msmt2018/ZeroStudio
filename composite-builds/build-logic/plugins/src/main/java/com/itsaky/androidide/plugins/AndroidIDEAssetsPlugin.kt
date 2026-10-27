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

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.build.config.downloadVersion
import com.itsaky.androidide.plugins.tasks.AddAndroidJarToAssetsTask
import com.itsaky.androidide.plugins.tasks.AddFileToAssetsTask
import com.itsaky.androidide.plugins.tasks.GenerateInitScriptTask
import com.itsaky.androidide.plugins.tasks.GradleWrapperGeneratorTask
import com.itsaky.androidide.plugins.tasks.SetupAapt2Task
import com.itsaky.androidide.plugins.util.SdkUtils.getAndroidJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip

/**
 * -------------------------------------------------------------------------------------------------
 * 类名：AndroidIDEAssetsPlugin
 * 用途与功能：
 *   负责管理 AndroidIDE 应用构建过程中的资产（Assets）复制、工具链打包与生成逻辑。
 *   适配 AGP 9.4.1、Kotlin 2.4.10+ 及 Gradle 9 构建规范。
 * 工作流程线路图：
 *   1. 注册基础生成任务：`generateGradleWrapper`、`setupAapt2`、`addAndroidJarToAssets`。
 *   2. 遍历 ApplicationAndroidComponentsExtension 的各个变体（Variants）：
 *      - 挂载 JniLibs 源码集（AAPT2）。
 *      - 挂载 Assets 源码集（Gradle Wrapper、Android Jar）。
 *      - 动态注册并挂载 `generate<Variant>InitScript` 初始化脚本生成任务。
 *      - 动态注册并挂载 `copy<Variant>DebuggerLibraryToAssets` 调试器运行时打包分发任务。
 *      - 动态注册并挂载 `copy<Variant>ToolingApiJar` Tooling API JAR 复制任务。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 * 历史贡献者：Akash Yadav
 */
class AndroidIDEAssetsPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.run {
      val wrapperGeneratorTaskProvider =
          tasks.register("generateGradleWrapper", GradleWrapperGeneratorTask::class.java)

      val androidComponentsExtension =
          extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

      val setupAapt2TaskTaskProvider = tasks.register("setupAapt2", SetupAapt2Task::class.java)

      val addAndroidJarTaskProvider =
          tasks.register("addAndroidJarToAssets", AddAndroidJarToAssetsTask::class.java) {
            androidJar = androidComponentsExtension.getAndroidJar(assertExists = true)
          }

      androidComponentsExtension.onVariants { variant ->
        // 使用现代化安全首字母大写转换，替代已废弃的 capitalized()
        val variantNameCapitalized = variant.name.replaceFirstChar { 
          if (it.isLowerCase()) it.titlecase() else it.toString() 
        }

        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            setupAapt2TaskTaskProvider,
            SetupAapt2Task::outputDirectory,
        )

        variant.sources.assets?.addGeneratedSourceDirectory(
            wrapperGeneratorTaskProvider,
            GradleWrapperGeneratorTask::outputDirectory,
        )

        variant.sources.assets?.addGeneratedSourceDirectory(
            addAndroidJarTaskProvider,
            AddAndroidJarToAssetsTask::outputDirectory,
        )

        // 初始化脚本生成器任务
        val generateInitScript =
            tasks.register(
                "generate${variantNameCapitalized}InitScript",
                GenerateInitScriptTask::class.java,
            ) {
              mavenGroupId.set(BuildConfig.packageName)
              downloadVersion.set(this@run.downloadVersion)
            }

        variant.sources.assets?.addGeneratedSourceDirectory(
            generateInitScript,
            GenerateInitScriptTask::outputDir,
        )

        // 调试器库打包复制任务
        val copyDebuggerLibrary =
            tasks.register(
                "copy${variantNameCapitalized}DebuggerLibraryToAssets",
                AddFileToAssetsTask::class.java,
            ) {
              val debuggerLibraryPath = ":debugger:Breakpoint-debugger:library"
              val debuggerLibrary =
                  checkNotNull(rootProject.findProject(debuggerLibraryPath)) {
                    "Cannot find required debugger runtime library module: '$debuggerLibraryPath'"
                  }
              val packageTask =
                  debuggerLibrary.tasks.named("packageDebuggerLibrary", Zip::class.java)
              dependsOn(packageTask)
              inputFile.set(packageTask.flatMap { it.archiveFile })
              fileName.set("debugger-library.zip")
              baseAssetsPath.set("data/common")
            }

        variant.sources.assets?.addGeneratedSourceDirectory(
            copyDebuggerLibrary,
            AddFileToAssetsTask::outputDirectory,
        )

        // Tooling API JAR 复制器任务
        val copyToolingApiJar =
            tasks.register(
                "copy${variantNameCapitalized}ToolingApiJar",
                AddFileToAssetsTask::class.java,
            ) {
              val implPath = ":tooling:impl"
              val toolingApi =
                  checkNotNull(rootProject.findProject(implPath)) {
                    "Cannot find the Tooling Impl module with project path: '$implPath'"
                  }
              dependsOn(toolingApi.tasks.getByName("copyJar"))

              val toolingApiJar = toolingApi.layout.buildDirectory.file("libs/tooling-api-all.jar")

              inputFile.set(toolingApiJar)
              baseAssetsPath.set("data/common")
            }

        variant.sources.assets?.addGeneratedSourceDirectory(
            copyToolingApiJar,
            AddFileToAssetsTask::outputDirectory,
        )
      }
    }
  }
}