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

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * v2.2 P3 Live Edit 统计快照.
 *
 * 通过 [LiveEditStatsRegistry] 全局单例暴露, [DebugDrawer] 每 500ms 拉取一次.
 *
 * ## 字段语义
 *
 * - [reloadCount]      成功 hot-reload 次数 (DEX swap 成功)
 * - [errorCount]       编译/dex 失败次数 (不会清空旧预览, 只更新 indicator)
 * - [lastReloadMs]     上次 reload 端到端耗时 (ms) — 从 source change 到 render 完成
 * - [avgReloadMs]      滚动平均 (exponential moving average, alpha=0.3)
 * - [lastReloadTs]     上次 reload 时间戳 (epoch ms)
 * - [lastError]        上次错误信息 (nullable)
 * - [lastSourceHash]   上次 reload 的源 hash (32-bit FNV-1a) — 用于 cache 校验
 * - [paused]           是否暂停 (用户临时禁用 hot reload)
 *
 * 所有字段都通过 atomic 读写, 无锁.
 */
data class LiveEditStatsSnapshot(
    val reloadCount: Long = 0L,
    val errorCount: Long = 0L,
    val lastReloadMs: Long = 0L,
    val avgReloadMs: Double = 0.0,
    val lastReloadTs: Long = 0L,
    val lastError: String? = null,
    val lastSourceHash: Int = 0,
    val paused: Boolean = false,
)

/**
 * 内部可变状态, 配合 [LiveEditStatsRegistry] 提供 atomic 写入.
 *
 * 线程模型: 仅在 [LiveEditCoordinator] 的协程内调用 (单线程), 但 [snapshot]
 * 可从任意线程 (UI / DebugDrawer) 拉取, 因此用 [AtomicReference] 保证可见性.
 */
class LiveEditStats internal constructor() {
    private val reloadCountRef = AtomicLong(0L)
    private val errorCountRef = AtomicLong(0L)
    private val lastReloadMsRef = AtomicLong(0L)
    private val avgReloadMsRef = java.util.concurrent.atomic.AtomicReference(0.0)
    private val lastReloadTsRef = AtomicLong(0L)
    private val lastErrorRef = AtomicReference<String?>(null)
    private val lastSourceHashRef = AtomicLong(0L)
    private val pausedRef = AtomicReference(false)

    internal fun recordSuccess(elapsedMs: Long, sourceHash: Int) {
        reloadCountRef.incrementAndGet()
        lastReloadMsRef.set(elapsedMs)
        lastReloadTsRef.set(System.currentTimeMillis())
        lastSourceHashRef.set(sourceHash.toLong() and 0xFFFFFFFFL)
        // EMA: avg = avg * 0.7 + sample * 0.3
        val sample = elapsedMs.toDouble()
        avgReloadMsRef.updateAndGet { prev -> prev * 0.7 + sample * 0.3 }
        lastErrorRef.set(null)
    }

    internal fun recordError(message: String, sourceHash: Int) {
        errorCountRef.incrementAndGet()
        lastErrorRef.set(message)
        lastSourceHashRef.set(sourceHash.toLong() and 0xFFFFFFFFL)
    }

    internal fun setPaused(paused: Boolean) {
        pausedRef.set(paused)
    }

    fun snapshot(): LiveEditStatsSnapshot = LiveEditStatsSnapshot(
        reloadCount = reloadCountRef.get(),
        errorCount = errorCountRef.get(),
        lastReloadMs = lastReloadMsRef.get(),
        avgReloadMs = avgReloadMsRef.get(),
        lastReloadTs = lastReloadTsRef.get(),
        lastError = lastErrorRef.get(),
        lastSourceHash = (lastSourceHashRef.get() and 0xFFFFFFFFL).toInt(),
        paused = pausedRef.get(),
    )
}

/**
 * v2.2 P3 全局 LiveEditStats registry.
 *
 * 单例; 与 v2.1 P3-P5 的 [BinderStatsRegistry] / [CompilationCacheHolder] / [DexCacheHolder]
 * 模式一致 — atomic install + lazy snapshot.
 */
object LiveEditStatsRegistry {
    private val ref = AtomicReference<LiveEditStats?>(null)

    fun install(stats: LiveEditStats) {
        ref.set(stats)
    }

    fun get(): LiveEditStats? = ref.get()

    fun snapshotOrEmpty(): LiveEditStatsSnapshot =
        ref.get()?.snapshot() ?: LiveEditStatsSnapshot()

    fun recordSuccess(elapsedMs: Long, sourceHash: Int) {
        ref.get()?.recordSuccess(elapsedMs, sourceHash)
    }

    fun recordError(message: String, sourceHash: Int) {
        ref.get()?.recordError(message, sourceHash)
    }

    fun setPaused(paused: Boolean) {
        ref.get()?.setPaused(paused)
    }
}
