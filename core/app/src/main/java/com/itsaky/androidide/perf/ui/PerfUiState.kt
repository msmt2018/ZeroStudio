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
    /** Advanced/ColdStart: 进程启动到 App.onCreate 开始 (ms). */
    val coldStartProc2AppMs: Long = 0L,
    /** Advanced/ColdStart: App.onCreate 耗时 (ms). */
    val coldStartAppDurMs: Long = 0L,
    /** Advanced/ColdStart: App.onCreate 结束到首 Activity onResume (ms). */
    val coldStartApp2ActMs: Long = 0L,
    /** Advanced/ColdStart: 首 Activity onResume 到首帧 (ms). */
    val coldStartAct2FrameMs: Long = 0L,
    /** Advanced/ColdStart: 进程启动到首帧 (ms), 即整冷启动. */
    val coldStartTotalMs: Long = 0L,
    /**
     * Advanced/Crash: 本次启动至今的 crash 事件列表 (按时间倒序).
     *
     * 来源: CrashHandler 上报 `crash_<ExceptionClass>` instant, 详见
     * [com.itsaky.androidide.perf.monitor.CrashHandler].
     */
    val crashEvents: List<PerfEvent.Instant> = emptyList(),
    val totalBootMs: Long = 0L,
) {
  companion object {
    val EMPTY = PerfUiState()
  }
}
