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
import android.os.StrictMode
import com.itsaky.androidide.perf.tracer.PerfTracer
import org.slf4j.LoggerFactory

/**
 * StrictMode 违规上报 (PR #8/8).
 *
 * 监听 [StrictMode] 的 thread / vm 违规, 通过 [PerfTracer] 上报.
 * 上报格式:
 *
 * - `strict_thread_<class>_<hash>` — 线程违规 (e.g. `strict_thread_disk_read_abc123`)
 * - `strict_vm_<class>_<hash>` — VM 违规 (e.g. `strict_vm_leaked_closeable_xyz789`)
 *
 * `<class>` 是违规 class 简称 (e.g. `disk_read`, `leaked_closeable`).
 * `<hash>` 是违规信息 (e.g. stack trace / class name) 的 hash, 保证去重.
 *
 * ## 与现有 StrictMode setup 的关系
 *
 * PR #2 在 IDEApplication.onCreate 设了 `StrictMode.setVmPolicy` + `penaltyLog()`,
 * 即违规只 logcat, 不上报. PR #8 加 listener 后, log 同时上报.
 *
 * ## API
 *
 * ```kotlin
 * StrictModeViolationMonitor.install()  // 在 IDEApplication.onCreate 调一次
 * ```
 *
 * @author android_zero
 */
object StrictModeViolationMonitor {

  private val log = LoggerFactory.getLogger(StrictModeViolationMonitor::class.java)

  @Volatile private var installed: Boolean = false

  /**
   * 注册 StrictMode 违规监听.
   *
   * 幂等. 仅 BuildConfig.DEBUG 启用, Release 不上报.
   *
   * @param sampleEveryNth 抽样率: 1 = 全部上报, 10 = 每 10 次违规上报 1 次.
   *   StrictMode 违规在启动期可能高频 (e.g. 200+ 磁盘读违规), 抽样可避免
   *   刷爆 socket 队列.
   */
  @JvmStatic
  fun install(sampleEveryNth: Int = 5) {
    if (installed) return
    if (!com.itsaky.androidide.BuildConfig.DEBUG) {
      // Release: 不安装, 0 overhead
      return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      // API < 26 无 OnThreadViolationListener, 降级
      log.info("StrictModeViolationMonitor: API < 26, skip install")
      return
    }

    installed = true

    // 1. Thread 违规
    val threadPolicy = StrictMode.getThreadPolicy()
    val newThreadPolicy =
        StrictMode.ThreadPolicy.Builder(threadPolicy)
            .onThreadViolation { _, violation ->
              reportViolation("thread", violation, sampleEveryNth)
            }
            .build()
    StrictMode.setThreadPolicy(newThreadPolicy)

    // 2. VM 违规
    val vmPolicy = StrictMode.getVmPolicy()
    val newVmPolicy =
        StrictMode.VmPolicy.Builder(vmPolicy)
            .onVmViolation { violation -> reportViolation("vm", violation, sampleEveryNth) }
            .build()
    StrictMode.setVmPolicy(newVmPolicy)

    log.info("StrictModeViolationMonitor installed (sample rate: 1/{})", sampleEveryNth)
  }

  private var counter: Int = 0

  private fun reportViolation(scope: String, violation: Throwable, sampleEveryNth: Int) {
    counter++
    if (counter % sampleEveryNth != 0) {
      // 抽样跳过
      return
    }

    val className = violation::class.java.simpleName
    val shortName = className.removePrefix("StrictMode\$").removeSuffix("Violation")
    val hash = stableHash(violation.stackTrace.take(3).joinToString("|") { it.toString() })
    PerfTracer.reportInstant("strict_${scope}_${shortName}_${hash}")
  }

  /**
   * 稳定 hash: 给定相同输入输出相同 32-bit hex.
   *
   * 不需要密码学强度, 只需去重. 用 [String.hashCode] 即可.
   */
  private fun stableHash(s: String): String =
      s.hashCode().toUInt().toString(16).padStart(8, '0')
}
