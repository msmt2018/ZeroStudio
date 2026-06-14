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
package com.itsaky.androidide.perf

import android.app.Application
import org.slf4j.LoggerFactory

/**
 * :perf 进程的 [Application].
 *
 * 跑在独立进程 `android:process=":perf"`, 不会被主 application
 * (`com.itsaky.androidide`) 任何初始化阻塞. 主 application 启动
 * 崩溃时, 此 Application 仍可用, 性能监控 UI 仍能显示.
 *
 * ## 启动后做的事 (按 PR 顺序)
 *
 * - PR #1 (本 PR): 仅 log, 不启动任何后台任务
 * - PR #3: 创建 `LocalServerSocket` ([PERF_SOCKET_PATH]) 等主进程 connect
 * - PR #4: 启动 4 个 Monitor (FrameRate / Memory / Gc / Anr)
 *
 * 主进程的 `PerfTracer.tryAttach(...)` 通过读 [PERF_SOCKET_PATH_FILE] 文件
 * 获得 socket 完整路径, 实现两进程解耦.
 *
 * @author android_zero
 */
class PerfApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    log.info("PerfApplication onCreate (PR #1 骨架, 无 socket server / monitor)")
  }

  companion object {
    private val log = LoggerFactory.getLogger(PerfApplication::class.java)

    /**
     * LocalServerSocket 路径 (相对 app cacheDir).
     *
     * 放在 app cacheDir 而不是 filesDir, 因为:
     * - cacheDir 在低存储时可被系统清理, 但 perf.sock 是运行时 socket,
     *   app 退出后不需要保留, 清理无害
     * - cacheDir 路径短, 减少 socket path 长度 (Linux AF_UNIX path ≤ 108 chars)
     * - 与 TermuxAmSocketServer 路径隔离, 避免冲突
     */
    const val PERF_SOCKET_PATH = "perf/perf.sock"

    /**
     * Socket 路径信息文件.
     *
     * 主进程的 [com.itsaky.androidide.perf.tracer.PerfTracer] 通过
     * ContentProvider / 直读此文件得到 socket 完整路径. 替代硬编码路径,
     * 让 :perf 与主进程解耦.
     */
    const val PERF_SOCKET_PATH_FILE = "perf/socket-path.txt"
  }
}

