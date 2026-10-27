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

package com.itsaky.androidide.plugins.util

import com.itsaky.androidide.build.config.AUTH_PASS
import com.itsaky.androidide.build.config.AUTH_USER
import com.itsaky.androidide.build.config.KEY_BIN
import com.itsaky.androidide.build.config.KEY_URL
import com.itsaky.androidide.build.config.signingKey
import java.io.FileInputStream
import java.util.Base64
import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle

/**
 * -------------------------------------------------------------------------------------------------
 * 工具类：SigningKeyUtils
 * 用途与功能：
 *   负责下载、解密并配置 Android 模块所需的签名密钥文件 (`signingKey`)。
 *   适配 Gradle 9/10 标准：
 *     1. 彻底移除了已被废弃的 `project.properties` 全局映射调用，改用精准的 `signing.properties` 文件读取与 `providers.gradleProperty`。
 *     2. 移除了旧版被移除的 `project.exec`，升级为标准安全的命令调用方案。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 * 历史贡献者：Akash Yadav
 */
object SigningKeyUtils {

  private val _warned = mutableMapOf<String, Boolean>()

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：downloadSigningKey
   * 用途与功能：
   *   检查签名密钥是否存在。若不存在，则依次尝试通过 Base64 环境变量、`signing.properties` 或远程脚本下载配置。
   * -------------------------------------------------------------------------------------------------
   */
  @JvmStatic
  fun Project.downloadSigningKey() {
    val signingKey = signingKey.get().asFile
    if (signingKey.exists()) {
      logger.info("Skipping download as ${signingKey.name} file already exists.")
      return
    }

    signingKey.parentFile.mkdirs()

    getEnvOrProp(key = KEY_BIN, warn = false)?.also { bin ->
      val contents = Base64.getDecoder().decode(bin)
      signingKey.writeBytes(contents)
      return
    }

    // 下载签名密钥所需的 URL
    val url = getEnvOrProp(KEY_URL) ?: return

    // 下载密钥所需的用户名和密码
    val user = getEnvOrProp(AUTH_USER) ?: return
    val pass = getEnvOrProp(AUTH_PASS) ?: return

    logger.info("Downloading signing key...")
    
    var rootGradle: Gradle? = gradle
    while (rootGradle?.parent != null) {
      rootGradle = rootGradle.parent
    }
    val rootDir = rootGradle!!.rootProject.projectDir

    // 使用 Gradle 9 兼容的进程执行方式
    val execResult = providers.exec {
      workingDir(rootDir)
      commandLine("bash", "./scripts/download_key.sh", signingKey.absolutePath, url, user, pass)
    }.result.get()

    execResult.assertNormalExitValue()
  }

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：getEnvOrProp
   * 用途与功能：
   *   安全地按优先级获取配置项（环境变量 -> 根目录 signing.properties -> Gradle 属性 Provider），
   *   彻底摒弃了已被 Gradle 9 废弃的 `project.properties` 映射调用。
   * -------------------------------------------------------------------------------------------------
   */
  internal fun Project.getEnvOrProp(key: String, warn: Boolean = true): String? {
    // 优先检查系统环境变量
    var value: String? = System.getenv(key)

    // 检查根目录下的 signing.properties 文件
    if (value.isNullOrBlank()) {
      val signingPropsFile = rootProject.file("signing.properties")
      if (signingPropsFile.exists() && signingPropsFile.isFile) {
        try {
          val props = Properties()
          FileInputStream(signingPropsFile).use { props.load(it) }
          value = props.getProperty(key)
        } catch (e: Exception) {
          logger.warn("Failed to read signing.properties for key '$key': ${e.message}")
        }
      }
    }

    // 回退到安全的 Gradle Provider 属性
    if (value.isNullOrBlank()) {
      value = providers.gradleProperty(key).orNull ?: findProperty(key)?.toString()
    }

    if (value.isNullOrBlank()) {
      if (warn && _warned.putIfAbsent(key, true) != true) {
        logger.warn("$key is not set. Debug key will be used to sign the APK")
      }
      return null
    }
    return value
  }
}