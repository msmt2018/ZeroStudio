package com.itsaky.androidide.services.bsp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import com.itsaky.androidide.bsp.ipc.IBspSessionCallback
import com.itsaky.androidide.bsp.ipc.IBspSessionService
import com.itsaky.androidide.tooling.api.bsp.ipc.BspWireProtocol
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

  private val binder =
    object : IBspSessionService.Stub() {
      override fun registerCallback(callback: IBspSessionCallback?) {
        if (callback != null) callbacks.register(callback)
      }

      override fun unregisterCallback(callback: IBspSessionCallback?) {
        if (callback != null) callbacks.unregister(callback)
      }

      override fun initialize(rootUri: String?, optionsJson: String?): String {
        publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"initialize\"}")
        return ok("initialize queued")
      }

      override fun syncWorkspace(optionsJson: String?): String {
        publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"sync\"}")
        return ok("sync queued")
      }

      override fun compile(targetIdsJson: String?, optionsJson: String?): String {
        publish(BspWireProtocol.TOPIC_PROGRESS, "{\"phase\":\"compile\"}")
        return ok("compile queued")
      }

      override fun test(targetIdsJson: String?, optionsJson: String?): String {
        publish(BspWireProtocol.TOPIC_PROGRESS, "{\"phase\":\"test\"}")
        return ok("test queued")
      }

      override fun cancel(taskId: String?): String {
        publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"cancel\"}")
        return ok("cancel queued")
      }

      override fun shutdown(optionsJson: String?): String {
        publish(BspWireProtocol.TOPIC_LOG, "{\"phase\":\"shutdown\"}")
        return ok("shutdown queued")
      }
    }

  override fun onBind(intent: Intent?): IBinder = binder

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
}
