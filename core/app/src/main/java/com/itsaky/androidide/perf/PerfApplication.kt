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

import android.content.Context
import com.itsaky.androidide.perf.export.ThreadDumper
import com.itsaky.androidide.perf.server.PerfServerSocket
import com.itsaky.androidide.perf.server.PhaseCollector
import com.itsaky.androidide.perf.store.BootHistoryStore
import java.io.File
import java.util.concurrent.Executors
import org.slf4j.LoggerFactory

/**
 * :perf 进程的"逻辑 Application" (PR #3/5).
 *
 * ## 重要: 本类 **不是** 系统加载的 Application 类
 *
 * Android 一个 APK 只能有一个 Application 类, 当前 manifest 用的是
 * `com.itsaky.androidide.app.IDEApplication`. 系统在 fork `:perf` 进程时
 * 会加载 IDEApplication, [IDEApplication.onCreate] 通过 [isPerfProcess] 判断
 * 提前 return 后, **显式调 [init]** 把本类的逻辑跑起来.
 *
 * 这种设计的好处:
 * 1. 保持 :perf 进程所有初始化代码集中在一个文件 ([init]), 易维护
 * 2. 主进程 manifest 改动最小 (不需要 `tools:replace` 之类的 manifest merger 黑魔法)
 * 3. IDEApplication 主进程代码完全不变, 只是在 :perf 进程多了一个 init 入口
 *
 * ## 启动后做的事 (按 PR 顺序)
 *
 * - **PR #1**: 骨架 — 仅 log 占位
 * - **PR #3 (本 PR)**: 启动 [PerfServerSocket] + [PhaseCollector],
 *   把 socket 路径写到 [PERF_SOCKET_PATH_FILE] 供主进程连接
 * - **PR #4**: 启动 4 个 Monitor (FrameRate / Memory / Gc / Anr)
 *
 * ## 进程模型
 *
 * ```
 *  ┌──────────────────┐  LocalSocket (filesystem)  ┌──────────────────┐
 *  │  Main process    │ ────────────────────────▶ │  :perf process   │
 *  │  (IDEApplication)│   {"type":"phase",...}    │  PerfServerSocket│
 *  │  PerfTracer      │                           │  → PhaseCollector│
 *  │  PerfClientSocket│ ◀─────── (无返回) ──────── │  → UI (PR #5)    │
 *  └──────────────────┘                            └──────────────────┘
 * ```
 *
 * @author android_zero
 */
object PerfApplication {

  private val log = LoggerFactory.getLogger(PerfApplication::class.java)

  /**
   * 后台 executor, 用于跑 server accept loop.
   *
   * 单线程足够 (server.start 内部用 cached pool 处理多 client).
   * daemon = true 保证不阻止 JVM 退出.
   */
  private val serverExecutor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "perf-app-server").apply { isDaemon = true }
  }

  /**
   * 初始化 :perf 进程.
   *
   * 由 [com.itsaky.androidide.app.IDEApplication.onCreate] 在 `isPerfProcess()` 分支调.
   * 幂等: 二次调用 no-op.
   */
  @JvmStatic
  fun init(context: Context) {
    if (PhaseStore.isReady()) {
      log.info("PerfApplication.init already done, skip")
      return
    }
    log.info("PerfApplication.init (PR #3 启动 server + PhaseCollector)")

    val cacheDir = context.cacheDir
    val socketPath = File(cacheDir, PERF_SOCKET_PATH).absolutePath
    val pathFile = File(cacheDir, PERF_SOCKET_PATH_FILE)

    val collector = PhaseCollector()
    val server = PerfServerSocket(socketPath, pathFile, collector)

    // 启动期结束 (PerfTracer.endBoot) 时持久化 phase 列表到历史
    val historyStore = BootHistoryStore(context)
    collector.addEndBootListener { events, startElapsedMs ->
      historyStore.append(events, startElapsedMs)
    }

    // PR #8: ANR 触发时自动 dump 线程到 cacheDir/perf/dumps/
    collector.addAnrListener { name, latencyMs ->
      // 写文件放后台线程, 不阻塞 collect 主流程
      serverExecutor.submit {
        ThreadDumper.dumpToCache(cacheDir, reason = "anr_${latencyMs}ms")
      }
    }

    // 标记 server 状态供 UI (PR #5) 读
    PhaseStore.bind(collector, server)

    // server 启动放后台线程, 不阻塞 IDEApplication.onCreate
    serverExecutor.submit { server.start() }
  }

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
