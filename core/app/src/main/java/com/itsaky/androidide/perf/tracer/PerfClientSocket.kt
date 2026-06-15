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

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * :perf socket 客户端 (PR #2/5).
 *
 * 与 [com.itsaky.androidide.perf.server.PerfServerSocket] (PR #3 实施)
 * 通过 Unix domain socket (LocalSocket) 通信. 主进程是 client, :perf 进程是 server.
 *
 * ## 协议 (PR #2 暂定, PR #3 协议层会细化)
 *
 * 每条事件一行, UTF-8 JSON:
 *
 * ```
 * {"type":"phase","name":"koin_start","elapsed":45}\n
 * {"type":"instant","name":"first_frame"}\n
 * {"type":"end_boot"}\n
 * ```
 *
 * - 字段顺序不重要, server 容错解析
 * - `\n` 是一条事件的结束符
 * - UTF-8 编码, name 不能含换行 / 引号 (调用方保证)
 *
 * ## 线程模型
 *
 * - 业务线程 (main / IO / Binder) 调 `sendPhase` → 写入无锁 [LinkedBlockingQueue]
 * - 单独的 writer 线程 ([executor]) 从队列取事件 → 序列化 → 写入 [OutputStream]
 * - 写失败 (server 关闭 / IO 异常) → 标记 [broken] = true, 后续 sendXxx 立即 no-op
 *
 * 这种设计保证:
 * 1. **业务线程 0 阻塞**: 写 socket 是后台线程的事
 * 2. **顺序保证**: FIFO 队列保证事件按发出顺序被 server 收到
 * 3. **失败隔离**: socket 写崩不会让主 application 挂
 *
 * @author android_zero
 */
internal class PerfClientSocket(private val socketPath: String) {

  private val log = LoggerFactory.getLogger(PerfClientSocket::class.java)

  private val queue = LinkedBlockingQueue<String>(QUEUE_CAPACITY)
  private val executor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "perf-tracer-writer").apply { isDaemon = true }
  }

  @Volatile private var socket: LocalSocket? = null
  @Volatile private var out: OutputStream? = null

  /** 是否已断连 (写失败一次即标记, 后续 sendXxx 全部 no-op). */
  @Volatile private var broken: Boolean = false

  /**
   * 尝试 connect 到 :perf server.
   *
   * @return true 表示 connect + 启动 writer 成功
   */
  fun tryConnect(): Boolean {
    return try {
      val sock = LocalSocket()
      sock.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
      val output = sock.outputStream
      socket = sock
      out = output
      executor.submit(::writerLoop)
      true
    } catch (e: IOException) {
      log.warn("PerfClientSocket connect failed: {}", e.message)
      cleanup()
      false
    }
  }

  /**
   * 异步发送 phase 事件 (有耗时). `inline` (e.g. [PerfTracer.trace]) 调,
   * 必须 `@PublishedApi internal` 才能 inline 访问.
   */
  @PublishedApi
  internal fun sendPhase(name: String, elapsedMs: Long) {
    if (broken) return
    enqueue("""{"type":"phase","name":"${escape(name)}","elapsed":$elapsedMs}""")
  }

  /** 异步发送 instant 事件 (无耗时). `inline` 调用, 同上. */
  @PublishedApi
  internal fun sendInstant(name: String) {
    if (broken) return
    enqueue("""{"type":"instant","name":"${escape(name)}"}""")
  }

  /** 异步发送 end_boot 标记. `inline` 调用, 同上. */
  @PublishedApi
  internal fun sendEndBoot() {
    if (broken) return
    enqueue("""{"type":"end_boot"}""")
  }

  /** 主动关闭 (app exit 时调用). */
  fun close() {
    broken = true
    executor.shutdown()
    cleanup()
  }

  // -- private --
  //
  // 注: enqueue 必须是 @PublishedApi internal, 因为上面的
  // @PublishedApi internal fun sendPhase/sendInstant/sendEndBoot
  // 被 inline (e.g. PerfTracer.trace) 调, inline 展开时会把 enqueue
  // 调用 inline 到 caller 字节码, 但 enqueue 本身是 private to this
  // class — caller 编译期看不到 private, 报
  // "inline function cannot access non-public-API function".
  // 加 @PublishedApi internal 后, inline 知道怎么访问.

  @PublishedApi
  internal fun enqueue(line: String) {
    if (broken) return
    val ok = queue.offer(line)
    if (!ok) {
      // 队列满 → 标记 broken, 后续全部丢弃, 避免主线程被阻塞
      log.warn("PerfClientSocket queue full ({}), dropping events", QUEUE_CAPACITY)
      broken = true
    }
  }

  private fun writerLoop() {
    try {
      while (!broken && !Thread.currentThread().isInterrupted) {
        val line = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: continue
        writeLine(line)
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    } catch (e: Throwable) {
      log.warn("PerfClientSocket writer loop crashed: {}", e.message)
    } finally {
      cleanup()
      broken = true
    }
  }

  private fun writeLine(line: String) {
    val output = out ?: run {
      broken = true
      return
    }
    try {
      output.write(line.toByteArray(Charsets.UTF_8))
      output.write(LINE_DELIMITER)
      output.flush()
    } catch (e: IOException) {
      log.warn("PerfClientSocket write failed: {}", e.message)
      broken = true
      cleanup()
    }
  }

  private fun cleanup() {
    runCatching { out?.close() }
    runCatching { socket?.close() }
    out = null
    socket = null
  }

  private fun escape(s: String): String =
      s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

  companion object {
    private val LINE_DELIMITER = byteArrayOf('\n'.code.toByte())
    private const val QUEUE_CAPACITY = 1024
    private const val POLL_TIMEOUT_MS = 50L
  }
}
