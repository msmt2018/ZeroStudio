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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * v2.5 P2: DexMmapPool 定时 evict 调度器.
 *
 * ## 设计目标
 *
 * [DexMmapPool.evictStale] 释放 5 分钟以上无引用的 mmap entry. 但实际项目中需要
 * 有人定时触发这个清理, 否则 mmap 永远占用物理页. 本类用协程定时 (默认 10 分钟) 跑一次.
 *
 * ## 用法
 *
 * ```
 * // 在 DebugDrawer / Application onCreate
 * val evictor = DexMmapPoolEvictor()
 * evictor.start()
 *
 * // UI destroy / 进程退出
 * evictor.stop()
 * ```
 *
 * ## 线程模型
 *
 * - 后台 coroutine (Dispatchers.Default) 定时触发
 * - [start] 幂等 (compareAndSet)
 * - [stop] 取消 scope
 * - [evictCount] 统计累计 evict 次数
 */
class DexMmapPoolEvictor(
    private val pool: DexMmapPool = DexMmapPoolRegistry.getOrCreate(),
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
) {

    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    private val evictCount = AtomicLong(0L)
    private val evictedEntries = AtomicLong(0L)

    /** 启动定时 evict. 幂等. */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        job = s.launch {
            while (isActive) {
                delay(intervalMs)
                try {
                    val n = pool.evictStale(maxAgeMs)
                    if (n > 0) {
                        evictCount.incrementAndGet()
                        evictedEntries.addAndGet(n.toLong())
                    }
                } catch (e: Throwable) {
                    // 后台异常, 不抛
                }
            }
        }
    }

    fun isRunning(): Boolean = running.get()

    /** 累计触发次数. */
    fun evictRunCount(): Long = evictCount.get()

    /** 累计释放的 entry 数. */
    fun evictedEntryCount(): Long = evictedEntries.get()

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        job?.cancel()
        scope?.cancel()
        job = null
        scope = null
    }

    companion object {
        /** 10 分钟 evict 一次. */
        const val DEFAULT_INTERVAL_MS: Long = 10L * 60_000L

        /** 5 分钟无引用的 entry 视为 stale. */
        const val DEFAULT_MAX_AGE_MS: Long = 5L * 60_000L
    }
}
