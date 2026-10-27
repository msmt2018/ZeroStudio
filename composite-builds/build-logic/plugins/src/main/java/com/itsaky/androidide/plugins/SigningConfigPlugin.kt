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

import com.android.build.api.dsl.ApplicationExtension
import com.itsaky.androidide.build.config.KEY_ALIAS
import com.itsaky.androidide.build.config.KEY_PASS
import com.itsaky.androidide.build.config.KEY_STORE_PASS
import com.itsaky.androidide.build.config.isFDroidBuild
import com.itsaky.androidide.build.config.signingKey
import com.itsaky.androidide.plugins.util.SigningKeyUtils.downloadSigningKey
import com.itsaky.androidide.plugins.util.SigningKeyUtils.getEnvOrProp
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * -------------------------------------------------------------------------------------------------
 * 类名：SigningConfigPlugin
 * 用途与功能：
 *   负责为 Android 应用模块（Application Modules）动态配置正式发布签名（Signing Config）。
 *   兼容 AGP 9.4.1、Kotlin 2.4.10+ 及 Gradle 9 规范。
 * 工作流程线路图：
 *   1. 检查是否为 F-Droid 构建，如果是则跳过签名配置。
 *   2. 调用 downloadSigningKey 准备密钥文件。
 *   3. 通过安全高效的 getEnvOrProp 获取别名、密钥密码及仓库密码。
 *   4. 如果签名配置完整，创建 "common" 签名配置并应用到所有 BuildTypes 中。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 * 历史贡献者：Akash Yadav
 */
class SigningConfigPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.run {
      if (isFDroidBuild) {
        logger.warn("!!! Do not apply ${javaClass.simpleName} when building for F-Droid.")
        return
      }

      downloadSigningKey()

      val signingKey = signingKey.get().asFile
      if (!signingKey.exists()) {
        logger.warn("Signing key not found. Debug signing will be used.")
        return
      }

      val extension = extensions.findByType(ApplicationExtension::class.java) ?: return

      val alias = getEnvOrProp(KEY_ALIAS)
      val storePass = getEnvOrProp(KEY_STORE_PASS)
      val keyPass = getEnvOrProp(KEY_PASS)

      if (!alias.isNullOrBlank() && !storePass.isNullOrBlank() && !keyPass.isNullOrBlank() && signingKey.exists()) {
        val config =
            extension.signingConfigs.create("common") {
              storeFile = signingKey
              keyAlias = alias
              storePassword = storePass
              keyPassword = keyPass
              
              // 显式启用现代安全签名机制 (V1, V2, V3, V4)
              enableV1Signing = true
              enableV2Signing = true
              enableV3Signing = true
              enableV4Signing = true
            }

        extension.buildTypes.forEach { buildType -> 
            buildType.signingConfig = config 
        }
      } else {
        logger.warn(
            "Signing info not configured. keystoreFile=${signingKey}[exists=${signingKey.exists()}]"
        )
      }
    }
  }
}