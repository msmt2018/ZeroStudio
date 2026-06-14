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

import android.os.Build
import com.itsaky.androidide.perf.tracer.PerfTracer

/**
 * GC Monitor (PR #4/5).
 *
 * 1Hz 采样 GC 计数与耗时, 检测 GC 频率.
 *
 * ## API 兼容性
 *
 * - API 26+ (Android 8.0 Oreo): [android.os.Debug.getRuntimeStats] 提供
 *   详细 GC 统计 (gc_count, gc_time_ms). 我们用之.
 * - API 24-25: 退化, 仅记录 [Runtime.totalMemory] / [freeMemory] 变化
 *
 * ## 上报
 *
 * - `gc_count` — 累计 GC 次数
 * - `gc_time_ms` — 累计 GC 耗时 (ms)
 * - `gc_count_delta` — 1 秒内 GC 次数 (近期频率)
 * - `gc_time_delta_ms` — 1 秒内 GC 耗时 (ms)
 *
 * UI (PR #5) 用 delta 算 GC 频率 (e.g. gc/s), 不直接显示累计.
 *
 * @author android_zero
 */
class GcMonitor : PerfMonitor(name = "Gc", intervalMs = 1000L) {

  private var lastCount: Long = 0L
  private var lastTimeMs: Long = 0L
  private var hasLast: Boolean = false

  override fun tick() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val stats = android.os.Debug.getRuntimeStats()
      // stats 是 Map<String, String>, key 形如 "art.gc.count" / "art.gc.time"
      val countStr = stats["art.gc.count"]
      val timeStr = stats["art.gc.time"]
      val count = countStr?.toLongOrNull() ?: 0L
      val timeMs = timeStr?.toLongOrNull() ?: 0L

      if (hasLast) {
        val countDelta = count - lastCount
        val timeDelta = timeMs - lastTimeMs
        PerfTracer.reportInstant("gc_count_delta_$countDelta")
        PerfTracer.reportInstant("gc_time_delta_ms_$timeDelta")
      }
      lastCount = count
      lastTimeMs = timeMs
      hasLast = true

      // 同时上报累计值 (UI 可在重启后比较)
      PerfTracer.reportInstant("gc_count_$count")
      PerfTracer.reportInstant("gc_time_ms_$timeMs")
    } else {
      // API 24-25 fallback: 仅上报 Java heap 使用量
      val runtime = Runtime.getRuntime()
      val usedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024
      PerfTracer.reportInstant("gc_java_used_kb_$usedKb")
    }
  }
}
