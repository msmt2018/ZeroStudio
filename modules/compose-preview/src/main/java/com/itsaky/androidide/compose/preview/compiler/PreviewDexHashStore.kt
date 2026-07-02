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
import java.security.MessageDigest

/**
 * 预览 dex 哈希存储 v4.
 *
 * ## 用途
 *
 * 避免 "用户改了一行无关代码 → gradle assemble 重跑 → 浪费 30s+ → 用户看到的还是
 * 上次 preview" 这种链式浪费. 哈希用户 compose preview SDK 的 .kt 源 + 编译产物
 * dex, 存到 IDE home 下, 二次进入 preview 时先比对, 未变化就跳过 gradle 直接渲
 * 染.
 *
 * **不** 哈希 androidx compose SDK 等运行时依赖 — 那部分 dex 由 IDE 主 APK 的
 * PathClassLoader 解析, 跟用户代码完全无关, 进 build 缓存里, 本 store 完全不碰.
 *
 * ## 存储位置
 *
 * `<Environment.ANDROIDIDE_HOME>/compose-preview/dex-hashes/<key>.hash`, 故意
 * 放在 `build/` 之外, 避免 `gradle clean` / `rm -rf build/` 一并清掉.
 *
 * ## 哈希策略
 *
 *  - **per-key 摘要**: 用 project modulePath + variant 派生一个 key (例如
 *    `:app-debug`), 文件名就是 `app-debug.hash`, 排查时一眼能定位.
 *  - **覆盖范围**: 哈希 = SHA-256 (project kt 源 ∪ project dex), 每个文件分别
 *    digest 后用分隔符合并再 digest 一次, 顺序按 path 排序, 保证幂等.
 *  - **容错**: 读取失败 / 写失败 / 哈希生成抛异常 → 返回 "未命中", 仍然跑 gradle
 *    assemble. 缓存是优化, 不能变成正确性问题.
 *
 * @author android_zero
 */
class PreviewDexHashStore(
    rootDir: File,
) {

    private val LOG = LoggerFactory.getLogger(PreviewDexHashStore::class.java)

    /**
     * 存储目录. 在 IDE home 下, 不会被 gradle clean 清掉. 故意不在 `build/` 下.
     */
    val storeDir: File = File(rootDir, "compose-preview/dex-hashes").apply {
        mkdirs()
    }

    /**
     * 对一组文件算 SHA-256 (按 path 排序保证幂等). 任一文件不存在或读失败抛
     * [HashingException], 调用方应降级为 "未命中" 处理.
     */
    @Throws(HashingException::class)
    fun hashFiles(files: Collection<File>): String {
        val sorted = files.filter { it.exists() && it.isFile }
            .sortedBy { it.absolutePath }
        if (sorted.isEmpty()) {
            throw HashingException("No files to hash")
        }
        val md = MessageDigest.getInstance("SHA-256")
        for (f in sorted) {
            // 用 "<path>:<size>:" 作前缀, 防止两个不同文件内容相同的边界情况
            // (例如空文件) 互相覆盖. SHA-256 本身对长度有抗碰撞, 加 path 是为了
            // 调试时看到哈希就能定位文件.
            md.update(f.absolutePath.toByteArray(Charsets.UTF_8))
            md.update(0)
            md.update(f.length().toString().toByteArray(Charsets.UTF_8))
            md.update(0)
            f.inputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n <= 0) break
                    md.update(buffer, 0, n)
                }
            }
            md.update(0)
        }
        return md.digest().toHex()
    }

    /**
     * 计算 "compose preview SDK" 的组合哈希 — 用户的 .kt 源 + 编译产物 dex, 不
     * 含 androidx 等运行时依赖. 调用方负责筛掉 androidx 的 .kt / .dex (只传用户
     * 模块自己的).
     */
    @Throws(HashingException::class)
    fun combinedHash(
        userSourceFiles: Collection<File>,
        projectDexFiles: Collection<File>,
    ): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("sources:".toByteArray(Charsets.UTF_8))
        md.update(hashFiles(userSourceFiles).toByteArray(Charsets.UTF_8))
        md.update(0)
        md.update("dex:".toByteArray(Charsets.UTF_8))
        md.update(hashFiles(projectDexFiles).toByteArray(Charsets.UTF_8))
        return md.digest().toHex()
    }

    /**
     * 检查 [key] 对应的上次快照是否与当前 [userSourceFiles] / [projectDexFiles]
     * 完全一致. 一致返回 true (可以跳过 gradle), 不一致返回 false (需要重跑).
     *
     * 任何异常都返回 false (保守: 宁可多跑一次, 也不因缓存错导致 preview 错).
     */
    fun isUnchanged(
        key: String,
        userSourceFiles: Collection<File>,
        projectDexFiles: Collection<File>,
    ): Boolean {
        val current = try {
            combinedHash(userSourceFiles, projectDexFiles)
        } catch (e: HashingException) {
            LOG.debug("Hashing failed for {}: {}", key, e.message)
            return false
        }
        val stored = readStored(key) ?: return false
        return current.equals(stored, ignoreCase = true)
    }

    /**
     * 把 [userSourceFiles] / [projectDexFiles] 的当前哈希存到磁盘. gradle 跑
     * 完后调用, 供下次启动比对.
     */
    fun store(
        key: String,
        userSourceFiles: Collection<File>,
        projectDexFiles: Collection<File>,
    ) {
        try {
            val current = combinedHash(userSourceFiles, projectDexFiles)
            writeStored(key, current)
        } catch (e: Throwable) {
            LOG.warn("Failed to write hash for {}", key, e)
        }
    }

    /**
     * 列出所有已存 key. 主要给 debug / "清空缓存" UI 用.
     */
    fun listKeys(): List<String> = storeDir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".hash") }
        ?.map { it.name.removeSuffix(".hash") }
        ?: emptyList()

    /**
     * 删掉单个 key 的快照 (例如某次 build 失败后, 下次应该强制重跑).
     */
    fun invalidate(key: String) {
        try {
            hashFile(key).delete()
        } catch (e: Throwable) {
            LOG.warn("Failed to invalidate hash for {}", key, e)
        }
    }

    // ---------------------------------------------------------------------
    //  内部
    // ---------------------------------------------------------------------

    private fun readStored(key: String): String? = try {
        val f = hashFile(key)
        if (f.exists()) f.readText(Charsets.UTF_8).trim() else null
    } catch (e: Throwable) {
        LOG.debug("Failed to read hash for {}: {}", key, e.message)
        null
    }

    private fun writeStored(key: String, value: String) {
        val f = hashFile(key)
        f.parentFile?.mkdirs()
        // 原子写: 写临时文件再 rename, 避免 gradle assemble 过程中被杀导致半截 hash.
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeText(value, Charsets.UTF_8)
        if (f.exists()) f.delete()
        if (!tmp.renameTo(f)) {
            // rename 失败 (跨 mount) → 退化成 copy
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
    }

    private fun hashFile(key: String): File =
        File(storeDir, "${sanitizeKey(key)}.hash")

    private fun sanitizeKey(key: String): String =
        key.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { "%02x".format(it) }
}

class HashingException(message: String) : RuntimeException(message)
