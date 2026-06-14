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
 * 慢帧 / Jank Monitor (PR #10/N).
 *
 * 用 [android.view.Choreographer] 监听 VSYNC, 测量每帧间隔 (`frameTimeNanos` delta),
 * 检测 > 16ms (60fps 阈值) 的卡顿帧.
 *
 * ## 与 FrameRateMonitor 的区别
 *
 * [FrameRateMonitor] 只统计 1Hz 帧数, 输出平均 FPS.
 * [JankMonitor] 逐帧检测, 关注**单帧延迟** (jank = 真实可感卡顿), 与 Android Vitals / Firebase
 * Performance / Systrace 的"慢帧"指标一致.
 *
 * ## 上报
 *
 * - **严重慢帧** (delta > 50ms): 单条 instant `jank_<deltaMs>ms`
 *   e.g. `jank_127ms` — 真实的可感知卡顿, UI 可单独列出
 * - **每秒钟聚合**: `jank_pct_<pct>`
 *   e.g. `jank_pct_15` — 上一秒 jank 帧占比 (%) . UI 用 sparkline 看趋势
 * - **最慢单帧 (每秒)**: `jank_max_<deltaMs>ms`
 *   e.g. `jank_max_342ms` — 上一秒最慢的一帧, UI 大字显示
 *
 * ## 阈值
 *
 * - 60fps 目标: 16.67ms / 帧
 * - 30fps 目标: 33.33ms / 帧
 * - "可感知卡顿": > 100ms (per Android Vitals)
 *
 * 我们用 16ms 作为 jank 阈值 (匹配 60fps), 50ms 作为"上报 instant" 阈值
 * (避免启动期或动画期间 1Hz 上百条 jank 事件刷爆 socket).
 *
 * @author android_zero
 */
class JankMonitor : PerfMonitor(name = "Jank", intervalMs = 1000L) {

  /** 60fps 帧间隔 (ns). 一帧 > 16.67ms 算 jank. */
  private val jankThresholdNs: Long = 16_666_666L

  /** 严重 jank 阈值 (ns), > 50ms 才单独上报 instant. */
  private val severeJankThresholdNs: Long = 50_000_000L

  private val mainHandler = Handler(Looper.getMainLooper())
  private val choreographer = android.view.Choreographer.getInstance()

  @Volatile private var lastFrameTimeNs: Long = 0L
  @Volatile private var totalFrames: Int = 0
  @Volatile private var jankFrames: Int = 0
  @Volatile private var maxJankMs: Int = 0

  override fun onStart() {
    mainHandler.post { postFrameCallback() }
  }

  override fun tick() {
    // 上报上一秒聚合
    val total = totalFrames
    val jank = jankFrames
    val maxMs = maxJankMs
    totalFrames = 0
    jankFrames = 0
    maxJankMs = 0

    if (total == 0) {
      // 没有帧, 不上报 (避免空闲 0% 噪音)
      return
    }

    val pct = (jank * 100) / total
    PerfTracer.reportInstant("jank_pct_$pct")
    if (maxMs > 0) {
      PerfTracer.reportInstant("jank_max_${maxMs}ms")
    }
  }

  private fun postFrameCallback() {
    choreographer.postFrameCallback { frameTimeNanos ->
      if (lastFrameTimeNs != 0L) {
        val deltaNs = frameTimeNanos - lastFrameTimeNs
        if (deltaNs > jankThresholdNs) {
          // 这一帧是 jank
          jankFrames++
          val deltaMs = (deltaNs / 1_000_000L).toInt()
          if (deltaMs > maxJankMs) {
            maxJankMs = deltaMs
          }
          if (deltaNs > severeJankThresholdNs) {
            // 严重 jank: 单独上报
            PerfTracer.reportInstant("jank_${deltaMs}ms")
          }
        }
        totalFrames++
      } else {
        // 第一帧, 没法算 delta, 只统计
        totalFrames++
      }
      lastFrameTimeNs = frameTimeNanos
      postFrameCallback()
    }
  }
}
