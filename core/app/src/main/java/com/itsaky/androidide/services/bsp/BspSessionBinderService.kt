package com.itsaky.androidide.services.bsp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import com.itsaky.androidide.bsp.ipc.IBspSessionCallback
import com.itsaky.androidide.bsp.ipc.IBspSessionService
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.projects.internal.ProjectManagerImpl
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.bsp.ipc.BspWireProtocol
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory

/**
 * AIDL/Binder facade for high-throughput BSP orchestration.
 *
 * Design:
 * - Binder handles control-plane RPC and event subscription.
 * - BSP remains build semantic protocol.
 * - Large payloads are represented as references/tokens in JSON.
 */
class BspSessionBinderService : Service() {

  private val log = LoggerFactory.getLogger(BspSessionBinderService::class.java)
  private val callbacks = RemoteCallbackList<IBspSessionCallback>()
  private val executor = Executors.newSingleThreadExecutor()
  private val lastSyncAt = AtomicLong(0L)

  private val binder =
    object : IBspSessionService.Stub() {
      override fun registerCallback(callback: IBspSessionCallback?) {
        if (callback != null) callbacks.register(callback)
      }

      override fun unregisterCallback(callback: IBspSessionCallback?) {
        if (callback != null) callbacks.unregister(callback)
      }

      override fun initialize(rootUri: String?, optionsJson: String?): String {
        val service = buildService() ?: return error("BuildService unavailable")
        val uri = rootUri ?: return error("rootUri is required")
        executor.execute {
          runCatching {
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"initialize\",\"state\":\"start\"}")
            service.initializeProject(InitializeProjectParams(URI(uri).path)).get()
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"initialize\",\"state\":\"finish\"}")
          }.onFailure {
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"initialize\",\"state\":\"error\"}")
          }
        }
        return ok("initialize submitted")
      }

      override fun syncWorkspace(optionsJson: String?): String {
        val service = buildService() ?: return error("BuildService unavailable")
        executor.execute {
          runCatching {
            val now = System.currentTimeMillis()
            if (now - lastSyncAt.get() < 1200) {
              publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"sync\",\"state\":\"throttled\"}")
              return@runCatching
            }
            lastSyncAt.set(now)
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"sync\",\"state\":\"start\"}")
            val manager = ProjectManagerImpl.getInstance()
            val initParams = InitializeProjectParams(manager.projectDir.canonicalPath)
            service.initializeProject(initParams).get()
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"sync\",\"state\":\"finish\"}")
          }.onFailure {
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"sync\",\"state\":\"error\"}")
          }
        }
        return ok("sync submitted")
      }

      override fun compile(targetIdsJson: String?, optionsJson: String?): String {
        val service = buildService() ?: return error("BuildService unavailable")
        val tasks = parseTasks(optionsJson, "assembleDebug")
        executor.execute {
          runCatching {
            publish(BspWireProtocol.TOPIC_PROGRESS, "{\"phase\":\"compile\",\"state\":\"start\"}")
            service.executeTasks(*tasks.toTypedArray()).get()
            publish(BspWireProtocol.TOPIC_PROGRESS, "{\"phase\":\"compile\",\"state\":\"finish\"}")
          }.onFailure {
            publish(BspWireProtocol.TOPIC_PROGRESS, "{\"phase\":\"compile\",\"state\":\"error\"}")
          }
        }
        return ok("compile submitted")
      }

      override fun test(targetIdsJson: String?, optionsJson: String?): String {
        val service = buildService() ?: return error("BuildService unavailable")
        val tasks = parseTasks(optionsJson, "test")
        executor.execute {
          runCatching {
            publish(BspWireProtocol.TOPIC_PROGRESS, "{\"phase\":\"test\",\"state\":\"start\"}")
            service.executeTasks(*tasks.toTypedArray()).get()
            publish(BspWireProtocol.TOPIC_PROGRESS, "{\"phase\":\"test\",\"state\":\"finish\"}")
          }.onFailure {
            publish(BspWireProtocol.TOPIC_PROGRESS, "{\"phase\":\"test\",\"state\":\"error\"}")
          }
        }
        return ok("test submitted")
      }

      override fun cancel(taskId: String?): String {
        val service = buildService() ?: return error("BuildService unavailable")
        executor.execute {
          runCatching {
            service.cancelCurrentBuild().get()
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"cancel\",\"state\":\"finish\"}")
          }.onFailure {
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"cancel\",\"state\":\"error\"}")
          }
        }
        return ok("cancel submitted")
      }

      override fun shutdown(optionsJson: String?): String {
        val service = buildService() ?: return error("BuildService unavailable")
        executor.execute {
          runCatching {
            service.cleanupIdleResources("binder-shutdown").get()
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"shutdown\",\"state\":\"finish\"}")
          }.onFailure {
            publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"shutdown\",\"state\":\"error\"}")
          }
        }
        return ok("shutdown submitted")
      }
    }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onDestroy() {
    callbacks.kill()
    executor.shutdownNow()
    super.onDestroy()
  }

  private fun buildService(): BuildService? = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)

  private fun parseTasks(optionsJson: String?, fallback: String): List<String> {
    if (optionsJson.isNullOrBlank()) return listOf(fallback)
    val tasksPart = optionsJson.substringAfter("tasks=", "").substringBefore(";")
    return tasksPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(fallback) }
  }

  private fun publish(topic: String, payloadJson: String) {
    val n = callbacks.beginBroadcast()
    try {
      for (i in 0 until n) {
        runCatching { callbacks.getBroadcastItem(i).onEvent(topic, payloadJson) }
          .onFailure { log.warn("Failed to publish IPC callback", it) }
      }
    } finally {
      callbacks.finishBroadcast()
    }
  }

  private fun ok(message: String): String =
    "{\"status\":\"${BspWireProtocol.STATUS_OK}\",\"message\":\"$message\"}"

  private fun error(message: String): String =
    "{\"status\":\"${BspWireProtocol.STATUS_ERROR}\",\"message\":\"$message\"}"
}
