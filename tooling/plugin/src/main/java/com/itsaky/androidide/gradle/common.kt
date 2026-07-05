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

package com.itsaky.androidide.gradle

import com.itsaky.androidide.buildinfo.BuildInfo
import com.itsaky.androidide.tooling.api.LogSenderConfig._PROPERTY_IS_TEST_ENV
import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler

/** @author Akash Yadav */
const val APP_PLUGIN = "com.android.application"
const val LIBRARY_PLUGIN = "com.android.library"

const val LIB_GROUP_LOGGING = "logging"
const val LIB_GROUP_TOOLING = "tooling"

internal val Project.isTestEnv: Boolean
  get() =
      hasProperty(_PROPERTY_IS_TEST_ENV) && property(_PROPERTY_IS_TEST_ENV).toString().toBoolean()

internal fun depVersion(testEnv: Boolean): String {
  return if (testEnv && !System.getenv("CI").toBoolean()) {
    BuildInfo.VERSION_NAME_SIMPLE
  } else {
    BuildInfo.VERSION_NAME_DOWNLOAD
  }
}

fun Project.ideDependency(group: String, artifact: String): Dependency {
  return dependencies.ideDependency(group, artifact, isTestEnv)
}

fun DependencyHandler.ideDependency(group: String, artifact: String, testEnv: Boolean): Dependency {
  localIdeArtifact(artifact)?.let { return create(it) }
  return create("io.github.mohammed-baqer-null:${artifact}:${depVersion(testEnv)}")
}

private fun localIdeArtifact(artifact: String): File? {
  val localName =
      when (artifact) {
        "plugin" -> "androidide-plugin.jar"
        "plugin-config" -> "plugin-config.jar"
        "logger" -> "logger.jar"
        "logsender" -> "logsender.aar"
        "ide-log-plugin" -> "ide-log-plugin-1.0.0.aar"
        "ide-debugger" -> "ide-debugger.aar"
        // 子项目 9d: host ADRT AAR (HostAttachAgent / HostSocksServer / etc.)
        // 之前漏写这个 case, IDE 端没有 Maven 远端环境时
        //   IdeDebuggerInitScriptPlugin 注入会跑 create(io.github.mohammed-baqer-null:ide-debugger-host:VERSION)
        //   然后 Maven 远端解析失败, host app build fail.
        // Phase 12o 修: 加 case, 走本地 aar (跟 ide-log-plugin 一致).
        "ide-debugger-host" -> "ide-debugger-host-1.0.0.aar"
        else -> return null
      }
  return listOf(
          File("/data/data/com.itsaky.androidide/files/home/.androidide/plugin/logger", localName),
          File("/data/user/0/com.itsaky.androidide/files/home/.androidide/plugin/logger", localName),
          File("init", localName),
      )
      .firstOrNull { it.isFile }
}
