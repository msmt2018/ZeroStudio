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
package com.itsaky.androidide.perf.monitor

import android.content.Context
import com.itsaky.androidide.BuildConfig

/**
 * Monitor 协调器 (PR #4/5).
 *
 * 统一启停 4 个 [PerfMonitor] 子类 (FrameRate / Memory / Gc / Anr),
 * 串到 IDEApplication.onCreate 末尾调一次.
 *
 * ## 启停条件
 *
 * - **Release build** ([BuildConfig.DEBUG] = false): 不启动 (0 overhead)
 * - **主进程**: 启动 4 个 monitor
 * - **:perf 进程**: 不启动 (Perf Console 自己没业务, 不需要监控)
 * - **:perf console 进程未运行** (PerfTracer 未 attach): 仍启动,
 *   PerfTracer 上报是 no-op, monitor 自身开销是 1Hz 4 个 syscalls, 可忽略
 *
 * ## API
 *
 * ```kotlin
 * // IDEApplication.onCreate 末尾:
 * MonitorCoordinator.start(this)
 *
 * // IDEApplication.onTerminate (或 AppExit 钩子):
 * MonitorCoordinator.stop()
 * ```
 *
 * @author android_zero
 */
object MonitorCoordinator {

  @Volatile private var started: Boolean = false

  private val monitors =
      listOf<PerfMonitor>(
          FrameRateMonitor(),
          MemoryMonitor(),
          GcMonitor(),
          AnrMonitor(),
      )

  /**
   * 启动所有 monitor.
   *
   * 幂等: 二次调用 no-op.
   *
   * @param context 任意 context (用于将来扩展需要 context 的 monitor)
   */
  @JvmStatic
  fun start(@Suppress("UNUSED_PARAMETER") context: Context) {
    if (started) return
    if (!BuildConfig.DEBUG) {
      // Release build: 完全不启动
      return
    }
    started = true
    monitors.forEach { it.start() }
  }

  /** 停止所有 monitor. */
  @JvmStatic
  fun stop() {
    if (!started) return
    started = false
    monitors.forEach { it.stop() }
  }

  /** 当前已启动的 monitor 列表 (UI 调试用). */
  @JvmStatic
  fun runningMonitors(): List<String> =
      if (started) monitors.map { it.name } else emptyList()
}
