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

package com.itsaky.androidide.compose.preview.bytecode

import java.util.concurrent.atomic.AtomicLong

/**
 * 字节码 binder 统计快照 v2.1 (P3 补).
 *
 * 由 [BinderStatsRegistry.snapshot] 周期性采集, 推到 DebugDrawer 的 Stats tab
 * 用于:
 * - 验证 P3 字节码加速是否生效 (FieldAccessorCache 命中率应稳定 > 90%)
 * - 排查 K2JVMCompiler 反射调用热点 (k2BinderCumulativeExecs 递增节奏)
 * - 检测 Compose 版本不兼容 (layoutBinderTotalBoundFields 期望 ≈ 9)
 *
 * 字段分类:
 * 1. **FieldAccessorCache** (singleton) — 字段访问 MethodHandle 缓存
 * 2. **K2StaticBinder** (per-classloader) — K2JVMCompiler 反射 binder
 * 3. **LayoutNodeBinder** (per-classloader) — LayoutNode 字段访问 binder
 *
 * @property fieldAccessorSize 当前 FieldAccessor 缓存条目数
 * @property fieldAccessorHits 累计命中次数
 * @property fieldAccessorMisses 累计未命中次数
 * @property fieldAccessorHitRate 命中率, 范围 [0, 1]
 * @property k2BinderCount K2StaticBinder 实例数 (≈ classloader 数)
 * @property k2CumulativeExecs 累计 K2JVMCompiler.exec 调用次数
 * @property k2CumulativeNewInstances 累计 K2JVMCompiler 构造次数
 * @property layoutBinderCount LayoutNodeBinder 实例数
 * @property layoutBinderTotalBoundFields 累计绑定成功的 LayoutNode 字段数
 *                           (期望 ≈ 9 × binderCount)
 * @property snapshotTakenAtMs snapshot 调用时刻 (System.currentTimeMillis)
 */
data class BinderStats(
    val fieldAccessorSize: Int = 0,
    val fieldAccessorHits: Long = 0,
    val fieldAccessorMisses: Long = 0,
    val fieldAccessorHitRate: Double = 0.0,
    val k2BinderCount: Int = 0,
    val k2CumulativeExecs: Long = 0,
    val k2CumulativeNewInstances: Long = 0,
    val layoutBinderCount: Int = 0,
    val layoutBinderTotalBoundFields: Int = 0,
    val snapshotTakenAtMs: Long = 0L,
) {
    companion object {
        val EMPTY = BinderStats()
    }
}

/**
 * Binder 统计中心 v2.1.
 *
 * 聚合 3 个 binder 子系统的运行指标, 供 DebugDrawer Stats tab 展示.
 *
 * ## 用法
 *
 * ```kotlin
 * // 渲染线程周期性采集 (例如每 500ms):
 * LaunchedEffect(Unit) {
 *     while (isActive) {
 *         val stats = BinderStatsRegistry.snapshot()
 *         // 推到 ViewModel → DebugDrawer
 *         delay(500)
 *     }
 * }
 * ```
 *
 * ## 线程安全
 *
 * 全部使用 [AtomicLong] + `ConcurrentHashMap`, 多线程并发 snapshot 安全.
 *
 * ## 开销
 *
 * snapshot 主要是几个 `AtomicLong.get()` + `ConcurrentHashMap.size()`,
 * 单次 < 1µs, 可放心高频采集.
 */
object BinderStatsRegistry {

    // ----- FieldAccessorCache (singleton object) -----
    // 直接转调 FieldAccessorCache.size/hits/misses/hitRate

    // ----- K2StaticBinder 累计计数 -----
    private val k2CumulativeExecs = AtomicLong(0)
    private val k2CumulativeNewInstances = AtomicLong(0)

    // ----- LayoutNodeBinder 累计绑定字段数 -----
    // 通过 K2StaticBinder.cacheSize / LayoutNodeBinder.cacheSize 计算

    /**
     * 取一次 binder 统计快照.
     */
    @JvmStatic
    fun snapshot(): BinderStats {
        val fieldSize = FieldAccessorCache.size()
        val hits = FieldAccessorCache.hits()
        val misses = FieldAccessorCache.misses()
        val layoutTotalFields = LayoutNodeBinder.totalBoundFields()
        return BinderStats(
            fieldAccessorSize = fieldSize,
            fieldAccessorHits = hits,
            fieldAccessorMisses = misses,
            fieldAccessorHitRate = FieldAccessorCache.hitRate(),
            k2BinderCount = K2StaticBinder.binderCount(),
            k2CumulativeExecs = k2CumulativeExecs.get(),
            k2CumulativeNewInstances = k2CumulativeNewInstances.get(),
            layoutBinderCount = LayoutNodeBinder.binderCount(),
            layoutBinderTotalBoundFields = layoutTotalFields,
            snapshotTakenAtMs = System.currentTimeMillis(),
        )
    }

    // -------- 内部 counter, 供 binder 内部 increment --------

    /** 由 K2StaticBinder.exec 调用处触发. */
    @JvmStatic
    internal fun recordK2Exec() {
        k2CumulativeExecs.incrementAndGet()
    }

    /** 由 K2StaticBinder.newK2Instance 调用处触发. */
    @JvmStatic
    internal fun recordK2NewInstance() {
        k2CumulativeNewInstances.incrementAndGet()
    }

    /** 测试用: 清零所有 counter. */
    @JvmStatic
    fun resetCounters() {
        k2CumulativeExecs.set(0)
        k2CumulativeNewInstances.set(0)
    }
}
