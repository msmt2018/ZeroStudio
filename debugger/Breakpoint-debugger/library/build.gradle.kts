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

import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip

/**
 * -------------------------------------------------------------------------------------------------
 * 文件名：build.gradle.kts (Bundling Archive)
 * 用途与功能：
 *   将 ZeroStudio 调试器/日志记录器运行时产物（JAR 与 AAR）打包为单个可分发的归档文件 (`debugger-library.zip`)。
 *   适配 Gradle 9 规范，移除了已被废弃的 `tasks.registering` 属性委托语法，采用现代的 `tasks.register<Zip>(...)`。
 * 工作流程线路图：
 *   1. 定义一系列运行时产物数据结构 [RuntimeArtifact]。
 *   2. 显式注册 `packageDebuggerLibrary` Zip 任务。
 *   3. 动态遍历各个产物模块，配置 `dependsOn` 依赖及其构建输出拷贝重命名规则。
 *   4. 将 `assemble` 任务与该归档打包任务进行挂钩。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 */

plugins { `java-library` }

description = "Packages ZeroStudio debugger/logger runtime artifacts into a single distributable archive."

val debuggerLibraryArchiveName = "debugger-library.zip"

data class RuntimeArtifact(
    val projectPath: String,
    val producerTaskName: String,
    val buildOutput: String,
    val packagedName: String,
)

val runtimeArtifacts = listOf(
    RuntimeArtifact(
        ":tooling:plugin",
        "jar",
        "libs/androidide-plugin.jar",
        "androidide-plugin.jar",
    ),
    RuntimeArtifact(
        ":debugger:Breakpoint-debugger:ide-debugger-host",
        "assembleRelease",
        "outputs/aar/ide-debugger-host-release.aar",
        "ide-debugger-host.aar",
    ),
    RuntimeArtifact(
        ":debugger:Breakpoint-debugger:ide-log-plugin",
        "assembleRelease",
        "outputs/aar/ide-log-plugin-release.aar",
        "ide-log-plugin-1.0.0.aar",
    ),
    RuntimeArtifact(
        ":logging:logsender",
        "assembleRelease",
        "outputs/aar/logsender-release.aar",
        "logsender.aar",
    ),
    RuntimeArtifact(
        ":tooling:plugin-config",
        "jar",
        "libs/plugin-config.jar",
        "plugin-config.jar",
    ),
)

/**
 * -------------------------------------------------------------------------------------------------
 * 任务名：packageDebuggerLibrary
 * 用途与功能：
 *   采用 Gradle 9 推荐的显式 `tasks.register<Zip>` API 替代原先被废弃的 `by tasks.registering(Zip::class)` 委托语法。
 *   负责收集所有依赖产物并打包压缩。
 * -------------------------------------------------------------------------------------------------
 */
val packageDebuggerLibrary = tasks.register<Zip>("packageDebuggerLibrary") {
  group = "debugger"
  description = "Bundles debugger/logger runtime jars and AARs into $debuggerLibraryArchiveName."

  archiveFileName.set(debuggerLibraryArchiveName)
  destinationDirectory.set(layout.buildDirectory.dir("distributions"))
  duplicatesStrategy = DuplicatesStrategy.FAIL

  runtimeArtifacts.forEach { artifact ->
    val artifactProject = project(artifact.projectPath)
    dependsOn(artifactProject.tasks.named(artifact.producerTaskName))
    from(artifactProject.layout.buildDirectory.file(artifact.buildOutput)) {
      rename { artifact.packagedName }
    }
  }
}

tasks.named("assemble") { dependsOn(packageDebuggerLibrary) }