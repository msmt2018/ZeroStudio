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

package com.itsaky.androidide.compose.preview.runtime

import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * v2.2 P4 持久化管理器 — 单一入口, 跨多个协程安全.
 *
 * ## 职责
 *
 * 1. **内存 store**: in-memory `ConcurrentHashMap<className, groupMap>`
 * 2. **延迟写盘**: 1s debounce, 多次 setLiteral 合并为一次 JSON write
 * 3. **加载**: 启动时从 `<project>/.androidide/live-state.json` 读 snapshot
 * 4. **stale check**: 读取时校验 sourceHash, 不一致视为过期
 * 5. **设备/主题持久化**: deviceProfile / theme / debugEnabled / displayMode
 *
 * ## 关键不变量
 *
 * - 写操作 (setLiteral) 立即更新内存, 标记 dirty, schedule 1s 后写盘
 * - 读操作 (getLiteral) 直接读内存, 同步
 * - atomic write: 写 tmp + rename, 避免半写状态
 * - 项目隔离: 不同项目用不同 instance (per [install])
 * - sourceHash 校验失败时返回 null, 不污染内存
 *
 * ## 线程安全
 *
 * - 内存 store 用 `ConcurrentHashMap`, 多线程 set/get 安全
 * - dirty bit 用 `AtomicBoolean` (CAS)
 * - 写盘跑在 [PersistenceScheduler] 单线程, 与 setLiteral 调用方隔离
 *
 * ## 用法
 *
 * ```kotlin
 * // 1. 启动时 (Repository.initialize)
 * val manager = LiveStatePersistenceManager.install(File(projectDir, ".androidide"))
 * manager.startScheduler()
 * manager.load()  // 从磁盘加载
 *
 * // 2. 每次 setLiteral
 * manager.setLiteral(className, groupKey, encodedValue, typeName, sourceHash)
 * manager.scheduleFlush()
 *
 * // 3. 编译后恢复
 * val restored = manager.getLiteral(className, groupKey, currentSourceHash)
 * if (restored != null) editor.updateValue(group, restored.toValue())
 * ```
 */
class LiveStatePersistenceManager private constructor(
    private val projectDir: File,
) {
    private val LOG = LoggerFactory.getLogger(LiveStatePersistenceManager::class.java)

    private val stateDir = File(projectDir, ".androidide").apply { mkdirs() }
    private val stateFile = File(stateDir, "live-state.json")
    private val stateBackup = File(stateDir, "live-state.json.bak")

    private val scheduler = PersistenceScheduler("LiveState-$projectDir.name")

    // 内存 store
    private val literalsByClass = ConcurrentHashMap<String, ConcurrentHashMap<String, PersistedLiteral>>()

    // 设备/主题/preferences
    private val deviceProfileRef = AtomicReference<String?>(null)
    private val themeRef = AtomicReference<String?>(null)
    private val debugEnabledRef = AtomicReference(false)
    private val displayModeRef = AtomicReference<String?>(null)

    private val dirty = AtomicBoolean(false)
    private val loaded = AtomicBoolean(false)
    private val started = AtomicBoolean(false)

    fun startScheduler() {
        if (started.getAndSet(true)) return
        scheduler.start()
    }

    /**
     * 同步加载磁盘快照到内存. 通常在 [startScheduler] 之后调用.
     *
     * 失败时:
     * - 文件不存在 → no-op, 内存为空
     * - 解析失败 → 备份为 .bak, 内存为空
     * - schema 不匹配 → 备份为 .bak, 内存为空
     */
    fun load() {
        if (loaded.getAndSet(true)) return
        if (!stateFile.exists()) {
            LOG.info("No existing live state at {}", stateFile.absolutePath)
            return
        }
        val json = try {
            stateFile.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            LOG.warn("Failed to read live state: {}", e.message)
            return
        }
        val snapshot = LiveStateJsonCodec.decode(json)
        if (snapshot == null) {
            // 损坏: 备份为 .bak, 便于排查
            try {
                if (stateFile.exists()) stateFile.copyTo(stateBackup, overwrite = true)
                stateFile.delete()
            } catch (e: IOException) {
                LOG.warn("Failed to backup corrupted live state: {}", e.message)
            }
            return
        }

        // literals
        for ((className, groups) in snapshot.literals) {
            val classMap = ConcurrentHashMap<String, PersistedLiteral>()
            for ((groupKey, literal) in groups) {
                classMap[groupKey] = literal
            }
            literalsByClass[className] = classMap
        }
        // prefs
        deviceProfileRef.set(snapshot.deviceProfile)
        themeRef.set(snapshot.theme)
        debugEnabledRef.set(snapshot.debugEnabled)
        displayModeRef.set(snapshot.displayMode)

        LOG.info(
            "Loaded live state from {} ({} classes, device={}, theme={})",
            stateFile.absolutePath, literalsByClass.size, snapshot.deviceProfile, snapshot.theme,
        )
    }

    /**
     * 写一条字面量到内存. 不立即写盘 — 需调用 [scheduleFlush] 或 [flushNow].
     *
     * @param className e.g. `com.example.MyKt`
     * @param groupKey  e.g. `intLit-12345` (primaryFieldName)
     * @param value     primary 字段编码值
     * @param pairedValue 配对字段 (LONG / COLOR 才有)
     * @param type      [LiveLiteralType.name]
     * @param sourceHash 当前 source FNV-1a hash
     */
    fun setLiteral(
        className: String,
        groupKey: String,
        value: Int,
        pairedValue: Int?,
        type: String,
        sourceHash: Int,
    ) {
        val classMap = literalsByClass.computeIfAbsent(className) { ConcurrentHashMap() }
        classMap[groupKey] = PersistedLiteral(
            value = value,
            pairedValue = pairedValue,
            type = type,
            sourceHash = sourceHash,
            lastModified = LiveStateJsonCodec.nowIso(),
        )
        dirty.set(true)
    }

    /**
     * 读取一条字面量. 若 [currentSourceHash] 与持久化 sourceHash 不一致返回 null.
     */
    fun getLiteral(
        className: String,
        groupKey: String,
        currentSourceHash: Int,
    ): PersistedLiteral? {
        val classMap = literalsByClass[className] ?: return null
        val literal = classMap[groupKey] ?: return null
        if (literal.sourceHash != 0 && literal.sourceHash != currentSourceHash) {
            // stale: source 改变后旧值失效
            return null
        }
        return literal
    }

    fun setDeviceProfile(name: String?) {
        deviceProfileRef.set(name)
        dirty.set(true)
    }
    fun getDeviceProfile(): String? = deviceProfileRef.get()

    fun setTheme(theme: String?) {
        themeRef.set(theme)
        dirty.set(true)
    }
    fun getTheme(): String? = themeRef.get()

    fun setDebugEnabled(enabled: Boolean) {
        debugEnabledRef.set(enabled)
        dirty.set(true)
    }
    fun getDebugEnabled(): Boolean = debugEnabledRef.get()

    fun setDisplayMode(mode: String?) {
        displayModeRef.set(mode)
        dirty.set(true)
    }
    fun getDisplayMode(): String? = displayModeRef.get()

    /**
     * 安排 1s 后的写盘任务. 多次调用会合并.
     */
    fun scheduleFlush() {
        scheduler.schedule(FLUSH_DELAY_MS) { flushNow() }
    }

    /**
     * 立即写盘. atomic write: tmp + rename.
     *
     * 线程安全: 由 [PersistenceScheduler] 保证单线程执行.
     */
    fun flushNow() {
        if (!dirty.compareAndSet(true, false)) {
            return // 没有 dirty, 跳过
        }
        val snapshot = buildSnapshot()
        val json = try {
            LiveStateJsonCodec.encode(snapshot)
        } catch (e: Throwable) {
            LOG.error("Failed to encode live state: {}", e.message)
            return
        }
        val tmp = File(stateDir, "live-state.json.tmp")
        try {
            tmp.writeText(json, Charsets.UTF_8)
            // atomic rename
            if (stateFile.exists()) stateFile.delete()
            if (!tmp.renameTo(stateFile)) {
                // fallback: copy + delete
                tmp.copyTo(stateFile, overwrite = true)
                tmp.delete()
            }
            LOG.debug("Wrote live state to {} ({} bytes)", stateFile.absolutePath, json.length)
        } catch (e: IOException) {
            LOG.error("Failed to write live state: {}", e.message)
        }
    }

    fun clear() {
        literalsByClass.clear()
        deviceProfileRef.set(null)
        themeRef.set(null)
        debugEnabledRef.set(false)
        displayModeRef.set(null)
        dirty.set(true)
    }

    fun snapshot(): LiveStateSnapshot = buildSnapshot()

    fun release() {
        flushNow()
        scheduler.shutdown()
    }

    private fun buildSnapshot(): LiveStateSnapshot {
        val literals = LinkedHashMap<String, Map<String, PersistedLiteral>>()
        for ((className, classMap) in literalsByClass) {
            val sorted = LinkedHashMap<String, PersistedLiteral>()
            // 按 groupKey 字母序, 输出稳定
            for (key in classMap.keys.sorted()) {
                sorted[key] = classMap[key]!!
            }
            literals[className] = sorted
        }
        return LiveStateSnapshot(
            version = LiveStateJsonCodec.SCHEMA_VERSION,
            lastUpdated = LiveStateJsonCodec.nowIso(),
            literals = literals,
            deviceProfile = deviceProfileRef.get(),
            theme = themeRef.get(),
            debugEnabled = debugEnabledRef.get(),
            displayMode = displayModeRef.get(),
        )
    }

    companion object {
        private const val FLUSH_DELAY_MS = 1000L

        /**
         * 全局活跃 instance. 同一项目只允许一个 instance.
         */
        private val activeRef = AtomicReference<LiveStatePersistenceManager?>(null)

        /**
         * 装入一个 instance. 同一 [projectDir] 重复调用返回已有 instance;
         * 不同 projectDir 时先释放旧的再装入新的.
         */
        fun install(projectDir: File): LiveStatePersistenceManager {
            val existing = activeRef.get()
            if (existing != null && existing.projectDir == projectDir) {
                return existing
            }
            // 不同 project: 释放旧的
            if (existing != null) {
                existing.release()
            }
            val mgr = LiveStatePersistenceManager(projectDir)
            activeRef.set(mgr)
            LOG.info("Installed LiveStatePersistenceManager for {}", projectDir.absolutePath)
            return mgr
        }

        fun getActive(): LiveStatePersistenceManager? = activeRef.get()

        /**
         * 测试用: 卸载当前 instance.
         */
        fun uninstall() {
            activeRef.getAndSet(null)?.release()
        }
    }
}
