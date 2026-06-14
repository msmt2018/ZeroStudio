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
package com.itsaky.androidide.perf.server

import android.net.LocalServerSocket
import android.net.LocalSocket
import com.itsaky.androidide.perf.proto.PerfEvent
import com.itsaky.androidide.perf.proto.PerfSocketProtocol
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * :perf 进程 LocalServerSocket (PR #3/5).
 *
 * 与 [com.itsaky.androidide.perf.tracer.PerfClientSocket] (PR #2 实施)
 * 通过 Unix domain socket 通信. 本类是 server, 跑在 :perf 进程.
 *
 * ## 启动流程
 *
 * 1. [com.itsaky.androidide.perf.PerfApplication.onCreate] (PR #1) 在
 *    启动时创建本类实例, 调 [start].
 * 2. [start] 在 [executor] 后台线程:
 *    - 创建 [LocalServerSocket] 绑到 [socketPath]
 *    - 把 [socketPath] 写到 [pathFile] (供主进程
 *      [com.itsaky.androidide.perf.tracer.PerfTracer.tryAttach] 读)
 *    - 进入 [acceptLoop] 死循环
 * 3. 收到 client 连接 → 提交到 [executor] 的 [handleClient]:
 *    - 按行读, 每行调 [PerfSocketProtocol.parse]
 *    - 解析成功 → [collector.collect]
 *    - 解析失败 → log warn, 继续读下一行 (不中断)
 *    - 读到 EOF / IOException → 关闭 client socket
 *
 * ## 关闭流程
 *
 * - [stop] 关闭 server socket (让 accept() 抛异常退出循环)
 * - [executor] shutdown, 已连接的 client handler 自然结束
 * - [pathFile] 保留 (下次启动会被覆盖)
 *
 * @author android_zero
 */
internal class PerfServerSocket(
    private val socketPath: String,
    private val pathFile: java.io.File,
    private val collector: PhaseCollector,
) {

  private val log = LoggerFactory.getLogger(PerfServerSocket::class.java)

  private val executor = Executors.newCachedThreadPool { r ->
    Thread(r, "perf-server").apply { isDaemon = true }
  }

  @Volatile private var server: LocalServerSocket? = null

  @Volatile private var stopped: Boolean = false

  /**
   * 启动 server, 阻塞当前线程直到 [stop] 被调用.
   *
   * 设计: 业务线程 (PerfApplication.onCreate) 在后台线程调本方法, 不阻塞
   * main thread.
   */
  fun start() {
    if (stopped) {
      log.warn("PerfServerSocket already stopped, skip start")
      return
    }
    try {
      // 先清理上次残留的 socket 文件 (system 异常退出可能留下)
      java.io.File(socketPath).delete()

      val srv = LocalServerSocket(socketPath)
      server = srv

      // 把 socket 完整路径写到 pathFile, 主进程 PerfTracer.tryAttach 读这个
      pathFile.parentFile?.mkdirs()
      pathFile.writeText(socketPath)

      log.info("PerfServerSocket listening on {}", socketPath)
      acceptLoop(srv)
    } catch (e: IOException) {
      log.error("PerfServerSocket start failed", e)
      cleanup()
    }
  }

  /** 停止 server. */
  fun stop() {
    stopped = true
    cleanup()
    executor.shutdown()
    try {
      executor.awaitTermination(2, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }

  // -- private --

  private fun acceptLoop(srv: LocalServerSocket) {
    while (!stopped) {
      val client =
          try {
            srv.accept()
          } catch (e: IOException) {
            if (stopped) {
              log.info("PerfServerSocket accept loop exiting (server closed)")
            } else {
              log.warn("PerfServerSocket accept failed", e)
            }
            return
          }
      executor.submit { handleClient(client) }
    }
  }

  private fun handleClient(client: LocalSocket) {
    val remote = client.remoteSocketAddress?.toString() ?: "unknown"
    try {
      BufferedReader(InputStreamReader(client.inputStream, StandardCharsets.UTF_8)).use { reader ->
        while (!stopped) {
          val line =
              try {
                reader.readLine()
              } catch (e: IOException) {
                log.debug("PerfServerSocket client {} disconnected: {}", remote, e.message)
                return@use
              }
          if (line == null) {
            // EOF
            return@use
          }
          val event = PerfSocketProtocol.parse(line)
          if (event != null) {
            collector.collect(event)
          } else {
            log.warn("PerfServerSocket received unparseable line from {}: {}", remote, line)
          }
        }
      }
    } catch (e: Throwable) {
      log.warn("PerfServerSocket handleClient error for {}", remote, e)
    } finally {
      runCatching { client.close() }
    }
  }

  private fun cleanup() {
    runCatching { server?.close() }
    server = null
    // socket 路径文件保留 (PerfApplication onCreate 会重新写), 不删
  }
}
