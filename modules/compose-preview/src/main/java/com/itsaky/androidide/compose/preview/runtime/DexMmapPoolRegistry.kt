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

import java.util.concurrent.atomic.AtomicReference

/**
 * v2.5 P2: 全局 [DexMmapPool] 注册中心.
 *
 * ## 用途
 *
 * UI 层 ([com.itsaky.androidide.compose.preview.ui.PerfPanel] / DebugDrawer) 需要
 * 读取 mmap pool 统计, 但 [ComposeClassLoader] 持有的是 private 字段. 通过本 Registry
 * 提供一个全局访问点, ComposeClassLoader 在构造时 install, UI 端通过 [getOrCreate]
 * / [stats] / [pool] 拉取.
 *
 * ## 用法
 *
 * ```
 * // 在 ComposeClassLoader 构造时
 * DexMmapPoolRegistry.install(mmapPool)
 *
 * // UI 端
 * val stats = DexMmapPoolRegistry.stats()
 * println("active=${stats.activeEntries} hitRate=${stats.hitRate}")
 * ```
 *
 * ## 线程模型
 *
 * - [install] / [getOrCreate] 原子 (AtomicReference + getAndSet / compareAndSet)
 * - [stats] 无锁读
 * - [reset] 测试用
 */
object DexMmapPoolRegistry {

    private val ref = AtomicReference<DexMmapPool?>(null)

    /** 安装 (或替换) 全局 pool. */
    fun install(pool: DexMmapPool) {
        ref.set(pool)
    }

    /** 获取已安装的 pool, 否则 lazy 创建. */
    fun getOrCreate(): DexMmapPool = ref.get() ?: synchronized(this) {
        ref.get() ?: DexMmapPool().also { ref.set(it) }
    }

    /** 当前 pool (可能 null). */
    fun pool(): DexMmapPool? = ref.get()

    /** 直接拉取 stats, 没安装时返回空. */
    fun stats(): DexMmapPool.PoolStats =
        ref.get()?.stats() ?: DexMmapPool.PoolStats(0, 0, 0, 0, 0)

    /** 触发一次 evictStale, 返回释放的 entry 数. */
    fun evictStale(maxAgeMs: Long): Int =
        ref.get()?.evictStale(maxAgeMs) ?: 0

    /** 测试 / 进程退出时清空. */
    fun reset() {
        ref.getAndSet(null)?.clear()
    }
}
