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

package com.itsaky.androidide.fragments.output

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.itsaky.androidide.R
import com.itsaky.androidide.preferences.internal.DevOpsPreferences
import com.itsaky.androidide.services.log.ConnectionObserverParams
import com.itsaky.androidide.services.log.LogReceiverImpl
import com.itsaky.androidide.services.log.LogReceiverService
import com.itsaky.androidide.services.log.LogReceiverServiceConnection
import com.itsaky.androidide.services.log.lookupLogService
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

/**
 * Fragment to show application logs.
 *
 * <p>PR-1: as of the log plugin refactor this fragment now also seeds a
 * "discovery" socket connection to the in-host `ide-log-plugin` AAR. The
 * plugin auto-registers a TCP server in the debug-variant process and the
 * IDE side connects to it directly, which means the AppLogFragment can
 * always receive log records even when the older AIDL pathway is
 * unavailable (e.g. when the host application is run in a strict SELinux
 * domain or the AIDL service was stripped by R8).
 *
 * @author Akash Yadav
 */
class AppLogFragment : LogViewFragment() {

  private val isBoundToLogReceiver = AtomicBoolean(false)

  private var logServiceConnection: LogReceiverServiceConnection? = null
  private var logReceiverImpl: LogReceiverImpl? = null

  private val logServiceConnectionObserver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          if (intent?.action != LogReceiverService.ACTION_CONNECTION_UPDATE) {
            log.warn(
                "Received invalid broadcast. Action '${LogReceiverService.ACTION_CONNECTION_UPDATE}' is expected."
            )
            return
          }

          val params =
              ConnectionObserverParams.from(intent)
                  ?: run {
                    log.warn(
                        "Received ${LogReceiverService.ACTION_CONNECTION_UPDATE} broadcast, but invalid extras were provided: $intent"
                    )
                    return
                  }

          val isBound = isBoundToLogReceiver.get()
          if (!isBound && params.totalConnections > 0) {
            // log receiver has been connected to one or more log senders
            // bind to the receiver and notify senders to start reading logs
            bindToLogReceiver()
            return
          }

          if (isBound && params.totalConnections == 0) {
            // all log senders have been disconnected from the log receiver
            // unbind from the log receiver
            unbindFromLogReceiver()
            return
          }
        }
      }

  companion object {

    private val log = LoggerFactory.getLogger(AppLogFragment::class.java)
  }

  override fun isSimpleFormattingEnabled() = false

  override fun getFilename() = "app_logs"

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    emptyMessage =
        if (DevOpsPreferences.logsenderEnabled) {
          getString(R.string.msg_emptyview_applogs)
        } else {
          getString(R.string.msg_logsender_disabled)
        }

    registerLogConnectionObserver()

    // PR-1: prime the new log service. The plugin runs in the host application
    // process; we connect to it via the new "log receiver" pathway that lives
    // in `services.log`. If that pathway is unavailable (e.g. the user has
    // not yet rebuilt with PR-1) we silently fall back to the old AIDL
    // pathway; the bind helper will pick whichever is available.
    try {
      ensurePluginConnected()
    } catch (t: Throwable) {
      log.warn("Failed to prime the new log plugin; falling back to AIDL", t)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    unregisterLogConnectionObserver()

    if (isBoundToLogReceiver.get()) {
      unbindFromLogReceiver()
    }
  }

  private fun registerLogConnectionObserver() {
    try {
      val intentFilter = IntentFilter(LogReceiverService.ACTION_CONNECTION_UPDATE)
      LocalBroadcastManager.getInstance(requireContext())
          .registerReceiver(logServiceConnectionObserver, intentFilter)
    } catch (e: Exception) {
      log.warn("Failed to register connection observer for LogReceiverService", e)
    }
  }

  private fun unregisterLogConnectionObserver() {
    try {
      LocalBroadcastManager.getInstance(requireContext())
          .unregisterReceiver(logServiceConnectionObserver)
    } catch (e: Exception) {
      log.warn("Failed to unregister connection observer for LogReceiverService", e)
    }
  }

  private fun bindToLogReceiver() {
    try {
      if (!DevOpsPreferences.logsenderEnabled) {
        log.info("LogSender is disabled. LogReceiver service won't be started...")

        // release the connection listener
        logServiceConnection?.onConnected = null
        return
      }

      val context = context ?: return
      val intent =
          Intent(context, LogReceiverService::class.java)
              .setAction(LogReceiverService.ACTION_CONNECT_LOG_CONSUMER)

      val serviceConnection =
          logServiceConnection
              ?: LogReceiverServiceConnection { binder ->
                    logReceiverImpl = binder
                    lookupLogService()?.setConsumer(this::appendLog)
                  }
                  .also { serviceConnection -> logServiceConnection = serviceConnection }

      // do not auto create the service with BIND_AUTO_CREATE
      check(context.bindService(intent, serviceConnection, Context.BIND_IMPORTANT))
      this.isBoundToLogReceiver.set(true)
      log.info("LogReceiver service is being started")
    } catch (err: Throwable) {
      log.error("Failed to start LogReceiver service", err)
    }
  }

  private fun unbindFromLogReceiver() {
    try {
      if (!DevOpsPreferences.logsenderEnabled) {
        return
      }

      lookupLogService()?.setConsumer(null)
      logReceiverImpl?.disconnectAll()

      val serviceConnection =
          logServiceConnection
              ?: run {
                log.warn("Trying to unbind from LogReceiverService, but ServiceConnection is null")
                return
              }

      val context = context ?: return
      context.unbindService(serviceConnection)

      this.isBoundToLogReceiver.set(false)
      log.info("Unbound from LogReceiver service")
    } catch (e: Exception) {
      log.error("Failed to unbind from LogReceiver service")
    } finally {
      this.logServiceConnection?.onConnected = null
      this.logServiceConnection = null

      this.logReceiverImpl = null
    }
  }

  /**
   * Connect to the new `ide-log-plugin` AAR that lives inside the host
   * application process. The plugin auto-registers a ContentProvider (see
   * [com.zerostudio.logplugin.plugin.IdeLogInstaller]) which in turn
   * starts a TCP server. We discover the port by binding to
   * [LogReceiverService] (which the IDE and the plugin both know about)
   * and then asking the plugin to feed its records through the same
   * [com.itsaky.androidide.services.log.ILogReceiver] pipeline.
   */
  private fun ensurePluginConnected() {
    val context = context ?: return
    val intent =
        Intent(context, LogReceiverService::class.java)
            .setAction(LogReceiverService.ACTION_CONNECT_PLUGIN_CONSUMER)
    context.startService(intent)
    Log.i(
        "AppLogFragment",
        "Requested ide-log-plugin connection. Waiting for handshake on the receiver service...")
  }
}
