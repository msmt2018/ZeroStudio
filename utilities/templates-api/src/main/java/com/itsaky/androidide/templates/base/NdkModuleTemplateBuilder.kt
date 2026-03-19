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

import com.itsaky.androidide.templates.ModuleTemplate
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.SrcSet
import com.itsaky.androidide.templates.base.modules.android.buildGradleSrc
import com.itsaky.androidide.templates.base.util.AndroidManifestBuilder.ConfigurationType.APPLICATION_ATTR
import com.itsaky.androidide.templates.base.util.SourceWriter
import java.io.File

/**
 * 移植优化的 NDK 模块构建器
 * @author android_zero
 */
class NdkModuleTemplateBuilder : AndroidModuleTemplateBuilder() {

    /**
     * 根据是否开启 Cmake，自动决定 native 源码该放置在 cpp 还是 jni 目录下
     */
    fun srcNativeFilePath(srcSet: SrcSet, fileName: String): File {
        val nativeDir = if (data.useCmake) {
            File(srcFolder(srcSet), "cpp")
        } else {
            File(srcFolder(srcSet), "jni")
        }
        if (!nativeDir.exists()) {
            nativeDir.mkdirs()
        }
        return File(nativeDir, fileName)
    }

    /**
     * 快捷写入 cpp 源码至项目指定位置
     */
    inline fun writeCpp(
        writer: SourceWriter,
        fileName: String,
        crossinline cppSrcProvider: () -> String
    ) {
        val src = cppSrcProvider()
        if (src.isNotBlank() && fileName.isNotBlank()) {
            executor.save(src, srcNativeFilePath(SrcSet.Main, fileName))
        }
    }

    /**
     * 快捷写入 CMakeLists 源码至项目指定位置
     */
    inline fun writeCMakeList(
        writer: SourceWriter,
        fileName: String,
        crossinline cmakeSrcProvider: () -> String
    ) {
        val src = cmakeSrcProvider()
        if (src.isNotBlank() && fileName.isNotBlank()) {
            executor.save(src, srcNativeFilePath(SrcSet.Main, fileName))
        }
    }

    /**
     * 覆写 build.gradle 配置，使其直接对接经过更新后的 buildGradleSrc()，自动配置 ndk 模块和参数。
     */
    override fun RecipeExecutor.buildGradle() {
        val gradleContent = buildGradleSrc(isComposeModule, context)
        save(gradleContent, buildGradleFile())
    }
}

/**
 * 带有 NDK 构建能力的默认模块配置函数。
 */
inline fun ProjectTemplateBuilder.defaultAppModuleWithNdk(
    name: String = ":app",
    addAndroidX: Boolean = true,
    copyDefAssets: Boolean = true,
    crossinline block: NdkModuleTemplateBuilder.() -> Unit
) {
    val module = NdkModuleTemplateBuilder().apply {
        projectBuilder = this@defaultAppModuleWithNdk
        _name = name

        templateName = 0
        thumb = 0

        preRecipe = commonPreRecipe { return@commonPreRecipe defModule }
        postRecipe = commonPostRecipe {
            if (copyDefAssets) {
                copyDefaultRes()

                manifest {
                    configure(APPLICATION_ATTR) {
                        androidAttribute("dataExtractionRules", "@xml/data_extraction_rules")
                        androidAttribute("fullBackupContent", "@xml/backup_rules")
                    }
                }
            }
        }

        if (addAndroidX) baseAndroidXDependencies()

        block()
    }.build() as ModuleTemplate

    modules.add(module)
}