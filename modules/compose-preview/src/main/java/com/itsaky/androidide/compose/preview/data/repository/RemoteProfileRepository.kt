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

package com.itsaky.androidide.compose.preview.data.repository

import com.itsaky.androidide.compose.preview.ui.DeviceProfile
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v2.5 P0: 远程设备 Profile 同步仓库.
 *
 * ## 设计目标
 *
 * 内置 [com.itsaky.androidide.compose.preview.data.device.DeviceCatalog] 提供 30+ 真实
 * 设备, 但 IDE 用户可能需要自定义 Profile (例如: 公司内部测试设备 / 仿真器配置). 本类
 * 通过 HTTP 拉取远程 Profile 列表, 与本地内置合并, 缓存到 `<cacheDir>/profiles-v1.json`.
 *
 * ## 协议
 *
 * 远程 endpoint 返回 JSON 数组, 每个元素:
 * ```
 * [
 *   {
 *     "id": "company-test-device",
 *     "displayName": "公司测试机",
 *     "formFactor": "PHONE",
 *     "widthPx": 1080,
 *     "heightPx": 2400,
 *     "densityDpi": 440,
 *     ...
 *   },
 *   ...
 * ]
 * ```
 *
 * 失败时 (网络 / 解析) 返回上次缓存或空列表, 不抛异常.
 *
 * ## 线程模型
 *
 * - [fetchFromRemote] 阻塞 IO, 推荐在 IO 线程.
 * - [getMerged] 内存合并, 快速.
 * - [cacheProfiles] 原子写 (tmp + rename).
 */
class RemoteProfileRepository(
    private val cacheDir: File,
    private val fetcher: ProfileFetcher = HttpProfileFetcher(),
    private val cacheFileName: String = "profiles-v1.json",
) {

    private val LOG = LoggerFactory.getLogger(RemoteProfileRepository::class.java)

    /** 远程数据源接口, 便于测试时 mock. */
    fun interface ProfileFetcher {
        fun fetch(url: String, timeoutMs: Long): String
    }

    /** 内存中的远程 Profile 缓存. */
    @Volatile
    private var remoteProfiles: List<DeviceProfile> = emptyList()

    @Volatile
    private var lastSyncTs: Long = 0L

    init {
        cacheDir.mkdirs()
        // 启动时尝试加载磁盘缓存
        runCatching { loadFromDisk() }
            .onFailure { LOG.warn("RemoteProfileRepository: cache load failed: {}", it.message) }
    }

    /**
     * 拉取远程 Profile, 写磁盘缓存, 更新内存.
     *
     * @return 成功获取的数量, 失败 (无网络 / 解析错误) 时返回磁盘缓存大小
     */
    fun fetchFromRemote(url: String, timeoutMs: Long = 5_000L): Int {
        val json = try {
            fetcher.fetch(url, timeoutMs)
        } catch (e: Throwable) {
            LOG.warn("RemoteProfileRepository: fetch failed: {}", e.message)
            return remoteProfiles.size
        }
        val parsed = parseJsonList(json)
        if (parsed.isEmpty()) {
            LOG.warn("RemoteProfileRepository: parsed 0 profiles, keeping previous cache")
            return remoteProfiles.size
        }
        remoteProfiles = parsed
        lastSyncTs = System.currentTimeMillis()
        runCatching { saveToDisk(json) }.onFailure {
            LOG.warn("RemoteProfileRepository: cache save failed: {}", it.message)
        }
        LOG.info("RemoteProfileRepository: synced {} profiles from {}", parsed.size, url)
        return parsed.size
    }

    /** 远程 Profile 列表 (只读). */
    fun remoteProfiles(): List<DeviceProfile> = remoteProfiles

    fun lastSyncTimestamp(): Long = lastSyncTs

    fun cacheFile(): File = File(cacheDir, cacheFileName)

    /**
     * 与本地 [local] 合并, 远程优先 (id 相同则覆盖本地).
     */
    fun getMerged(local: List<DeviceProfile>): List<DeviceProfile> {
        if (remoteProfiles.isEmpty()) return local
        val byId = local.associateBy { it.id }.toMutableMap()
        remoteProfiles.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    /** 清除远程缓存 (测试 / 手动刷新). */
    fun clearRemote() {
        remoteProfiles = emptyList()
        lastSyncTs = 0L
    }

    private fun loadFromDisk() {
        val f = cacheFile()
        if (!f.exists() || f.length() == 0L) return
        val json = f.readText(Charsets.UTF_8)
        val parsed = parseJsonList(json)
        if (parsed.isNotEmpty()) {
            remoteProfiles = parsed
            lastSyncTs = f.lastModified()
            LOG.info("RemoteProfileRepository: loaded {} cached profiles", parsed.size)
        }
    }

    private fun saveToDisk(json: String) {
        val target = cacheFile()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("cannot delete old cache: ${target.absolutePath}")
        }
        if (!tmp.renameTo(target)) {
            throw IllegalStateException("rename failed: ${tmp.absolutePath} -> ${target.absolutePath}")
        }
    }

    /**
     * 极简 JSON 解析 — 仅提取 `id` / `displayName` / `formFactor` /
     * `widthPx` / `heightPx` / `densityDpi` 字段, 不引入 gson 依赖.
     * 不支持嵌套对象, 失败字段跳过.
     */
    internal fun parseJsonList(json: String): List<DeviceProfile> {
        val items = mutableListOf<DeviceProfile>()
        val arrayPattern = """\{[^{}]*\}""".toRegex()
        arrayPattern.findAll(json).forEach { match ->
            val obj = match.value
            val id = extractString(obj, "id") ?: return@forEach
            val name = extractString(obj, "displayName") ?: id
            val formFactor = extractString(obj, "formFactor") ?: "PHONE"
            val width = extractInt(obj, "widthPx") ?: 1080
            val height = extractInt(obj, "heightPx") ?: 1920
            val density = extractInt(obj, "densityDpi") ?: 420
            items.add(
                DeviceProfile(
                    id = id,
                    displayName = name,
                    formFactor = DeviceProfile.FormFactor.valueOf(formFactor.uppercase()),
                    widthPx = width,
                    heightPx = height,
                    densityDpi = density,
                )
            )
        }
        return items
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = """"$key"\s*:\s*(-?\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * 默认 HTTP fetcher. 不引入第三方 HTTP 库, 直接使用 [HttpURLConnection] 避免依赖膨胀.
     */
    class HttpProfileFetcher : ProfileFetcher {
        override fun fetch(url: String, timeoutMs: Long): String {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs.toInt()
            conn.readTimeout = timeoutMs.toInt()
            conn.setRequestProperty("Accept", "application/json")
            return try {
                if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    throw java.io.IOException("HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
