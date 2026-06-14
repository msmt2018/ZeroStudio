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

import android.os.Debug
import com.itsaky.androidide.perf.tracer.PerfTracer

/**
 * 内存 Monitor (PR #4/5).
 *
 * 1Hz 采样 [Debug.MemoryInfo], 提取关键指标并通过 [PerfTracer] 上报.
 *
 * ## 指标
 *
 * - `mem_total_pss_kb` — 总 PSS (KB) 反映 app 实际占用物理内存
 * - `mem_dalvik_pss_kb` — Dalvik heap PSS
 * - `mem_native_pss_kb` — Native heap PSS
 * - `mem_views_kb` / `mem_app_kb` / `mem_other_kb` — 细分
 *
 * 上报用 `phase` 事件, name=`mem_<metric>_kb`, elapsed=值.
 *
 * ## 注意
 *
 * - PSS 包含与其他进程共享的页面 (按比例计), 比 RSS 更准确
 * - Debug.MemoryInfo API 26+ 提供更多字段, 我们用基础字段保证兼容性
 *
 * @author android_zero
 */
class MemoryMonitor : PerfMonitor(name = "Memory", intervalMs = 1000L) {

  override fun tick() {
    val info = Debug.MemoryInfo()
    Debug.getMemoryInfo(info)

    PerfTracer.reportInstant("mem_total_pss_kb_${info.totalPss}")
    PerfTracer.reportInstant("mem_dalvik_pss_kb_${info.dalvikPss}")
    PerfTracer.reportInstant("mem_native_pss_kb_${info.nativePss}")

    // Java heap 已分配 (KB)
    val runtime = Runtime.getRuntime()
    val javaUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024
    PerfTracer.reportInstant("mem_java_used_kb_$javaUsedKb")
  }
}
