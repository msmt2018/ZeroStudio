/*
 *  ZeroStudio IDE - Shizuku 无线调试配对 Service
 *
 *  在 IDE 内部实现完整的 Shizuku 无线调试自配对工作流 (self-pairing),
 *  无需跳转 Shizuku Manager 即可完成:
 *    1. 通过 NsdManager (AdbMdns) 发现本机 adb-tls-pairing 服务
 *    2. 弹出带 RemoteInput 输入框的通知, 让用户输入 6 位配对码 (官方 Shizuku UX)
 *    3. 调用 AdbConnectionManager.pair() 完成配对
 *    4. 发现 adb-tls-connect 服务, 调用 connect() 连接本地 ADB
 *    5. 通过 openStream() 执行 Shizuku server 启动脚本
 *    6. 调用 rikka.shizuku.Shizuku.pingBinder() 验证 server 是否启动成功
 *    7. 通过广播把每一步状态同步给 UI
 *
 *  配对目标永远是 127.0.0.1 (本机自配对, 用于本地 Shizuku)。
 *
 *  注意: 运行时需要在 AndroidManifest.xml 注册本 Service 并声明
 *  foregroundServiceType="connectedDevice", 否则 startForegroundService 会失败。
 *  本文件只保证可编译, manifest 注册由集成方按需添加。
 */

package com.itsaky.androidide.debugger.shizuku

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import android.zero.studio.adb.AbsAdbConnectionManager
import android.zero.studio.adb.AdbStream
import android.zero.studio.adb.android.AdbMdns
import android.zero.studio.shell.common.data.adb.AdbConnectionManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 前台 Service: 实现 Shizuku 无线调试自配对 + server 启动的完整工作流。
 *
 * 通过 Intent action 驱动:
 * - [ACTION_START]        开始配对工作流 (mDNS 发现 + 通知)
 * - [ACTION_SUBMIT_CODE]  用户通过 RemoteInput 提交了 6 位配对码
 * - [ACTION_CANCEL]       取消配对
 * - [ACTION_START_SERVER] 直接启动 Shizuku server (假定 ADB 已连接)
 *
 * 无线调试需要 Android 11+ (API 30)。
 */
@RequiresApi(Build.VERSION_CODES.R)
class ShizukuPairingService : Service() {

    companion object {
        private const val TAG = "ShizukuPairingService"

        /** 开始配对工作流。 */
        const val ACTION_START = "com.itsaky.androidide.action.SHIZUKU_START_PAIRING"
        /** 用户通过 RemoteInput 提交配对码。 */
        const val ACTION_SUBMIT_CODE = "com.itsaky.androidide.action.SHIZUKU_SUBMIT_CODE"
        /** 取消配对。 */
        const val ACTION_CANCEL = "com.itsaky.androidide.action.SHIZUKU_CANCEL"
        /** 直接启动 Shizuku server (假定 ADB 已连接)。 */
        const val ACTION_START_SERVER = "com.itsaky.androidide.action.SHIZUKU_START_SERVER"

        /** 广播 action: 配对/启动流程状态变化。 */
        const val ACTION_PAIRING_STATE = "com.itsaky.androidide.SHIZUKU_PAIRING_STATE"
        /** 状态值, 取值为 [State] 的 name。 */
        const val EXTRA_STATE = "state"
        /** 附带的人类可读消息。 */
        const val EXTRA_MESSAGE = "message"

        /** RemoteInput 的 key (6 位配对码)。 */
        private const val KEY_PAIRING_CODE = "pairing_code"

        private const val CHANNEL_ID = "shizuku_pairing"
        private const val CHANNEL_NAME = "Shizuku Pairing"
        private const val NOTIFICATION_ID = 0x5B1C

        /** 自配对主机永远是本机。 */
        private const val HOST = "127.0.0.1"

        /** Shizuku server 启动脚本路径 (Shizuku v11.2.0+)。 */
        private const val START_SCRIPT_DESTINATION =
            "shell:sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"

        /** 配对成功后等待 connect 服务出现的最长时间 (毫秒)。 */
        private const val CONNECT_DISCOVERY_TIMEOUT_MS = 10_000L
        /** ADB connect 后让连接稳定下来的等待时间 (毫秒)。 */
        private const val STABILIZE_DELAY_MS = 2_000L
        /** 启动脚本执行后等待 binder 上线的等待时间 (毫秒)。 */
        private const val BINDER_CHECK_DELAY_MS = 3_000L

        /** connect 服务未通过 mDNS 发现时, 依次尝试的常见端口。 */
        private val CONNECT_FALLBACK_PORTS = intArrayOf(5555, 37373, 42069)

        /**
         * 启动配对工作流 (mDNS 发现 + 通知)。
         */
        fun start(context: Context) {
            val intent = Intent(context, ShizukuPairingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * 停止配对工作流并销毁 Service。
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, ShizukuPairingService::class.java))
        }

        /**
         * 直接启动 Shizuku server (假定 ADB 已连接, 例如已经配对过)。
         */
        fun startServer(context: Context) {
            val intent = Intent(context, ShizukuPairingService::class.java).apply {
                action = ACTION_START_SERVER
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    /** 配对/启动流程的状态机, 会通过 [ACTION_PAIRING_STATE] 广播给 UI。 */
    enum class State {
        SEARCHING,
        FOUND,
        PAIRING,
        CONNECTING,
        STARTING,
        SUCCESS,
        FAILED,
    }

    private lateinit var notificationManager: NotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // mDNS 发现 (pairing + connect 同时发现)
    private var pairingMdns: AdbMdns? = null
    private var connectMdns: AdbMdns? = null

    // 发现到的端口
    @Volatile private var discoveredPairingPort: Int? = null
    @Volatile private var discoveredConnectPort: Int? = null
    /** 配对成功后等待 connect 服务时使用的 latch。 */
    @Volatile private var connectLatch: CountDownLatch? = null

    // 流程状态
    @Volatile private var isProcessing = false
    @Volatile private var isPairingDone = false
    @Volatile private var isServerStarted = false
    @Volatile private var foregroundStarted = false

    private var currentJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action=$action")
        try {
            when (action) {
                ACTION_SUBMIT_CODE -> {
                    ensureForeground(buildPairingInProgressNotification())
                    handleSubmitCode(intent)
                }
                ACTION_CANCEL -> {
                    broadcastState(State.FAILED, "Cancelled")
                    cleanup()
                    stopSelf()
                }
                ACTION_START_SERVER -> {
                    resetState()
                    ensureForeground(buildStartingServerNotification())
                    broadcastState(State.STARTING, "Starting Shizuku server")
                    startServerWorkflow()
                }
                ACTION_START, null -> {
                    resetState()
                    ensureForeground(buildSearchingNotification())
                    broadcastState(State.SEARCHING, "Searching for pairing service")
                    startPairingDiscovery()
                }
                else -> {
                    Log.w(TAG, "Unknown action: $action")
                    ensureForeground(buildSearchingNotification())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling action $action", e)
            handleFailure("Error: ${e.message}")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        cleanup()
        serviceScope.cancel()
    }

    // region 通知 channel

    private fun createChannelIfNeeded() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shizuku wireless debugging pairing"
            // 高优先级但不发声, 避免打扰用户
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    // endregion

    // region 前台启动

    /**
     * 确保 Service 处于前台状态; Android 12+ 可能抛 ForegroundServiceStartNotAllowedException。
     */
    private fun ensureForeground(notification: Notification) {
        if (foregroundStarted) {
            // 已经是前台了, 只更新通知
            notificationManager.notify(NOTIFICATION_ID, notification)
            return
        }
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            }
            startForeground(NOTIFICATION_ID, notification, type)
            foregroundStarted = true
        } catch (e: Exception) {
            // Android 12+ 后台启动可能抛 ForegroundServiceStartNotAllowedException
            // (它继承 IllegalStateException, 已被本 catch 覆盖, 无需显式引用 API 31 类)。
            Log.e(TAG, "startForeground failed: ${e.javaClass.simpleName}: ${e.message}")
            broadcastState(State.FAILED, "Cannot start foreground service: ${e.message}")
            stopSelf()
        }
    }

    // endregion

    // region 通知构建

    private fun baseNotification(
        title: String,
        text: String,
        ongoing: Boolean = true,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

    /** "正在搜索配对服务" 通知。 */
    private fun buildSearchingNotification(): Notification {
        return baseNotification(
            title = "Shizuku Pairing",
            text = "Searching for wireless debugging pairing service…",
        ).build()
    }

    /** "请输入配对码" 通知, 带 RemoteInput 输入框。 */
    private fun buildEnterCodeNotification(): Notification {
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val submitIntent = Intent(this, ShizukuPairingService::class.java).apply {
            action = ACTION_SUBMIT_CODE
        }
        val submitPi = PendingIntent.getService(
            this, 0, submitIntent, pendingFlags,
        )
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("Enter 6-digit pairing code")
            .build()
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.stat_sys_download,
            "Enter pairing code",
            submitPi,
        ).addRemoteInput(remoteInput).build()

        return baseNotification(
            title = "Shizuku Pairing",
            text = "Pairing service found. Tap to enter the 6-digit code.",
        )
            .addAction(action)
            .build()
    }

    /** "正在配对" 通知。 */
    private fun buildPairingInProgressNotification(): Notification {
        return baseNotification(
            title = "Shizuku Pairing",
            text = "Pairing in progress…",
        ).build()
    }

    /** "正在连接 / 正在启动 server" 通知。 */
    private fun buildStartingServerNotification(): Notification {
        return baseNotification(
            title = "Shizuku Pairing",
            text = "Connecting ADB and starting Shizuku server…",
        ).build()
    }

    /** 成功通知。 */
    private fun buildSuccessNotification(): Notification {
        return baseNotification(
            title = "Shizuku Pairing",
            text = "Shizuku server started successfully.",
            ongoing = false,
        )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .build()
    }

    /** 失败通知。 */
    private fun buildFailureNotification(message: String): Notification {
        return baseNotification(
            title = "Shizuku Pairing",
            text = "Failed: $message",
            ongoing = false,
        )
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .build()
    }

    private fun postNotification(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // endregion

    // region mDNS 发现

    /**
     * 启动 mDNS 发现: 同时搜索 pairing 和 connect 服务。
     * pairing 服务出现 → 更新通知显示 RemoteInput 输入框。
     * connect 服务出现 → 记录端口, 供配对成功后连接使用。
     */
    private fun startPairingDiscovery() {
        Log.d(TAG, "Starting mDNS discovery for pairing + connect services")

        pairingMdns = AdbMdns(
            this,
            AdbMdns.SERVICE_TYPE_TLS_PAIRING,
        ) { hostAddress, port, serviceName ->
            if (port > 0) {
                if (isPairingDone || isProcessing) {
                    Log.d(TAG, "Pairing service found but pairing done/in progress, ignoring")
                    return@AdbMdns
                }
                Log.d(TAG, "Pairing service found: port=$port serviceName=$serviceName")
                discoveredPairingPort = port
                broadcastState(State.FOUND, "Pairing service found")
                postNotification(buildEnterCodeNotification())
            } else if (port == -1) {
                // pairing service lost
                if (!isPairingDone && !isProcessing) {
                    Log.d(TAG, "Pairing service lost")
                    discoveredPairingPort = null
                    broadcastState(State.SEARCHING, "Searching for pairing service")
                    postNotification(buildSearchingNotification())
                }
            }
        }

        connectMdns = AdbMdns(
            this,
            AdbMdns.SERVICE_TYPE_TLS_CONNECT,
        ) { hostAddress, port, serviceName ->
            if (port > 0) {
                Log.d(TAG, "Connect service found: port=$port serviceName=$serviceName")
                discoveredConnectPort = port
                connectLatch?.countDown()
            }
        }

        pairingMdns?.start()
        connectMdns?.start()
    }

    // endregion

    // region 提交配对码

    private fun handleSubmitCode(intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val code = remoteInput?.getCharSequence(KEY_PAIRING_CODE)?.toString()?.trim()

        if (isProcessing) {
            Log.d(TAG, "Already processing, ignoring duplicate code submission")
            return
        }

        if (code.isNullOrBlank() || code.length != 6 || !code.all { it.isDigit() }) {
            Log.w(TAG, "Invalid pairing code format: $code")
            handleFailure("Invalid pairing code (need 6 digits)", reEnter = true)
            return
        }

        val port = discoveredPairingPort
        if (port == null) {
            Log.e(TAG, "No pairing port discovered")
            handleFailure("Pairing service not found", reEnter = true)
            return
        }

        isProcessing = true
        postNotification(buildPairingInProgressNotification())
        broadcastState(State.PAIRING, "Pairing…")

        currentJob = serviceScope.launch {
            try {
                val manager = AdbConnectionManager.getInstance(this@ShizukuPairingService)
                val paired = manager.pair(HOST, port, code)
                if (!paired) {
                    isProcessing = false
                    handleFailure("Pairing rejected (wrong code?)", reEnter = true)
                    return@launch
                }
                Log.d(TAG, "Pairing succeeded")
                isPairingDone = true
                // 配对成功 → 发现 connect 服务并连接 + 启动 server
                connectAndRunServer(manager)
            } catch (e: Exception) {
                Log.e(TAG, "Pairing failed", e)
                isProcessing = false
                handleFailure("Pairing failed: ${e.message}", reEnter = true)
            }
        }
    }

    // endregion

    // region 连接 + 启动 server 工作流

    /**
     * 配对成功后 (或 ACTION_START_SERVER 直接调用): 发现 connect 端口, 连接 ADB, 启动 server。
     */
    private fun connectAndRunServer(manager: AbsAdbConnectionManager) {
        currentJob = serviceScope.launch {
            try {
                broadcastState(State.CONNECTING, "Connecting to local ADB")
                postNotification(buildStartingServerNotification())

                val connectPort = waitForConnectPort(manager)
                if (connectPort == null) {
                    handleFailure("Could not find ADB connect port")
                    return@launch
                }

                if (!manager.isConnected) {
                    val connected = try {
                        manager.connect(HOST, connectPort)
                    } catch (e: Exception) {
                        Log.e(TAG, "ADB connect failed on port $connectPort", e)
                        false
                    }
                    if (!connected) {
                        handleFailure("ADB connect failed")
                        return@launch
                    }
                }
                Log.d(TAG, "ADB connected to $HOST:$connectPort")

                // 让 ADB 连接稳定一下再跑脚本
                delay(STABILIZE_DELAY_MS)

                broadcastState(State.STARTING, "Starting Shizuku server")
                val scriptOutput = runStartScript(manager)
                Log.d(TAG, "Start script output: $scriptOutput")

                // 等 binder 上线后再校验
                delay(BINDER_CHECK_DELAY_MS)

                val pingOk = try {
                    Shizuku.pingBinder()
                } catch (t: Throwable) {
                    Log.w(TAG, "Shizuku.pingBinder threw: ${t.message}")
                    false
                }
                if (pingOk) {
                    isServerStarted = true
                    handleSuccess()
                } else {
                    handleFailure("Shizuku server did not start (pingBinder=false)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "connectAndRunServer failed", e)
                handleFailure("Start failed: ${e.message}")
            }
        }
    }

    /**
     * ACTION_START_SERVER 入口: 假定 ADB 已连接, 直接跑启动脚本 + 校验。
     * 如果 ADB 没连上, 尝试 mDNS / fallback 端口连接。
     */
    private fun startServerWorkflow() {
        // 没有 mDNS 在跑就先起 connect 发现 (用于回退连接)
        if (connectMdns == null) {
            connectMdns = AdbMdns(
                this,
                AdbMdns.SERVICE_TYPE_TLS_CONNECT,
            ) { _, port, _ ->
                if (port > 0) {
                    discoveredConnectPort = port
                    connectLatch?.countDown()
                }
            }
            connectMdns?.start()
        }
        currentJob = serviceScope.launch {
            try {
                val manager = AdbConnectionManager.getInstance(this@ShizukuPairingService)
                if (!manager.isConnected) {
                    val connectPort = waitForConnectPort(manager)
                    if (connectPort == null) {
                        handleFailure("ADB not connected and no connect port found")
                        return@launch
                    }
                    val connected = try {
                        manager.connect(HOST, connectPort)
                    } catch (e: Exception) {
                        Log.e(TAG, "ADB connect failed on port $connectPort", e)
                        false
                    }
                    if (!connected) {
                        handleFailure("ADB connect failed")
                        return@launch
                    }
                    delay(STABILIZE_DELAY_MS)
                }
                broadcastState(State.STARTING, "Starting Shizuku server")
                runStartScript(manager)
                delay(BINDER_CHECK_DELAY_MS)
                val pingOk = try {
                    Shizuku.pingBinder()
                } catch (t: Throwable) {
                    false
                }
                if (pingOk) {
                    isServerStarted = true
                    handleSuccess()
                } else {
                    handleFailure("Shizuku server did not start (pingBinder=false)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "startServerWorkflow failed", e)
                handleFailure("Start failed: ${e.message}")
            }
        }
    }

    /**
     * 等待 connect 服务端口: 优先用已发现的端口, 否则等待 mDNS 发现 (最多 [CONNECT_DISCOVERY_TIMEOUT_MS]),
     * 仍找不到则尝试常见 fallback 端口。返回可用端口或 null。
     */
    private suspend fun waitForConnectPort(manager: AbsAdbConnectionManager): Int? {
        // 已经发现的端口直接用
        discoveredConnectPort?.let { return it }

        // 设置 latch, 让 connectMdns listener 来 countDown
        val latch = CountDownLatch(1)
        connectLatch = latch
        // double-check: 在设置 latch 之后再次检查, 避免遗漏
        discoveredConnectPort?.let {
            connectLatch = null
            return it
        }
        try {
            latch.await(CONNECT_DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        connectLatch = null
        stopConnectMdns()

        discoveredConnectPort?.let { return it }

        // mDNS 没找到, 尝试常见端口
        Log.d(TAG, "Connect service not found via mDNS, trying fallback ports")
        for (port in CONNECT_FALLBACK_PORTS) {
            try {
                if (manager.connect(HOST, port)) {
                    Log.d(TAG, "Fallback connect succeeded on port $port")
                    // connect 成功后 waitForConnectPort 调用者不会再 connect, 这里直接返回端口
                    // 但调用者会判断 isConnected; 既然已经连上了, 返回该端口即可
                    delay(STABILIZE_DELAY_MS)
                    return port
                }
            } catch (e: Exception) {
                Log.d(TAG, "Fallback port $port failed: ${e.message}")
            }
        }
        return null
    }

    /**
     * 执行 Shizuku server 启动脚本, 读取全部输出直到流结束。返回脚本输出文本。
     */
    private fun runStartScript(manager: AbsAdbConnectionManager): String {
        var stream: AdbStream? = null
        try {
            stream = manager.openStream(START_SCRIPT_DESTINATION)
            val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
            val output = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                output.appendLine(line)
                line = reader.readLine()
            }
            return output.toString()
        } finally {
            try {
                stream?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing start script stream", e)
            }
        }
    }

    // endregion

    // region 结果处理

    private fun handleSuccess() {
        broadcastState(State.SUCCESS, "Shizuku server started")
        postNotification(buildSuccessNotification())
        // 成功后稍候停止 Service
        currentJob = serviceScope.launch {
            delay(2_000L)
            stopSelf()
        }
    }

    /**
     * 失败处理: 广播 + 通知。通知/广播均可从任意线程调用, 因此无需切线程。
     *
     * @param reEnter true 表示可重试 (如配对码错误), 稍后重新弹出输入框; false 表示不可恢复, 稍后停止 Service。
     */
    private fun handleFailure(message: String, reEnter: Boolean = false) {
        broadcastState(State.FAILED, message)
        postNotification(buildFailureNotification(message))
        if (reEnter) {
            // 配对码错误等可重试场景: 稍后重新弹出输入框
            currentJob = serviceScope.launch {
                delay(2_000L)
                if (!isPairingDone) {
                    postNotification(buildEnterCodeNotification())
                }
            }
        } else {
            // 不可恢复的失败: 稍后停止 Service
            currentJob = serviceScope.launch {
                delay(2_000L)
                stopSelf()
            }
        }
    }

    // endregion

    // region 广播

    /**
     * 把当前状态广播给 UI。使用 setPackage 限制在 app 内, 避免泄露给其他应用。
     */
    private fun broadcastState(state: State, message: String) {
        Log.d(TAG, "broadcastState: $state ($message)")
        val intent = Intent(ACTION_PAIRING_STATE).apply {
            putExtra(EXTRA_STATE, state.name)
            putExtra(EXTRA_MESSAGE, message)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // endregion

    // region 清理

    private fun stopConnectMdns() {
        try {
            connectMdns?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping connectMdns", e)
        }
        connectMdns = null
    }

    private fun stopPairingMdns() {
        try {
            pairingMdns?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping pairingMdns", e)
        }
        pairingMdns = null
    }

    /**
     * 重置状态, 为新一轮配对做准备。会断开已有 ADB 连接以便重新配对。
     */
    private fun resetState() {
        currentJob?.cancel()
        currentJob = null
        stopPairingMdns()
        stopConnectMdns()
        connectLatch = null
        discoveredPairingPort = null
        discoveredConnectPort = null
        isProcessing = false
        isPairingDone = false
        isServerStarted = false
        try {
            val manager = AdbConnectionManager.getInstance(this)
            if (manager.isConnected) {
                Log.d(TAG, "Disconnecting existing ADB connection before fresh pairing")
                manager.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting ADB", e)
        }
    }

    /**
     * Service 销毁/取消时的完整清理。
     */
    private fun cleanup() {
        currentJob?.cancel()
        currentJob = null
        stopPairingMdns()
        stopConnectMdns()
        connectLatch = null
        // 保留 ADB 连接 (供后续 Shizuku 使用), 不主动 disconnect
    }

    // endregion
}
