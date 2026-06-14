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
package com.itsaky.androidide.perf.ui

import com.itsaky.androidide.perf.proto.PerfEvent
import com.itsaky.androidide.perf.store.BootHistoryStore

/**
 * Perf Console UI 状态 (PR #5/5).
 *
 * 由 [PerfConsoleViewModel] 1Hz 刷新, 不可变 data class, Compose
 * `collectAsState` 直接订阅.
 *
 * ## 字段
 *
 * - [isReady] / [isBootEnded]: 控制 loading / boot banner
 * - [bootEvents] / [bootHistory]: Boot tab 数据
 * - [recentFps] / [recentPssKb] / [recentGcDelta] / [anrEvents]: 实时 tab 数据
 * - [totalBootMs]: 启动总耗时 (Boot tab 大字显示)
 *
 * @author android_zero
 */
data class PerfUiState(
    val isReady: Boolean = false,
    val isBootEnded: Boolean = false,
    val bootEvents: List<PerfEvent> = emptyList(),
    val bootHistory: List<BootHistoryStore.BootSession> = emptyList(),
    val recentFps: List<Int> = emptyList(),
    val recentPssKb: List<Long> = emptyList(),
    val recentGcDelta: List<Long> = emptyList(),
    val anrEvents: List<PerfEvent.Instant> = emptyList(),
    /** PR #10: 慢帧事件 (JankMonitor 上报 `jank_<deltaMs>ms`, >50ms 单独上报). */
    val jankEvents: List<PerfEvent.Instant> = emptyList(),
    /** PR #10: 慢帧百分比滚动窗口 (JankMonitor 1Hz 上报 `jank_pct_<n>`). */
    val recentJankPct: List<Int> = emptyList(),
    /** PR #10: 上报过的最慢单帧 (ms). */
    val slowestFrameMs: Int = 0,
    val totalBootMs: Long = 0L,
) {
  companion object {
    val EMPTY = PerfUiState()
  }
}
