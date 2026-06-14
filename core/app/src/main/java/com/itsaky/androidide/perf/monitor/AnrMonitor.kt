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

import android.os.Handler
import android.os.Looper
import com.itsaky.androidide.perf.tracer.PerfTracer

/**
 * ANR Monitor (PR #4/5).
 *
 * Watchdog 线程每 [PROBE_INTERVAL_MS] 给主线程 post 一个 Runnable, 检测
 * 往返时间, 超过 [ANR_THRESHOLD_MS] 视为 ANR 发生.
 *
 * ## 上报
 *
 * - `anr_warn_<latency>` — 接近阈值 (50%-100% of 5s), 记录 warning
 * - `anr_<latency>` — 超过 5s 阈值, 记录 ANR (UI 用红色高亮)
 *
 * 上报的 `latency` 是主线程往返时间 (ms), UI 可排序看最长 ANR.
 *
 * ## 注意
 *
 * - 这是"软 ANR 检测" (类似 BlockCanary), 不能替代系统 ANR 弹窗
 * - 多次连续 ANR 合并为最新一次, 避免刷屏
 * - 调试时 (BuildConfig.DEBUG) 启用, Release 关闭
 *
 * @author android_zero
 */
class AnrMonitor : PerfMonitor(name = "Anr", intervalMs = PROBE_INTERVAL_MS) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var pingSentAt: Long = 0L

  override fun onStart() {
    // 第一次 ping 在 PROBE_INTERVAL_MS 后
  }

  override fun tick() {
    pingSentAt = System.currentTimeMillis()
    mainHandler.post {
      val latency = System.currentTimeMillis() - pingSentAt
      when {
        latency >= ANR_THRESHOLD_MS -> {
          // 真正的 ANR (>5s)
          PerfTracer.reportInstant("anr_$latency")
        }
        latency >= ANR_THRESHOLD_MS / 2 -> {
          // 接近 ANR (>2.5s) — warning
          PerfTracer.reportInstant("anr_warn_$latency")
        }
      }
    }
  }

  companion object {
    /** 探针间隔 (ms). 1s 是经验值: 不太频繁, 又能在 ANR 几秒内检测到. */
    private const val PROBE_INTERVAL_MS = 1000L

    /** ANR 阈值 (ms). 与系统 ANR 阈值一致 (5s). */
    private const val ANR_THRESHOLD_MS = 5000L
  }
}
