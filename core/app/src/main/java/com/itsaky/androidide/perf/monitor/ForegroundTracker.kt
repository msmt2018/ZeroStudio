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

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.itsaky.androidide.perf.tracer.PerfTracer
import org.slf4j.LoggerFactory

/**
 * 前后台生命周期监听 (Advanced / Commit 3).
 *
 * 用 [ProcessLifecycleOwner] 监听整个 app process 的前台 / 后台切换.
 * 和 ActivityLifecycleCallbacks 不同, ProcessLifecycleOwner 把"所有
 * Activity 都 onStop 之后" 才算 background — 不会因为屏幕旋转或临时跳
 * 设置页误报.
 *
 * ## 上报
 *
 * - 切到前台: `lifecycle_foreground` instant
 * - 切到后台: `lifecycle_background` instant
 * - 每次切换时也上报累计前台时长: `lifecycle_fg_total_<ms>ms`,
 *   这样 ViewModel 解析一个 instant 就能拿到 totalForegroundMs.
 *
 * ## 当前是否前台
 *
 * [isInForeground] 给 ViewModel 读 (用于 UI badge), 不走 perf event 流.
 *
 * ## 一次性安装
 *
 * [install] 幂等. 内部持有 [ProcessLifecycleOwner.get] observer, 不再
 * 重复 add. 在主进程 IDEApplication.onCreate 末尾 (ColdStartTracker 注
 * 册之后) 调.
 *
 * @author android_zero
 */
object ForegroundTracker {

  private val log = LoggerFactory.getLogger(ForegroundTracker::class.java)

  @Volatile private var installed: Boolean = false

  /** 当前是否在前台. 初始 true (进程刚起就处于前台). */
  @Volatile var isInForeground: Boolean = true
    private set

  /** 进入后台的次数 (自启动以来). */
  @Volatile var backgroundCount: Int = 0
    private set

  /** 累计前台时长 (ms). 每次进入后台时把当前 segment 累加进来. */
  @Volatile var totalForegroundMs: Long = 0L
    private set

  /** 当前正在前台 segment 起始时间 (elapsedRealtime). 后台时此值无意义. */
  private var foregroundStartElapsedMs: Long = SystemClock.elapsedRealtime()

  /**
   * 安装前后台监听. 幂等.
   *
   * 必须在主进程调 (PerfTracer 那边 ProcessLifecycleOwner 在 :perf 进程
   * 不会有 UI activity, 状态不会变). 在 IDEApplication.onCreate
   * 末尾 (ColdStartTracker 注册之后) 调.
   */
  @JvmStatic
  fun install() {
    if (installed) return
    installed = true
    try {
      val owner = ProcessLifecycleOwner.get()
      owner.lifecycle.addObserver(
          object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
              onEnterForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
              onEnterBackground()
            }
          }
      )
    } catch (e: Throwable) {
      // ProcessLifecycleOwner 在某些测试 / 单元测试 mock 场景下不可用,
      // 静默失败, 不影响 IDE 主流程.
      log.warn("ForegroundTracker: install failed: {}", e.message)
      installed = false
    }
  }

  private fun onEnterForeground() {
    log.info("ForegroundTracker: app entered foreground")
    isInForeground = true
    foregroundStartElapsedMs = SystemClock.elapsedRealtime()
    PerfTracer.reportInstant("lifecycle_foreground")
    PerfTracer.reportInstant("lifecycle_fg_total_${totalForegroundMs}ms")
  }

  private fun onEnterBackground() {
    log.info("ForegroundTracker: app entered background")
    isInForeground = false
    backgroundCount += 1
    val segMs = SystemClock.elapsedRealtime() - foregroundStartElapsedMs
    if (segMs > 0) totalForegroundMs += segMs
    PerfTracer.reportInstant("lifecycle_background")
    PerfTracer.reportInstant("lifecycle_fg_total_${totalForegroundMs}ms")
  }
}
