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

package com.itsaky.androidide.templates.base.modules.android

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.itsaky.androidide.templates.Language.Kotlin
import com.itsaky.androidide.templates.ModuleType
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.ModuleTemplateBuilder
import com.itsaky.androidide.templates.base.modules.dependencies
import com.itsaky.androidide.utils.Environment
import java.io.File

private const val compose_kotlinCompilerExtensionVersion = "1.5.11"
private const val compose_kotlinExtraPlugin = "org.jetbrains.kotlin.plugin.compose"
private const val NATIVE_LIB_NAME = "native-lib"

public var composeExtraPluginKt: String = ""
public var composeExtraPluginGr: String = ""
public var isCompose = false

private val AndroidModuleTemplateBuilder.androidPlugin: String
    get() {
        return if (data.type == ModuleType.AndroidLibrary) "com.android.library"
        else "com.android.application"
    }

private val AndroidModuleTemplateBuilder.androidTomlPlugin: String
    get() {
        return if (data.type == ModuleType.AndroidLibrary) "alias(libs.plugins.android.library)"
        else "alias(libs.plugins.android.application)"
    }

fun AndroidModuleTemplateBuilder.buildGradleSrc(
    isComposeModule: Boolean,
    context: Context? = null,
): String {
    // Generate JNI template files if useNdk is true and it's not a pre-packaged template like ImGui
    if (data.useNdk && !data.useCmake && !File(data.projectDir, "src/main/jni/Android.mk").exists()) {
        // Option to generate simple hello world jni if needed
    }

    return if (data.useKts) buildGradleSrcKts(isComposeModule)
    else buildGradleSrcGroovy(isComposeModule)
}

private fun AndroidModuleTemplateBuilder.generateJniTemplateFiles() {
  val jniDir = File(data.projectDir, "src/main/jni")
  jniDir.mkdirs()

  // Generate Android.mk
  generateAndroidMk(jniDir)

  // Generate Application.mk
  generateApplicationMk(jniDir)

  // Generate native-lib.cpp
  generateNativeCppTemplateCpp(jniDir)

  // Generate native-lib.h
  generateNativeCppTemplateHeader(jniDir)
}

private fun AndroidModuleTemplateBuilder.generateAndroidMk(jniDir: File) {
  val androidMk = File(jniDir, "Android.mk")
  val content =
      """
LOCAL_PATH := ${'$'}(call my-dir)

include ${'$'}(CLEAR_VARS)

LOCAL_MODULE    := $NATIVE_LIB_NAME
LOCAL_SRC_FILES := $NATIVE_LIB_NAME.cpp

include ${'$'}(BUILD_SHARED_LIBRARY)
    """
          .trimIndent()

  executor.save(content, androidMk)
}

private fun AndroidModuleTemplateBuilder.generateApplicationMk(jniDir: File) {
  val applicationMk = File(jniDir, "Application.mk")
  val content =
      """
APP_ABI := all
APP_PLATFORM := android-${data.versions.minSdk.api}
APP_STL := c++_shared
APP_CPPFLAGS += -std=c++17
    """
          .trimIndent()

  executor.save(content, applicationMk)
}

private fun AndroidModuleTemplateBuilder.generateNativeCppTemplateCpp(jniDir: File) {
  val nativecpptemplateCpp = File(jniDir, "$NATIVE_LIB_NAME.cpp")
  val packagePath = data.packageName.replace('.', '_')

  val content =
      """
#include <jni.h>
#include <string>
#include "$NATIVE_LIB_NAME.h"

extern "C" JNIEXPORT jstring JNICALL
Java_${packagePath}_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello world, hello C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_${packagePath}_MainActivity_addNumbers(
        JNIEnv* env,
        jobject /* this */,
        jint a,
        jint b) {
    return a + b;
}

extern "C" JNIEXPORT void JNICALL
Java_${packagePath}_MainActivity_initNativeCppTemplate(
        JNIEnv* env,
        jobject /* this */) {
    // Initialize native-lib native library
    // Add your initialization code here
}
    """
          .trimIndent()

  executor.save(content, nativecpptemplateCpp)
}

private fun AndroidModuleTemplateBuilder.generateNativeCppTemplateHeader(jniDir: File) {
  val nativecpptemplateH = File(jniDir, "$NATIVE_LIB_NAME.h")
  val packagePath = data.packageName.replace('.', '_')
  val content =
      """
#ifndef NATIVECPPTEMPLATE_H
#define NATIVECPPTEMPLATE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Returns a greeting string from native-lib C++
 */
JNIEXPORT jstring JNICALL
Java_${packagePath}_MainActivity_stringFromJNI(JNIEnv* env, jobject thiz);

/**
 * Adds two integers and returns the result
 */
JNIEXPORT jint JNICALL
Java_${packagePath}_MainActivity_addNumbers(JNIEnv* env, jobject thiz, jint a, jint b);

/**
 * Says hello to a person with their name
 */
JNIEXPORT jstring JNICALL
Java_${packagePath}_MainActivity_sayHello(JNIEnv* env, jobject thiz, jstring name);

/**
 * Initialize the native-lib native library
 */
JNIEXPORT void JNICALL
Java_${packagePath}_MainActivity_initNativeCppTemplate(JNIEnv* env, jobject thiz);

#ifdef __cplusplus
}
#endif

#endif // NATIVECPPTEMPLATE_H
    """
          .trimIndent()

  executor.save(content, nativecpptemplateH)
}

private fun AndroidModuleTemplateBuilder.hasNativeFiles(): Boolean {
  val androidMkFile = File(data.projectDir, "src/main/jni/Android.mk")
  val cmakeListsFile = File(data.projectDir, "src/main/jni/CMakeLists.txt")
  return androidMkFile.exists() || cmakeListsFile.exists()
}

private fun AndroidModuleTemplateBuilder.isNdkInstalled(): Boolean {
  val ndkBuildFile = File(Environment.ANDROID_HOME, "ndk/28.2.13676358/ndk-build")
  return ndkBuildFile.exists()
}

private fun AndroidModuleTemplateBuilder.showNdkNotInstalledDialog(context: Context) {
  MaterialAlertDialogBuilder(context)
      .setTitle("NDK Not Found")
      .setMessage(
          "A compatible NDK (version 28.2.13676358) is not installed.\n\n" +
              "Native code features will be disabled for this project.\n\n" +
              "To enable native development, please install NDK version 28.2.13676358 " +
              "open a terminal then run: 'idesetup -y -c -wn'."
      )
      .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
      .setCancelable(false)
      .show()
}

private fun AndroidModuleTemplateBuilder.buildGradleSrcKts(isComposeModule: Boolean): String {
    val compileSdkValue = if (isComposeModule) 36 else data.versions.compileSdk.api
    
    // 1. TOML or Standard Plugins
    val pluginsBlock = if (data.useToml) {
        """
plugins {
    $androidTomlPlugin
    ${if (data.language == Kotlin) "alias(libs.plugins.kotlin.android)" else ""}
    ${if (isComposeModule) "alias(libs.plugins.kotlin.compose)" else ""}
}
        """.trimIndent()
    } else {
        composeExtraPluginKt = if (isComposeModule) "id(\"$compose_kotlinExtraPlugin\")" else ""
        """
plugins {
    id("$androidPlugin")
    ${ktPluginKts()}
}
        """.trimIndent()
    }

    // Native Build Logic (Separated CMake and NDK)
    val nativeBuildBlock = if (data.useCmake) {
        """
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "${data.cmakeVersion}"
        }
    }
        """.trimIndent()
    } else if (data.useNdk) {
        """
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
        """.trimIndent()
    } else ""

    val ndkVersionStr = if (data.useNdk || data.useCmake) """ndkVersion = "${data.ndkVersion}"""" else ""

    val nativeConfigBlock = if (data.useCmake) {
        """
        externalNativeBuild {
            cmake {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86"))
            }
        }
        """.trimIndent()
    } else if (data.useNdk) {
        """
        externalNativeBuild {
            ndkBuild {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86"))
            }
        }
        """.trimIndent()
    } else ""

    return """
$pluginsBlock

android {
    namespace = "${data.packageName}"
    compileSdk = $compileSdkValue
    $ndkVersionStr
    
    defaultConfig {
        applicationId = "${data.packageName}"
        minSdk = ${data.versions.minSdk.api}
        targetSdk = ${data.versions.targetSdk.api}
        versionCode = 1
        versionName = "1.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
         $nativeConfigBlock
    }

    compileOptions {
        sourceCompatibility = ${data.versions.javaSource()}
        targetCompatibility = ${data.versions.javaTarget()}
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

   $nativeBuildBlock

    buildFeatures {
        ${if (!isComposeModule && !data.useNdk && !data.useCmake) "viewBinding = true" else ""}
        ${if (isComposeModule) "compose = true" else ""}
    }
    
}

${dependencies()}
"""
}

private fun AndroidModuleTemplateBuilder.buildGradleSrcGroovy(isComposeModule: Boolean): String {
    val compileSdkValue = if (isComposeModule) 36 else data.versions.compileSdk.api
    
    val tomlPluginGroovy = if (data.type == ModuleType.AndroidLibrary) "alias libs.plugins.android.library" else "alias libs.plugins.android.application"
    
    val pluginsBlock = if (data.useToml) {
        """
plugins {
    $tomlPluginGroovy
    ${if (data.language == Kotlin) "alias libs.plugins.kotlin.android" else ""}
    ${if (isComposeModule) "alias libs.plugins.kotlin.compose" else ""}
}
        """.trimIndent()
    } else {
        composeExtraPluginGr = if (isComposeModule) "id '$compose_kotlinExtraPlugin'" else ""
        """
plugins {
    id '$androidPlugin'
    ${ktPluginGroovy()}
}
        """.trimIndent()
    }

    val nativeBuildBlock = if (data.useCmake) {
        """
    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '${data.cmakeVersion}'
        }
    }
        """.trimIndent()
    } else if (data.useNdk) {
        """
    externalNativeBuild {
        ndkBuild {
            path file('src/main/jni/Android.mk')
        }
    }
        """.trimIndent()
    } else ""

    val ndkVersionStr = if (data.useNdk || data.useCmake) """ndkVersion '${data.ndkVersion}'""" else ""

    val nativeConfigBlock = if (data.useCmake) {
        """
        externalNativeBuild {
            cmake {
                abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86_64', 'x86'
            }
        }
        """.trimIndent()
    } else if (data.useNdk) {
        """
        externalNativeBuild {
            ndkBuild {
                abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86_64', 'x86'
            }
        }
        """.trimIndent()
    } else ""

    return """
$pluginsBlock

android {
    namespace '${data.packageName}'
    compileSdk $compileSdkValue
    $ndkVersionStr
    
    defaultConfig {
        applicationId "${data.packageName}"
        minSdk ${data.versions.minSdk.api}
        targetSdk ${data.versions.targetSdk.api}
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'

        $nativeConfigBlock
    }

    compileOptions {
        sourceCompatibility ${data.versions.javaSource()}
        targetCompatibility ${data.versions.javaTarget()}
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    $nativeBuildBlock

    buildFeatures {
        ${if (!isComposeModule && !data.useNdk && !data.useCmake) "viewBinding true" else ""}
        ${if (isComposeModule) "compose true" else ""}
    }

}

${dependencies()}
"""
}

//    ${if(isComposeModule) composeConfigGroovy() else ""}
fun composeConfigGroovy(): String =
    """
    composeOptions {
        kotlinCompilerExtensionVersion '$compose_kotlinCompilerExtensionVersion'
    }
    packaging {
        resources {
            excludes += '/META-INF/{AL2.0,LGPL2.1}'
        }
    }
""".trim()

//    ${if(isComposeModule) composeConfigKts() else ""}
fun composeConfigKts(): String =
    """
    composeOptions {
        kotlinCompilerExtensionVersion = "$compose_kotlinCompilerExtensionVersion"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
""".trim()

private fun ModuleTemplateBuilder.ktJvmTarget(): String {
    if (data.language != Kotlin) return ""
    return if (data.useKts) ktJvmTargetKts() else ktJvmTargetGroovy()
}

//${ktJvmTarget()}
private fun ModuleTemplateBuilder.ktJvmTargetKts(): String = """
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("${data.versions.javaTarget}"))
    }
}
"""
//${ktJvmTarget()}
private fun ModuleTemplateBuilder.ktJvmTargetGroovy(): String = """
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).all {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("${data.versions.javaTarget}")
    }
}
"""

private fun ktPluginKts(): String = """
  id("kotlin-android")
  $composeExtraPluginKt
  """

private fun ktPluginGroovy(): String = """
  id 'kotlin-android'
  $composeExtraPluginGr
  """