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

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Choreographer
import com.itsaky.androidide.perf.tracer.PerfTracer
import org.slf4j.LoggerFactory

/**
 * 冷启动时间统计 (Advanced PR / Commit 1).
 *
 * 跟踪从"进程启动"到"首帧渲染"的全链路, 拆 4 段:
 *
 * ```
 *   ┌──────────────┬─────────────┬──────────────┬────────────┐
 *   │ Process      │ App         │ App→Activity │ Act→Frame  │
 *   │ fork → IDE   │ onCreate    │ onCreate →   │ onResume →  │
 *   │ Application  │             │ onResume     │ first frame│
 *   │ .class load  │             │              │            │
 *   └──────────────┴─────────────┴──────────────┴────────────┘
 * ```
 *
 * 4 个上报点:
 * - [markAppStart]  — IDEApplication.onCreate 第一行
 * - [markAppReady]  — IDEApplication.onCreate 末尾 (Koin DI 完成)
 * - [markFirstActivity] — 首个 Activity onResume
 * - [markFirstFrame]    — 首个 onResume 之后的第一个 Choreographer frame
 *
 * 上报 event:
 * - `coldstart_proc2app_<ms>` — 进程启动 → App.onCreate 开始
 * - `coldstart_app_dur_<ms>` — App.onCreate 耗时
 * - `coldstart_app2act_<ms>` — App.onCreate 结束 → 首个 Activity onResume
 * - `coldstart_act2frame_<ms>` — 首个 Activity onResume → 首帧
 * - `coldstart_total_<ms>` — 进程启动 → 首帧 (整冷启动)
 *
 * 一次性. 标记过的不会重复发.
 *
 * @author android_zero
 */
object ColdStartTracker {

  private val log = LoggerFactory.getLogger(ColdStartTracker::class.java)

  private const val MARK_APP_START = "app_on_create_begin"
  private const val MARK_APP_READY = "app_on_create_end"
  private const val MARK_FIRST_ACTIVITY = "first_activity_resumed"
  private const val MARK_FIRST_FRAME = "first_frame"

  @Volatile private var appStartMarked = false
  @Volatile private var appReadyMarked = false
  @Volatile private var firstActivityMarked = false
  @Volatile private var firstFrameMarked = false

  /** 进程启动基线. Lazy — 第一次访问时记录. */
  private val processStartElapsedMs: Long = PerfTracer.processStartElapsedMs

  /**
   * 标记 IDEApplication.onCreate 开始.
   *
   * 在 [com.itsaky.androidide.app.IDEApplication.onCreate] 第一行调.
   * 二次调用 no-op.
   */
  @JvmStatic
  fun markAppStart() {
    if (appStartMarked) return
    appStartMarked = true
    val proc2app = SystemClock.elapsedRealtime() - processStartElapsedMs
    if (proc2app > 0) {
      PerfTracer.reportInstant("coldstart_proc2app_${proc2app}ms")
    }
  }

  /**
   * 标记 IDEApplication.onCreate 结束 (DI 完成, 业务代码可跑).
   *
   * 在 IDEApplication.onCreate 末尾 (PerfTracer.endBoot() 之前) 调.
   */
  @JvmStatic
  fun markAppReady() {
    if (appReadyMarked) return
    if (!appStartMarked) {
      // 没调 markAppStart, 算不出 app 耗时, 直接用 processStart 作参考
      log.warn("ColdStartTracker: markAppStart not called before markAppReady")
    }
    appReadyMarked = true
    val total = SystemClock.elapsedRealtime() - processStartElapsedMs
    if (total > 0) {
      PerfTracer.reportInstant("coldstart_app_dur_${total}ms")
    }
  }

  /**
   * 注册首个 Activity onResume 监听.
   *
   * 调用一次 (e.g. IDEApplication.onCreate 末尾), 之后自动等首 Activity onResume
   * 后调 [markFirstActivity], 并在下一个 Choreographer frame 调 [markFirstFrame].
   *
   * 内部用 Application.ActivityLifecycleCallbacks + Choreographer.postFrameCallback.
   */
  @JvmStatic
  fun registerFirstActivityTracker(application: Application) {
    application.registerActivityLifecycleCallbacks(
        object : Application.ActivityLifecycleCallbacks {
          private val choreographer = Choreographer.getInstance()
          private val handlerThread = HandlerThread("perf-coldstart").apply { start() }
          private val handler = Handler(handlerThread.looper)

          override fun
              onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

          override fun onActivityStarted(activity: Activity) {}

          override fun onActivityResumed(activity: Activity) {
            if (firstActivityMarked) return
            // 取消后续回调, 节省开销
            markFirstActivity()
            // 等待下一个 Choreographer frame
            choreographer.postFrameCallback {
              if (!firstFrameMarked) {
                markFirstFrame()
              }
            }
            // 兜底: 如果 Choreographer 不回调 (e.g. Activity 没有 view), 200ms 后强制算
            handler.postDelayed(
                {
                  if (!firstFrameMarked) {
                    log.warn("ColdStartTracker: Choreographer did not fire, force-marking first frame")
                    markFirstFrame()
                  }
                },
                200L,
            )
            // 取消后续 activity 回调, 减少开销
            application.unregisterActivityLifecycleCallbacks(this)
            handlerThread.quitSafely()
          }

          override fun onActivityPaused(activity: Activity) {}
          override fun onActivityStopped(activity: Activity) {}
          override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
          override fun onActivityDestroyed(activity: Activity) {}
        }
    )
  }

  private fun markFirstActivity() {
    if (firstActivityMarked) return
    firstActivityMarked = true
    val total = SystemClock.elapsedRealtime() - processStartElapsedMs
    if (total > 0) {
      PerfTracer.reportInstant("coldstart_app2act_${total}ms")
    }
  }

  private fun markFirstFrame() {
    if (firstFrameMarked) return
    firstFrameMarked = true
    val total = SystemClock.elapsedRealtime() - processStartElapsedMs
    if (total > 0) {
      PerfTracer.reportInstant("coldstart_act2frame_${total}ms")
      PerfTracer.reportInstant("coldstart_total_${total}ms")
    }
    log.info("Cold start total: {}ms (since process start)", total)
  }
}
