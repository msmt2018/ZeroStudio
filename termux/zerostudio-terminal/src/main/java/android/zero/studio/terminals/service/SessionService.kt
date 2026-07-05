package com.termix.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.termix.resources.drawables
import com.termix.resources.strings
import com.termix.App.Companion.getTempDir
import com.termix.ui.activities.terminal.MainActivity
import com.termix.ui.screens.settings.Settings
import com.termix.ui.screens.terminal.MkSession
import com.termix.model.WorkingMode
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class SessionService : Service() {
    private val sessions = linkedMapOf<String, TerminalSession>()
    // Ordered list of session IDs for UI display
    val sessionOrder = mutableStateListOf<String>()
    // Map for storing workingMode per session
    val sessionList = mutableMapOf<String, Int>()
    // Observable map for terminal titles - triggers UI recomposition
    val sessionTitles = androidx.compose.runtime.mutableStateMapOf<String, String>()
    // Observable map for custom names - triggers UI recomposition
    val sessionCustomNames = androidx.compose.runtime.mutableStateMapOf<String, String>()
    var currentSession = mutableStateOf(Pair("main",com.termix.settings.Settings.working_Mode))

    /**
     * Resolve display title with priority chain:
     * 1. User custom name (if set)
     * 2. Terminal OSC title (if non-blank)
     * 3. Fallback to session ID ("main1", etc.)
     */
    fun getDisplayTitle(sessionId: String): String {
        return sessionCustomNames[sessionId]
            ?: sessionTitles[sessionId]?.takeIf { it.isNotBlank() }
            ?: getDefaultSessionName(sessionId)
    }

    private fun getDefaultSessionName(sessionId: String): String {
        val modeName = when (sessionList[sessionId]) {
            WorkingMode.ALPINE -> "alpine"
            WorkingMode.ANDROID -> "android"
            WorkingMode.ALPINE_ROOT -> "alpine (root)"
            WorkingMode.ARCH -> "arch"
            WorkingMode.ARCH_ROOT -> "arch (root)"
            else -> sessionId
        }
        return modeName
    }

    fun getWorkingMode(sessionId: String): Int? {
        return sessionList[sessionId]
    }

    fun updateTerminalTitle(sessionId: String, title: String) {
        sessionTitles[sessionId] = title
    }

    fun setCustomName(sessionId: String, name: String) {
        if (name.isBlank()) {
            sessionCustomNames.remove(sessionId)
            com.termix.settings.Settings.removeCustomSessionName(sessionId)
        } else {
            sessionCustomNames[sessionId] = name
            com.termix.settings.Settings.setCustomSessionName(sessionId, name)
        }
    }
    private fun cleanupSessionTemp(sessionId: String) {
        runCatching {
            val tmpDir = getTempDir().resolve(sessionId)
            if (tmpDir.exists()) {
                tmpDir.deleteRecursively()
            }
        }.onFailure { it.printStackTrace() }
    }

    inner class SessionBinder : Binder() {
        fun getService():SessionService{
            return this@SessionService
        }
        fun terminateAllSessions(){
            sessions.values.forEach{
                it.finishIfRunning()
            }
            sessions.keys.toList().forEach { cleanupSessionTemp(it) }
            sessions.clear()
            sessionOrder.clear()
            sessionList.clear()
            sessionTitles.clear()
            sessionCustomNames.clear()
            updateNotification()
        }
        fun createSession(id: String, client: TerminalSessionClient, activity: MainActivity,workingMode:Int): TerminalSession {
            return MkSession.createSession(activity, client, id, workingMode = workingMode).also {
                sessions[id] = it
                sessionOrder.add(id)
                sessionList[id] = workingMode
                sessionTitles[id] = ""
                // Restore persisted custom name if exists
                com.termix.settings.Settings.getCustomSessionName(id)?.let { name ->
                    sessionCustomNames[id] = name
                }
                updateNotification()
            }
        }
        fun getSession(id: String): TerminalSession? {
            return sessions[id]
        }
        fun terminateSession(id: String) {
            runCatching {
                //crash is here
                sessions[id]?.apply {
                    if (emulator != null){
                        sessions[id]?.finishIfRunning()
                    }
                }

                sessions.remove(id)
                sessionOrder.remove(id)
                sessionList.remove(id)
                sessionTitles.remove(id)
                sessionCustomNames.remove(id)
                com.termix.settings.Settings.removeCustomSessionName(id)
                cleanupSessionTemp(id)
                if (sessions.isEmpty()) {
                    stopSelf()
                } else {
                    updateNotification()
                }
            }.onFailure { it.printStackTrace() }

        }

        fun getSessionId(session: TerminalSession): String? {
            return sessions.entries.firstOrNull { it.value === session }?.key
        }
    }

    private val binder = SessionBinder()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        sessions.keys.toList().forEach { cleanupSessionTemp(it) }
        sessions.forEach { s -> s.value.finishIfRunning() }
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_EXIT" -> {
                sessions.keys.toList().forEach { cleanupSessionTemp(it) }
                sessions.forEach { s -> s.value.finishIfRunning() }
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = Intent(this, SessionService::class.java).apply {
            action = "ACTION_EXIT"
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Termix")
            .setContentText(getNotificationContentText())
            .setSmallIcon(drawables.terminal)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "EXIT",
                    exitPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private val CHANNEL_ID = "session_service_channel"

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Session Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for Terminal Service"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(1, notification)
    }

    private fun getNotificationContentText(): String {
        val count = sessions.size
        if (count == 1){
            return "1 session running"
        }
        return "$count sessions running"
    }
}
