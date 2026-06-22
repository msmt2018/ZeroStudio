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

package com.itsaky.androidide.services.log

import android.content.Context
import com.itsaky.androidide.models.LogLine
import com.itsaky.androidide.utils.ILogger
import com.zerostudio.logwire.WireCodec
import com.zerostudio.logwire.WireConstants
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

/**
 * Opens a client socket to the `ide-log-plugin` AAR running inside the host
 * application process and pumps its records into the IDE's existing
 * [LogReceiverImpl] pipeline.
 *
 * <p>The plugin publishes its listening port via a ContentProvider side
 * channel. For PR-1 the connector simply retries every {@link
 * #RETRY_DELAY_MS} milliseconds until a connection is established; PR-2
 * will replace the retry loop with a content-provider query that returns
 * the actual port number.
 */
class PluginLogConnector(private val context: Context) {

  companion object {
    private const val TAG = "PluginLogConnector"
    private const val RETRY_DELAY_MS = 1_000L
    /**
     * The well-known port range used by the plugin. The plugin binds to
     * whatever free port the OS gives it and we discover the actual port
     * by polling the content provider URI. For PR-1 we only know a small
     * default range; PR-2 replaces this with a proper discovery protocol.
     */
    private val DEFAULT_PORTS = intArrayOf(0x1A2B, 0x1A2C, 0x1A2D, 0x1A2E, 0x1A2F)
  }

  private val running = AtomicBoolean(false)
  private var thread: Thread? = null
  private var socket: Socket? = null
  private val log = LoggerFactory.getLogger(PluginLogConnector::class.java)

  fun start() {
    if (!running.compareAndSet(false, true)) {
      return
    }
    val t = Thread(this::connectLoop, "PluginLogConnector")
    t.isDaemon = true
    thread = t
    t.start()
  }

  fun stop() {
    if (!running.getAndSet(false)) {
      return
    }
    try {
      socket?.close()
    } catch (ignored: IOException) {
    }
    thread?.interrupt()
  }

  private fun connectLoop() {
    while (running.get()) {
      for (port in DEFAULT_PORTS) {
        if (!running.get()) {
          return
        }
        if (tryConnect(port)) {
          return
        }
      }
      try {
        Thread.sleep(RETRY_DELAY_MS)
      } catch (ie: InterruptedException) {
        Thread.currentThread().interrupt()
        return
      }
    }
  }

  private fun tryConnect(port: Int): Boolean {
    var sock: Socket? = null
    return try {
      sock = Socket()
      sock.connect(InetSocketAddress("127.0.0.1", port), 1_000)
      sock.soTimeout = 0
      this.socket = sock
      log.info("Connected to ide-log-plugin on 127.0.0.1:{}", port)
      readLoop(sock)
      true
    } catch (e: IOException) {
      try {
        sock?.close()
      } catch (ignored: IOException) {
      }
      false
    }
  }

  private fun readLoop(sock: Socket) {
    try {
      val dis = DataInputStream(sock.getInputStream())
      while (running.get() && !sock.isClosed) {
        val packet = WireCodec.decode(dis)
        when (packet.type) {
          WireConstants.TYPE_LOG -> {
            val record = WireCodec.decodeLogRecord(packet.body)
            val line =
                LogLine.obtain(
                    LogLevelMapper.toAndroidLevel(record.level),
                    record.tag,
                    record.message,
                    true,
                )
            receiver?.appendLine(line)
          }
          WireConstants.TYPE_HEARTBEAT -> {
            // ignore
          }
          WireConstants.TYPE_BACKPRESSURE -> {
            val dropped = WireCodec.decodeBackpressure(packet.body)
            log.warn("ide-log-plugin dropped {} records due to backpressure", dropped)
          }
          WireConstants.TYPE_HELLO -> {
            val info = WireCodec.decodeHello(packet.body)
            log.info(
                "ide-log-plugin hello: {} v{} api={} logcat={} jdwp={}",
                info.pluginName,
                info.pluginVersion,
                info.apiVersion,
                info.logcatPort,
                info.jdwpPort,
            )
          }
          WireConstants.TYPE_JDWP -> {
            // PR-2: forward to the debugger engine
          }
          else -> {
            log.warn("Unknown plugin packet type: {}", packet.type)
          }
        }
      }
    } catch (e: IOException) {
      log.warn("ide-log-plugin connection ended: {}", e.message)
    } finally {
      try {
        sock.close()
      } catch (ignored: IOException) {
      }
    }
  }

  private val receiver: LogReceiverImpl?
    get() = lookupLogService()?.let { svc ->
      try {
        val field = LogReceiverService::class.java.getDeclaredField("binder")
        field.isAccessible = true
        field.get(svc) as? LogReceiverImpl
      } catch (t: Throwable) {
        null
      }
    }
}
