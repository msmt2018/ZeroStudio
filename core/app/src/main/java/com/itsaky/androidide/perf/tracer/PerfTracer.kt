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
package com.itsaky.androidide.perf.tracer

import android.content.Context
import android.os.SystemClock
import com.itsaky.androidide.BuildConfig
import com.itsaky.androidide.perf.PerfApplication
import java.io.File
import org.slf4j.LoggerFactory

/**
 * 主进程埋点门面 (PR #2/5).
 *
 * 调用方在 IDEApplication / Activity 关键 init 步骤包裹:
 *
 * ```kotlin
 * PerfTracer.trace("init_koin") {
 *   RikkaHubRuntime.ensureKoinStarted(this)
 * }
 * ```
 *
 * ## 降级策略 (核心设计)
 *
 * - **Release build** ([BuildConfig.DEBUG] = false): 全部方法被编译器
 *   消除, 0 overhead. 所有 `trace {}` 块变为裸的 `block()` 调用.
 * - **Debug build 但 :perf 进程未启动** (Perf Console 未从桌面启动):
 *   [tryAttach] 读 [PERF_SOCKET_PATH_FILE] 文件失败 → 关闭内部
 *   [PerfClientSocket], 后续 `trace` 调用变 no-op, 不抛异常, 不打 log 噪音.
 * - **Debug build 且 :perf 已启动**: 通过 LocalSocket 异步发送事件
 *   到 :perf 进程, 由 PR #3 的 [com.itsaky.androidide.perf.server.PerfServerSocket] 接收.
 *
 * 这种设计保证:
 * 1. **用户能控制**: 不开 Perf Console = 完全 0 overhead (只是常量判断)
 * 2. **不会 crash**: socket 失败 → 静默 no-op, 永远不会因为监控影响主 application
 * 3. **不会 NPE**: 静态 [trace] 调用方不用 null check
 *
 * @author android_zero
 */
object PerfTracer {

  private val log = LoggerFactory.getLogger(PerfTracer::class.java)

  /**
   * socket 客户端 (懒初始化, 失败后变 no-op).
   *
   * `volatile` 保证多线程可见性 (IDEApplication 入口在 main thread,
   * 后续 Activity onCreate 可能跨 thread).
   */
  @PublishedApi
  @Volatile
  internal var socket: PerfClientSocket? = null

  /** :perf 进程是否已 attach 成功. 仅用于日志, 不影响行为. */
  @Volatile
  private var attached: Boolean = false

  /**
   * 尝试连接 :perf 进程.
   *
   * 必须在主 application 入口 (e.g. [com.itsaky.androidide.app.IDEApplication.onCreate]
   * 第一行) 调用一次, 后续 `trace` 才会真正发出事件.
   *
   * - [BuildConfig.DEBUG] = false → no-op
   * - :perf 进程 → no-op (避免 :perf 进程自己 connect 自己, 死循环)
   * - socket 文件不存在 → no-op (Perf Console 未启动, 用户故意不开监控)
   * - socket connect 失败 → 静默失败, 后续 trace no-op
   */
  @JvmStatic
  fun tryAttach(context: Context) {
    if (!BuildConfig.DEBUG) {
      // Release build: 完全不初始化, 编译器会消除后续 trace 调用
      return
    }

    val processName = runCatching {
      // android.os.Process.myProcessName() requires API 28
      @Suppress("DEPRECATION")
      android.os.Process.myProcessName()
    }.getOrDefault("")

    if (processName.endsWith(":perf")) {
      // :perf 进程自己不要 connect 自己的 socket server
      log.info("PerfTracer running inside :perf process, skip attach")
      return
    }

    val socketPathFile = File(context.cacheDir, PerfApplication.PERF_SOCKET_PATH_FILE)
    if (!socketPathFile.exists()) {
      log.info("PerfTracer: socket path file not found, :perf console not started")
      return
    }

    val socketPath = runCatching { socketPathFile.readText().trim() }.getOrNull()
    if (socketPath.isNullOrEmpty()) {
      log.warn("PerfTracer: socket path file is empty")
      return
    }

    val client = PerfClientSocket(socketPath)
    val connected = client.tryConnect()
    if (!connected) {
      log.warn("PerfTracer: connect to :perf socket failed, will be no-op")
      // 不保留 client 引用, 后续 trace 自动 no-op
      return
    }

    socket = client
    attached = true
    log.info("PerfTracer: attached to :perf socket at {}", socketPath)
  }

  /**
   * 包裹一段代码, 测量其耗时并发送 phase 事件.
   *
   * **inline** 设计: 编译器会把 [block] 调用直接展开, 关闭时 ([attached] = false)
   * 整个 `if (!attached) return block()` 会被消除, 调用方在 Release build 看到的就是
   * 纯 `block()` 调用, 0 overhead.
   *
   * @param name phase 名称 (e.g. "init_koin")
   * @param block 待测量代码块
   * @return [block] 的返回值
   */
  @JvmStatic
  inline fun <T> trace(name: String, block: () -> T): T {
    if (!BuildConfig.DEBUG) return block() // Release build 0 overhead
    return _traceImpl(name, block)
  }

  /**
   * `trace` 的实际工作体. 拆出来是为了避开 inline 跨 class 访问的
   * @PublishedApi 链:
   *
   * - 原来 inline trace body 直接调 `s.sendPhase`, 编译期会递归检查
   *   sendPhase body 的所有访问 (broken / escape 都是 PerfClientSocket
   *   private), 这些都要加 @PublishedApi, 污染严重.
   * - 拆出 _traceImpl 后, inline trace body 只调 _traceImpl (PerfTracer
   *   自己的 internal fun), 不会跨 class, 也就不递归检查 _traceImpl
   *   body. _traceImpl 是 non-inline, 调 sendPhase 不递归 body 检查.
   * - @PublishedApi 让 _traceImpl 能被 inline trace 调.
   *
   * Release 0 overhead 由 inline trace 的 `if (!BuildConfig.DEBUG)`
   * 保证 — _traceImpl 在 Release build 永远不会被调用.
   */
  @PublishedApi
  internal fun <T> _traceImpl(name: String, block: () -> T): T {
    val s = socket ?: return block()
    val start = SystemClock.elapsedRealtime()
    return try {
      block()
    } finally {
      val elapsed = SystemClock.elapsedRealtime() - start
      s.sendPhase(name, elapsed)
    }
  }

  /**
   * 报告一个"非代码块"事件 (e.g. process start, first frame).
   *
   * Release build 完全消除. Debug 未 attach 时 no-op.
   */
  @JvmStatic
  fun reportInstant(name: String) {
    if (!BuildConfig.DEBUG) return
    socket?.sendInstant(name)
  }

  /**
   * 标记启动阶段结束.
   *
   * 调用方通常在 SplashActivity / MainActivity onCreate 末尾调一次,
   * 触发 server 端切换到"启动后采样"模式 (降低 1Hz→0.1Hz 之类).
   */
  @JvmStatic
  fun endBoot() {
    if (!BuildConfig.DEBUG) return
    socket?.sendEndBoot()
  }

  /**
   * 是否已 attach 到 :perf 进程.
   *
   * 主要用于 UI 显示 / 日志, 业务代码不应依赖.
   */
  @JvmStatic
  fun isAttached(): Boolean = attached
}
