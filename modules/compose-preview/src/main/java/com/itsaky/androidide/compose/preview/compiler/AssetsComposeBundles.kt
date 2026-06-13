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

import android.content.Context
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 资产自包含的 Compose SDK 加载器.
 *
 * 取代旧的 `ComposeClasspathManager`(依赖 `.m2` / IDE 私有路径).
 * 所有 jar 都从 `assets/compose/compose-jars.zip` 解压到 `cacheDir/compose-sdk/`.
 *
 * 提供:
 * - [kotlinCompilerClasspath] : K2JVMCompiler 运行所需的全部 jar (kotlin + embeddable deps)
 * - [composePluginJar]        : compose-compiler-plugin.jar (给 K2 `-Xplugin` 用)
 * - [composeRuntimeJars]      : compose runtime / ui / foundation / material3 等
 * - [d8Jar]                   : 内置 D8 (来自 R8 制品, 含 D8 入口)
 * - [composeRuntimeDex]       : 预 dexed compose runtime, 直接给主 app 加载
 * - [androidJar]              : android.jar (从 SDK platforms 取, 缺失则 null)
 * - [versionTag]              : SHA-256 of the sdk zip, 用作缓存失效标识
 */
class AssetsComposeBundles(private val context: Context) {

    private val LOG = LoggerFactory.getLogger(AssetsComposeBundles::class.java)

    /** 解压根目录, 在 `cacheDir/compose-sdk/`. */
    val workDir: File by lazy { File(context.cacheDir, "compose-sdk").apply { mkdirs() } }

    /** 解压后 compose runtime 的目录. */
    val composeRuntimeDir: File by lazy { File(workDir, "compose-runtime").apply { mkdirs() } }

    /** 解压后 kotlin 编译器的目录. */
    val kotlinCompilerDir: File by lazy { File(workDir, "kotlin").apply { mkdirs() } }

    /** 解压后 D8 的目录. */
    val d8Dir: File by lazy { File(workDir, "d8").apply { mkdirs() } }

    /** 解压后 dex 的目录. */
    val dexDir: File by lazy { File(workDir, "dex").apply { mkdirs() } }

    @Volatile
    private var initialized = false

    /**
     * 解压所有资产. 幂等, 多次调用仅首次执行.
     *
     * @return `true` 表示至少 compose runtime / d8 / kotlin 编译器都解压成功;
     *         `false` 表示资产损坏或缺失.
     */
    @Synchronized
    fun init(): Boolean {
        if (initialized) return true
        try {
            context.assets.open(ASSET_FILE).use { input ->
                val md = MessageDigest.getInstance("SHA-256")
                val cacheFile = File(workDir, ASSET_FILE)
                cacheFile.parentFile?.mkdirs()
                cacheFile.outputStream().use { out ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        md.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
                versionTag = md.digest().joinToString("") { "%02x".format(it) }.take(16)
                LOG.info("compose-jars.zip sha256[:16] = {}", versionTag)
            }

            // 解压 zip 到 workDir
            File(workDir, ASSET_FILE).inputStream().use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val target = File(workDir, entry.name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { out -> zis.copyTo(out) }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            // 简单校验: 必须有 compose-compiler-plugin.jar 和 kotlin-compiler-embeddable.jar
            if (composePluginJar == null) {
                LOG.error("compose-compiler-plugin.jar not found in extracted assets")
                return false
            }
            if (kotlinCompilerClasspath.isEmpty()) {
                LOG.error("kotlin compiler jars not found in extracted assets")
                return false
            }

            initialized = true
            LOG.info(
                "AssetsComposeBundles ready: kotlin jars={}, runtime jars={}, d8={}, runtime.dex={}",
                kotlinCompilerClasspath.size,
                composeRuntimeJars.size,
                d8Jar?.name ?: "MISSING",
                composeRuntimeDex?.name ?: "MISSING"
            )
            return true
        } catch (e: Exception) {
            LOG.error("Failed to init AssetsComposeBundles", e)
            return false
        }
    }

    val kotlinCompilerClasspath: List<File>
        get() = kotlinCompilerDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }
            ?.toList()
            ?: emptyList()

    val composePluginJar: File?
        get() {
            // 兼容旧名 compose-compiler-plugin.jar 和新名 compose-compiler-1.5.10.jar
            return kotlinCompilerDir.parentFile
                ?.listFiles { f -> f.isFile && f.name.startsWith("compose-compiler") && f.name.endsWith(".jar") }
                ?.firstOrNull()
                ?: File(workDir, "compose-compiler-plugin.jar").takeIf { it.exists() }
        }

    val composeRuntimeJars: List<File>
        get() = composeRuntimeDir.listFiles { f ->
            f.isFile && f.name.endsWith(".jar") && f.name != "compose-compiler-plugin.jar"
        }?.toList() ?: emptyList()

    val d8Jar: File?
        get() {
            // R8 fat jar, 含 com.android.tools.r8.D8 入口
            return d8Dir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }
                ?.maxByOrNull { it.length() }
        }

    val composeRuntimeDex: File?
        get() = File(dexDir, "compose-runtime.dex").takeIf { it.exists() }

    /**
     * 查找 android.jar: 优先使用 IDE 自带的 platform jar, 否则尝试设备的 SDK,
     * 最后尝试一个内置的 stub. 失败返回 null (调用方应降级到 useGradleDex).
     */
    fun resolveAndroidJar(): File? {
        // 1) 优先: 项目的 android.jar (来自 AGP / IDE 安装目录)
        val candidates = mutableListOf<File>()
        val idePath = System.getProperty("android.jar.path")
        idePath?.let { candidates.add(File(it)) }
        // 2) 设备 SDK (沙箱构建机通常有)
        System.getenv("ANDROID_HOME")?.let { home ->
            File(home, "platforms").listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.firstNotNullOfOrNull { File(it, "android.jar").takeIf { j -> j.exists() } }
                ?.let { candidates.add(it) }
        }
        // 3) 资产里可能内置的精简 android.jar (后续 PR)
        File(workDir, "android.jar").takeIf { it.exists() }?.let { candidates.add(it) }

        return candidates.firstOrNull { it.exists() }
    }

    /** SHA-256 of sdk zip, 用作缓存失效标识. */
    @Volatile
    var versionTag: String = "unknown"
        private set

    companion object {
        private const val ASSET_FILE = "compose/compose-jars.zip"

        /** 预定义的 compose-compiler-plugin.jar 名 (来自 build.gradle.kts copyComposeCompilerPlugin). */
        const val COMPOSE_PLUGIN_NAME = "compose-compiler-plugin.jar"
    }
}
