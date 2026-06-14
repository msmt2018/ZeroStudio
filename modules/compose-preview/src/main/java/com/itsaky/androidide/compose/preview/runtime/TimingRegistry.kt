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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * v2.5 P0 P3-FE-03: 性能埋点注册中心.
 *
 * ## 测量点
 *
 * 5 个关键阶段 (与 [Phase] 对应):
 * - [Phase.COMPILE]    K2JVMCompiler 编译 .kt → .class
 * - [Phase.DEX]        d8 .class → .dex
 * - [Phase.CLASSLOAD]  DexClassLoader.loadClass
 * - [Phase.RENDER]     Composable 渲染到 BoundedComposeView
 * - [Phase.SERIALIZE]  PreviewState JSON 序列化
 *
 * 每个阶段独立维护一个滚动窗口, 默认保留最近 [DEFAULT_WINDOW_SIZE] 个样本.
 * 暴露 [snapshot] 给 DebugDrawer Perf 面板展示 avg / p50 / p95 / max / count.
 *
 * ## 用法
 *
 * ```
 * val elapsed = measureTimeMillis { doWork() }
 * TimingRegistry.record(Phase.RENDER, elapsed)
 *
 * // 或使用 [time] 包装
 * val result = TimingRegistry.time(Phase.RENDER) { compute() }
 * ```
 *
 * ## 线程模型
 *
 * - [record] 无锁 (AtomicLong + ConcurrentHashMap)
 * - [snapshot] 线程安全
 * - [reset] 仅用于测试
 */
object TimingRegistry {

    private val LOG = LoggerFactory.getLogger(TimingRegistry::class.java)

    /** 5 个性能阶段. 与 [PhaseStats] 一一对应. */
    enum class Phase(val label: String) {
        COMPILE("Compile"),
        DEX("Dex"),
        CLASSLOAD("ClassLoad"),
        RENDER("Render"),
        SERIALIZE("Serialize"),
    }

    /** 单阶段统计. */
    data class PhaseStats(
        val phase: Phase,
        val count: Long,
        val avgMs: Double,
        val p50Ms: Double,
        val p95Ms: Double,
        val maxMs: Long,
        val totalMs: Long,
    ) {
        companion object {
            val EMPTY = PhaseStats(Phase.COMPILE, 0, 0.0, 0.0, 0.0, 0, 0)
        }
    }

    /** 全局快照, 5 个阶段打包. */
    data class TimingSnapshot(
        val phases: Map<Phase, PhaseStats>,
        val capturedAt: Long,
    )

    /** 内部滚动窗口. 线程安全 append. */
    private class RollingWindow(val capacity: Int) {
        private val samples = ArrayDeque<Long>(capacity)
        private val lock = Any()

        fun add(value: Long) {
            synchronized(lock) {
                if (samples.size >= capacity) samples.removeFirst()
                samples.addLast(value)
            }
        }

        fun stats(phase: Phase): PhaseStats {
            val snapshot: LongArray
            val count: Long
            val total: Long
            synchronized(lock) {
                snapshot = samples.toLongArray()
                count = samples.size.toLong()
                total = samples.sum()
            }
            if (count == 0L) return PhaseStats(phase, 0, 0.0, 0.0, 0.0, 0, 0)
            val sorted = snapshot.sortedArray()
            val avg = total.toDouble() / count
            val p50 = sorted[((sorted.size - 1) * 0.50).toInt()]
            val p95 = sorted[((sorted.size - 1) * 0.95).toInt()]
            val max = sorted.last()
            return PhaseStats(phase, count, avg, p50.toDouble(), p95.toDouble(), max, total)
        }

        fun clear() = synchronized(lock) { samples.clear() }
    }

    private val windows = ConcurrentHashMap<Phase, RollingWindow>().apply {
        Phase.values().forEach { put(it, RollingWindow(DEFAULT_WINDOW_SIZE)) }
    }
    private val totalRecords = AtomicLong(0L)

    /**
     * 记录一次阶段耗时.
     *
     * @param phase 阶段
     * @param elapsedMs 耗时 (ms, 负数会被忽略)
     */
    fun record(phase: Phase, elapsedMs: Long) {
        if (elapsedMs < 0) return
        windows[phase]?.add(elapsedMs)
        totalRecords.incrementAndGet()
    }

    /**
     * 包装执行 + 埋点. 返回 lambda 结果.
     *
     * ```
     * val result = TimingRegistry.time(Phase.RENDER) { myComposable() }
     * ```
     */
    inline fun <T> time(phase: Phase, block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val elapsed = (System.nanoTime() - start) / 1_000_000L
            record(phase, elapsed)
        }
    }

    /** 拉取全阶段快照. */
    fun snapshot(): TimingSnapshot = TimingSnapshot(
        phases = windows.entries.associate { (p, w) -> p to w.stats(p) },
        capturedAt = System.currentTimeMillis(),
    )

    /** 单阶段统计查询. */
    fun statsFor(phase: Phase): PhaseStats =
        windows[phase]?.stats(phase) ?: PhaseStats.EMPTY

    /** 总记录数. */
    fun totalRecordCount(): Long = totalRecords.get()

    /** 重置全部窗口 (测试用). */
    fun reset() {
        windows.values.forEach { it.clear() }
        totalRecords.set(0L)
        LOG.debug("TimingRegistry reset")
    }

    const val DEFAULT_WINDOW_SIZE: Int = 64
}
