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

import android.content.Context
import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.itsaky.androidide.templates.ModuleType.AndroidLibrary
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.SrcSet
import com.itsaky.androidide.templates.base.modules.android.androidGitignoreSrc
import com.itsaky.androidide.templates.base.modules.android.buildGradleSrc
import com.itsaky.androidide.templates.base.modules.android.proguardRules
import com.itsaky.androidide.templates.base.util.AndroidManifestBuilder
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager
import com.itsaky.androidide.templates.base.util.stringRes
import com.squareup.javapoet.TypeSpec
import java.io.File

/**
 * @author Akash Yadav
 * @author android_zero
 */
class AndroidModuleTemplateBuilder : ModuleTemplateBuilder() {

  /**
   * Set whether this Android module is a Jetpack Compose module or not.
   *
   * If this is set to `true`, then compose-specific configurations will be
   * added to the `build.gradle[.kts]` file.
   */
  var isComposeModule = false
    set(value) {
      field = value
      // Automatically enable Compose support in the project builder if available
      if (value && projectBuilder != null) {
        projectBuilder?.enableComposeSupport()
      }
    }

  var context: Context? = null
  var projectBuilder: ProjectTemplateBuilder? = null

  val manifest = AndroidManifestBuilder()
  val res = AndroidModuleResManager()

  /**
   * Return the file path to `AndroidManifest.xml`.
   */
  fun manifestFile(): File {
    return File(srcFolder(SrcSet.Main), ANDROID_MANIFEST_XML).also { it.parentFile!!.mkdirs() }
  }

  /**
   * Configure the properties for `AndroidManifest.xml` file.
   */
  inline fun manifest(crossinline block: AndroidManifestBuilder.() -> Unit) {
    manifest.apply(block)
  }

  /**
   * Get the Android `res` directory for [main][SrcSet.Main] source set in this module.
   *
   * @return The `res` directory for the [main][SrcSet.Main] source set.
   */
  fun mainResDir(): File {
    return resDir(SrcSet.Main)
  }

  /**
   * Get the Android `res` directory for the given [source set][srcSet] in this module.
   *
   * @return The `res` directory.
   */
  fun resDir(srcSet: SrcSet): File {
    return File(srcFolder(srcSet), "res").also { it.mkdirs() }
  }

  /**
   * Configure the resources in the module.
   *
   * @param configure Function to configure the resources.
   */
  inline fun RecipeExecutor.res(crossinline configure: AndroidModuleResManager.() -> Unit) {
    res.apply(configure)
  }

  /**
   * Copy the default resources (without `values` directory) to this module.
   */
  fun RecipeExecutor.copyDefaultRes() {
    copyAssetsRecursively(baseAsset("res"), mainResDir())
  }

  /**
   * Creates a new activity class in the application/library package.
   *
   * @param name The name of the class.
   */
  inline fun RecipeExecutor.createActivity(
      name: String = "MainActivity",
      crossinline configure: TypeSpec.Builder.() -> Unit,
  ) {
    sources { createClass(data.packageName, name, configure) }
  }

  override fun baseAsset(path: String): String {
    return super.baseAsset("android/${path}")
  }

  override fun RecipeExecutor.preConfig() {
    manifest.apply {
      packageName = data.packageName
      isLibrary = data.type == AndroidLibrary
    }

    // Copy the proguard-rules.pro file
    proguardRules()
  }

  override fun RecipeExecutor.postConfig() {

    // Write .gitignore
    gitignore()

    manifest.apply { generate(manifestFile()) }

    res {
      data.appName?.let { putStringRes(manifest.appLabelRes, it) }

      if (strings.isNotEmpty()) {
        createValuesResource("strings") {
          linefeed()
          strings.forEach { (name, value) ->
            stringRes(name, value)
          }
          linefeed()
        }
      }
    }
  }

  override fun RecipeExecutor.buildGradle() {
    // Ensure project builder knows about compose usage
    if (isComposeModule && projectBuilder != null) {
      projectBuilder?.enableComposeSupport()
    }

    save(buildGradleSrc(isComposeModule, context), buildGradleFile())

    // Create marker file if this is a compose module
    if (isComposeModule) {
      val markerFile = File(data.projectDir.parentFile, ".compose_enabled")
      markerFile.createNewFile()
    }
  }

  /**
   * Writes the `.gitignore` file in the mdoule directory.
   */
  fun RecipeExecutor.gitignore() {
    val gitignore = File(data.projectDir, ".gitignore")
    save(androidGitignoreSrc(), gitignore)
  }
}

/**
 * 动态生成 NDK 标准示范模板的 C++ 文件与 CMakeLists.txt
 */
fun AndroidModuleTemplateBuilder.generateNdkFiles() {

    if (!data.useNdk) return

    val cppDir = File(data.projectDir, "src/main/cpp")
    cppDir.mkdirs()

    val cmakeFile = File(cppDir, "CMakeLists.txt")
    val cppFile = File(cppDir, "native-lib.cpp")

    // 获取模块的纯名称（去掉前面的冒号，比如 ":app" -> "app"）
    val moduleCleanName = data.name.substringAfterLast(":").ifEmpty { "app" }

    // 生成 CMakeLists.txt
    val cmakeContent = """
        cmake_minimum_required(VERSION ${data.cmakeVersion})

        # Declare project name
        project("$moduleCleanName")

        # Compiling the native library: Compile native-lib.cpp into a SHARED dynamic link library.
        add_library(
                ${'$'}{CMAKE_PROJECT_NAME}
                SHARED
                native-lib.cpp)

        # Find the Android system's built-in log library for printing Logcat.
        find_library(
                log-lib
                log)

        # Link the target library with the log library.
        target_link_libraries(
                ${'$'}{CMAKE_PROJECT_NAME}
                ${'$'}{log-lib})
    """.trimIndent()

    // 动态计算 JNI 方法签名的包名前缀 (如 com.example.myapp -> com_example_myapp)
    val jniPackageName = data.packageName.replace(".", "_")

    // 生成 C++ 源文件示例
    val cppContent = """
        #include <jni.h>
        #include <string>
        
        // In Kotlin/Java, it can be called using the following code:
        // System.loadLibrary("$moduleCleanName");
        // external fun stringFromJNI(): String
        
        extern "C" JNIEXPORT jstring JNICALL
        Java_${jniPackageName}_MainActivity_stringFromJNI(
                JNIEnv* env,
                jobject /* this */) {
            std::string hello = "Hello from C++ (AndroidIDE)";
            return env->NewStringUTF(hello.c_str());
        }
    """.trimIndent()

    // 写入文件
    executor.save(cmakeContent, cmakeFile)
    executor.save(cppContent, cppFile)
}