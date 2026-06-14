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

import com.itsaky.androidide.build.config.BuildConfig
import java.util.zip.ZipFile

plugins {
  id("com.android.library")
  id("kotlin-android")
  alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

val composeVersion = "1.6.0"
val material3Version = "1.2.0"
val composeCompilerVersion = "1.5.10"

val composeCompilerJars: Configuration by configurations.creating {
    isTransitive = false
}

val composeAarsForPreview: Configuration by configurations.creating {
    isTransitive = false
}

// 进程内 K2JVMCompiler 需要的全部 jar (Kotlin 编译器 + 内嵌依赖)
val kotlinCompilerJars: Configuration by configurations.creating {
    isTransitive = true
}

// D8 (来自 R8 制品, R8 内置 D8 入口类 com.android.tools.r8.D8)
val bundledD8Jars: Configuration by configurations.creating {
    isTransitive = true
}

dependencies {
    composeCompilerJars("androidx.compose.compiler:compiler:$composeCompilerVersion")

    composeAarsForPreview("androidx.compose.runtime:runtime-android:$composeVersion")
    composeAarsForPreview("androidx.compose.ui:ui-android:$composeVersion")
    composeAarsForPreview("androidx.compose.ui:ui-graphics-android:$composeVersion")
    composeAarsForPreview("androidx.compose.ui:ui-text-android:$composeVersion")
    composeAarsForPreview("androidx.compose.ui:ui-unit-android:$composeVersion")
    composeAarsForPreview("androidx.compose.ui:ui-geometry-android:$composeVersion")
    composeAarsForPreview("androidx.compose.animation:animation-android:$composeVersion")
    composeAarsForPreview("androidx.compose.animation:animation-core-android:$composeVersion")
    composeAarsForPreview("androidx.compose.foundation:foundation-android:$composeVersion")
    composeAarsForPreview("androidx.compose.foundation:foundation-layout-android:$composeVersion")
    composeAarsForPreview("androidx.compose.material3:material3-android:$material3Version")
    composeAarsForPreview("androidx.compose.ui:ui-tooling-preview-android:$composeVersion")
    composeAarsForPreview("androidx.activity:activity-compose:1.8.2")
    composeAarsForPreview("androidx.activity:activity-ktx:1.8.2")
    composeAarsForPreview("androidx.activity:activity:1.8.2")
    composeAarsForPreview("androidx.lifecycle:lifecycle-runtime:2.6.1")
    composeAarsForPreview("androidx.lifecycle:lifecycle-common:2.6.1")
    composeAarsForPreview("androidx.lifecycle:lifecycle-viewmodel:2.6.1")
    composeAarsForPreview("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1")
    composeAarsForPreview("androidx.savedstate:savedstate:1.2.1")
    composeAarsForPreview("androidx.core:core:1.12.0")
    composeAarsForPreview("androidx.core:core-ktx:1.12.0")
    composeAarsForPreview("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
    composeAarsForPreview("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // === Build phase 自包含所需 ===
    // Kotlin embeddable 编译器 (含 stdlib / reflect / script-runtime 等所有依赖)
    // 版本需与 compose-compiler 1.5.10 兼容, 即 Kotlin 1.9.22
    kotlinCompilerJars("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.22")
    // R8 制品内置 D8 入口 com.android.tools.r8.D8
    bundledD8Jars("com.android.tools:r8:8.5.35")
}

val copyComposeCompilerPlugin by tasks.registering(Copy::class) {
    from(composeCompilerJars)
    into(layout.buildDirectory.dir("compose-jars"))

    rename { originalName ->
        when {
            originalName.startsWith("compiler-") -> "compose-compiler-plugin.jar"
            else -> originalName
        }
    }
}

val extractComposeClasses by tasks.registering {
    dependsOn(copyComposeCompilerPlugin)

    val outputDir = layout.buildDirectory.dir("compose-jars")

    doLast {
        val outDir = outputDir.get().asFile
        outDir.mkdirs()

        composeAarsForPreview.files.forEach { file ->
            when {
                file.name.endsWith(".aar") -> {
                    ZipFile(file).use { aar ->
                        val classesEntry = aar.getEntry("classes.jar")
                        if (classesEntry != null) {
                            val targetName = file.nameWithoutExtension + ".jar"
                            val targetFile = File(outDir, targetName)
                            aar.getInputStream(classesEntry).use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            println("Extracted classes.jar from ${file.name} -> $targetName")
                        }
                    }
                }
                file.name.endsWith(".jar") -> {
                    val targetFile = File(outDir, file.name)
                    file.copyTo(targetFile, overwrite = true)
                    println("Copied JAR: ${file.name}")
                }
            }
        }
    }
}

// === 进程内 K2JVMCompiler 依赖: 把 kotlin-compiler-embeddable 等所有 jar 拷到 build/compose-jars/kotlin/ ===
val copyKotlinCompilerJars by tasks.registering(Copy::class) {
    from(kotlinCompilerJars)
    into(layout.buildDirectory.dir("compose-jars/kotlin"))
}

// === 进程内 D8 依赖: 把 r8 (内置 D8 入口) 拷到 build/compose-jars/dex/ ===
val copyBundledD8Jars by tasks.registering(Copy::class) {
    from(bundledD8Jars)
    into(layout.buildDirectory.dir("compose-jars/dex"))
}

fun resolveD8Jar(): File {
    val buildToolsDir = File(android.sdkDirectory, "build-tools")
    return buildToolsDir.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedByDescending { it.name }
        ?.firstNotNullOfOrNull { File(it, "lib/d8.jar").takeIf { jar -> jar.exists() } }
        ?: throw GradleException("D8 jar not found in $buildToolsDir")
}

fun resolveAndroidJar(): File {
    val platformsDir = File(android.sdkDirectory, "platforms")
    return platformsDir.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedByDescending { it.name }
        ?.firstNotNullOfOrNull { File(it, "android.jar").takeIf { jar -> jar.exists() } }
        ?: throw GradleException("android.jar not found in $platformsDir")
}

val compileRuntimeDex by tasks.registering {
    dependsOn(extractComposeClasses)

    val jarsDir = layout.buildDirectory.dir("compose-jars")
    val dexOutputDir = layout.buildDirectory.dir("compose-jars/dex")

    doLast {
        val outDir = dexOutputDir.get().asFile.apply { mkdirs() }
        val runtimeJars = jarsDir.get().asFile.listFiles { file: File ->
            file.extension == "jar" && file.name != "compose-compiler-plugin.jar"
        }?.toList() ?: throw GradleException("No runtime JARs found to compile to DEX")

        project.javaexec {
            classpath = files(resolveD8Jar())
            mainClass.set("com.android.tools.r8.D8")
            maxHeapSize = "1g"
            args = buildList {
                add("--release")
                add("--min-api"); add("21")
                add("--lib"); add(resolveAndroidJar().absolutePath)
                add("--output"); add(outDir.absolutePath)
                runtimeJars.forEach { add(it.absolutePath) }
            }
        }

        File(outDir, "classes.dex").let {
            if (it.exists()) it.renameTo(File(outDir, "compose-runtime.dex"))
        }
    }
}

val packageComposeJars by tasks.registering(Zip::class) {
    dependsOn(compileRuntimeDex, copyKotlinCompilerJars, copyBundledD8Jars)

    from(layout.buildDirectory.dir("compose-jars"))
    archiveFileName.set("compose-jars.zip")
    destinationDirectory.set(file("src/main/assets/compose"))

    doFirst {
        file("src/main/assets/compose").mkdirs()
    }
}

tasks.named("preBuild") {
    dependsOn(packageComposeJars)
}

android {
  namespace = "${BuildConfig.packageName}.compose.preview"

  buildFeatures {
    compose = true
    viewBinding = true
  }

  defaultConfig { consumerProguardFiles("proguard-rules.pro") }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))

  // Compose 基础依赖
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.activity.compose)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // AndroidX & 基础 UI
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.google.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.common.kotlin.coroutines.android)

  // AndroidIDE模块
  implementation(projects.core.common)
  implementation(projects.editor.impl)
  implementation(projects.editor.api)
  implementation(projects.core.resources)
  implementation(projects.logging.logger)
  implementation(projects.core.projects)

  // 进程内 K2JVMCompiler (仅 build 阶段使用; 体积大, 用 compileOnly 避免打包进 APK)
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.22")
  // 反射读取 LayoutNode 等需要 kotlin-reflect
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
}
