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
 * 帧率 Monitor (PR #4/5).
 *
 * 用 [android.view.Choreographer] 监听 VSYNC, 计算 1 秒内帧数 = FPS.
 *
 * ## 实现细节
 *
 * - [Choreographer] 必须在有 Looper 的线程 (主线程) 创建并 callback
 * - 我们用 [Handler] 切到 main thread 注册 callback
 * - 每个 VSYNC 回调累计 frame 数, 每秒重置 + 上报
 *
 * ## 上报
 *
 * 用 `phase` 事件, name=`fps_<value>`, elapsed=帧数 (i.e. fps 值).
 * 接收方 (PR #5 UI) 解析 `fps_*` 前缀识别为帧率指标.
 *
 * @author android_zero
 */
class FrameRateMonitor : PerfMonitor(name = "FrameRate", intervalMs = 1000L) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val choreographer = android.view.Choreographer.getInstance()

  @Volatile private var frames: Int = 0

  override fun onStart() {
    mainHandler.post { postFrameCallback() }
  }

  override fun tick() {
    // 上报上一秒累计帧数
    val fps = frames
    frames = 0
    if (fps > 0) {
      PerfTracer.reportInstant("fps_$fps")
    }
  }

  private fun postFrameCallback() {
    // Choreographer.postFrameCallback 必须在 main thread 调
    // callback 自身也会跑在 main thread
    choreographer.postFrameCallback { _ ->
      frames++
      postFrameCallback()
    }
  }
}
