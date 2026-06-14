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

/**
 * Perf Monitor 抽象基类 (PR #4/5).
 *
 * 每个具体 monitor (FrameRate / Memory / Gc / Anr) 继承本类, 实现 [tick]:
 *
 * ```kotlin
 * class FrameRateMonitor : PerfMonitor("FrameRate", intervalMs = 1000L) {
 *   override fun tick() { /* 1Hz 采样 */ }
 * }
 * ```
 *
 * ## 线程模型
 *
 * - [start] 在调用方线程 (主进程) 启动一个 `ScheduledExecutorService`
 * - [tick] 跑在 executor 线程 (非 main), 业务代码自己确保线程安全
 * - [stop] shutdown executor, 等已提交任务完成
 *
 * ## 上报
 *
 * 业务 tick 内通过 [com.itsaky.androidide.perf.tracer.PerfTracer] 上报:
 *
 * ```kotlin
 * override fun tick() {
 *   val fps = computeFps()
 *   // 用 "phase" 事件, name=metric, elapsed=值 (kb/ms/fps)
 *   PerfTracer.reportInstant("fps_$fps")
 * }
 * ```
 *
 * @author android_zero
 */
abstract class PerfMonitor(
    val name: String,
    val intervalMs: Long = 1000L,
) {

  @Volatile private var started: Boolean = false

  protected val executor =
      java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "perf-monitor-$name").apply { isDaemon = true }
      }

  /**
   * 启动 monitor.
   *
   * 幂等: 二次调用 no-op.
   */
  fun start() {
    if (started) return
    started = true
    onStart()
    executor.scheduleWithFixedDelay(
        {
          try {
            tick()
          } catch (e: Throwable) {
            // 单次 tick 失败不能让 monitor 线程死
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("tick failed", e)
          }
        },
        intervalMs,
        intervalMs,
        java.util.concurrent.TimeUnit.MILLISECONDS,
    )
  }

  /**
   * 停止 monitor.
   *
   * 不再恢复; 业务代码应保证只调一次.
   */
  fun stop() {
    if (!started) return
    started = false
    executor.shutdown()
    try {
      executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
    onStop()
  }

  /** 子类可选: 启动时一次性初始化. */
  protected open fun onStart() {}

  /** 子类可选: 停止时清理. */
  protected open fun onStop() {}

  /** 子类必实现: 1Hz 采样 + 上报. */
  protected abstract fun tick()
}
