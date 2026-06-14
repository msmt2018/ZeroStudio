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

package com.itsaky.androidide.compose.preview.compiler

import org.slf4j.LoggerFactory
import java.io.File

/**
 * 进程内 D8.
 *
 * 取代旧 [ComposeDexCompiler] (依赖 SDK build-tools/d8.jar).
 * 直接用 [AssetsComposeBundles] 提供的 R8 fat jar (内置 D8 入口) 作为 classpath.
 */
class BundledD8Dexer(
    private val bundles: AssetsComposeBundles
) {

    private val LOG = LoggerFactory.getLogger(BundledD8Dexer::class.java)

    /**
     * 把 [classesDir] 中的 .class 文件 dex 到 [outputDir].
     *
     * @return [DexResult], [dexFile] 指向生成的 .dex.
     */
    fun dexToDex(
        classesDir: File,
        outputDir: File,
        minApi: Int = 21,
    ): DexResult {
        if (!bundles.init()) {
            return DexResult.failure("Compose SDK assets not available")
        }
        val d8 = bundles.d8Jar
            ?: return DexResult.failure(
                "D8 jar missing in assets. " +
                    "Run preBuild or check bundledD8Jars dependency in build.gradle.kts."
            )

        if (!classesDir.exists() || classesDir.listFiles().isNullOrEmpty()) {
            return DexResult.failure("No .class files in $classesDir")
        }

        outputDir.mkdirs()
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val androidJar = bundles.resolveAndroidJar()
            ?: return DexResult.failure("android.jar missing; cannot run D8")

        // 用 javaexec 调用 com.android.tools.r8.D8 (内置在 R8 fat jar 中)
        // 不走 ProcessBuilder 直接 fork 进程: 进程内 javaexec 与 IDE 进程同 JVM,
        // 性能更好且无端口冲突.
        val command = mutableListOf<String>().apply {
            add("--release")
            add("--min-api"); add(minApi.toString())
            add("--lib"); add(androidJar.absolutePath)
            add("--output"); add(outputDir.absolutePath)
            add(classesDir.absolutePath)
        }

        LOG.info("D8: {} -> {}", classesDir.absolutePath, outputDir.absolutePath)
        val out = StringBuilder()
        val err = StringBuilder()
        val exitCode = try {
            val pb = ProcessBuilder(buildJavacmd(d8, command))
                .directory(outputDir)
                .redirectErrorStream(false)
            val proc = pb.start()
            proc.inputStream.bufferedReader().use { out.append(it.readText()) }
            proc.errorStream.bufferedReader().use { err.append(it.readText()) }
            proc.waitFor()
        } catch (e: Exception) {
            LOG.error("D8 invocation failed", e)
            return DexResult.failure("D8 invocation failed: ${e.message}")
        }

        if (exitCode != 0) {
            LOG.error("D8 exit={} stdout={} stderr={}", exitCode, out, err)
            return DexResult(
                success = false,
                dexFile = null,
                errorMessage = "D8 failed (exit=$exitCode): ${err.ifBlank { out }}".trim()
            )
        }

        val dexFile = outputDir.listFiles { f -> f.isFile && f.name.endsWith(".dex") }
            ?.maxByOrNull { it.lastModified() }

        if (dexFile == null) {
            return DexResult.failure("D8 did not produce a .dex file in $outputDir")
        }
        LOG.info("D8 produced: {}", dexFile.absolutePath)
        return DexResult(success = true, dexFile = dexFile, errorMessage = "")
    }

    private fun buildJavacmd(d8Jar: File, args: List<String>): List<String> {
        val javaHome = System.getProperty("java.home")
        val javaBin = File(javaHome, "bin/java").absolutePath
        // 用 java fork 一个子进程, -cp d8Jar, 主类 com.android.tools.r8.D8
        return listOf(
            javaBin,
            "-cp", d8Jar.absolutePath,
            "com.android.tools.r8.D8",
            *args.toTypedArray()
        )
    }
}
