package com.itsaky.androidide.debugger.root

import android.content.Context
import android.zero.studio.adblib.AdbBase64
import android.zero.studio.adblib.AdbConnection
import android.zero.studio.adblib.AdbCrypto
import android.zero.studio.adblib.AdbStream
import android.zero.studio.adblib.TcpChannel
import android.zero.studio.shell.common.domain.model.OutputLine
import android.zero.studio.shell.otg_adb_shell.domain.repository.OtgRepository
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root 后驱动 ADB 连接设备的桥接器。
 *
 * 三种连接方式：
 * - **本机**：root 已授权即等于连上本机；命令直接走 [RootManager.executeRootCommand]
 * - **无线 / 局域网设备**：通过 adblib [TcpChannel] + [AdbConnection] 连接 `ip:port`
 * - **USB 有线设备**：复用 [OtgRepository]（其内部已实现 UsbChannel + adblib 连接）
 *
 * 设备列表 [deviceList] 包含所有已识别设备，其中至多一个 `isActive = true`。
 * 命令执行页根据活动设备的类型路由到对应后端。
 *
 * 注：本类仅负责「连接管理 + 命令路由」，UI 状态由 [RootManager.rootState] 与本类 [deviceList] 共同驱动。
 */
@Singleton
class RootAdbBridge @Inject constructor(
    private val rootManager: RootManager,
    private val otgRepository: OtgRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {

    private val _deviceList = MutableStateFlow<List<RootAdbDevice>>(emptyList())
    val deviceList: StateFlow<List<RootAdbDevice>> = _deviceList.asStateFlow()

    /** WiFi 连接的 AdbConnection 池，key 为 "ip:port"。 */
    private val wifiConnections = mutableMapOf<String, AdbConnection>()

    /** AdbCrypto，懒加载。 */
    @Volatile
    private var adbCrypto: AdbCrypto? = null

    /**
     * 连接本机。root 已授权即可，本设备始终可用。
     */
    fun connectLocal() {
        if (!rootManager.isGranted) return
        val current = _deviceList.value
        if (current.none { it.type == RootAdbDeviceType.LOCAL }) {
            val device = RootAdbDevice(
                type = RootAdbDeviceType.LOCAL,
                serial = "this-device",
                model = "本机",
                state = RootAdbDeviceState.DEVICE,
            )
            _deviceList.value = current + device
        }
        setActive("this-device")
    }

    /**
     * 通过 adblib 连接无线 / 局域网设备。
     *
     * 目标设备需已开启 adb tcpip（默认 5555 端口）。
     */
    suspend fun connectWifi(ip: String, port: Int): Result<RootAdbDevice> = withContext(Dispatchers.IO) {
        if (!rootManager.isGranted) {
            return@withContext Result.failure(IllegalStateException("需要 root 权限"))
        }
        val address = "$ip:$port"
        // 已存在则直接返回
        _deviceList.value.firstOrNull { it.serial == address }?.let {
            return@withContext Result.success(it)
        }
        runCatching {
            val crypto = getOrCreateCrypto()
            val socket = Socket(ip, port)
            socket.tcpNoDelay = true
            val channel = TcpChannel(socket)
            val connection = AdbConnection.create(channel, crypto).apply { connect() }
            wifiConnections[address] = connection
            val device = RootAdbDevice(
                type = RootAdbDeviceType.WIFI,
                serial = address,
                model = address,
                state = RootAdbDeviceState.DEVICE,
            )
            _deviceList.value = _deviceList.value + device
            device
        }
    }

    /**
     * 断开无线设备。
     */
    fun disconnectWifi(address: String) {
        wifiConnections.remove(address)?.close()
        _deviceList.value = _deviceList.value.filterNot { it.serial == address }
    }

    /**
     * 把已通过 [OtgRepository] 连接的 USB 设备纳入设备列表。
     *
     * 调用时机：OTG 卡片连接成功后，由 RootAdbBridge 同步镜像。
     */
    fun mirrorUsbDevice(serial: String, deviceName: String?) {
        if (_deviceList.value.any { it.serial == serial && it.type == RootAdbDeviceType.USB }) return
        val device = RootAdbDevice(
            type = RootAdbDeviceType.USB,
            serial = serial,
            model = deviceName,
            state = RootAdbDeviceState.DEVICE,
        )
        _deviceList.value = _deviceList.value + device
    }

    /**
     * 设为活动设备。后续 [execOnActiveDevice] 的命令会路由到该设备。
     */
    fun setActive(serial: String) {
        _deviceList.value = _deviceList.value.map { it.copy(isActive = it.serial == serial) }
    }

    /**
     * 刷新设备列表（移除已离线 / 已断开的设备）。
     *
     * 落实 spec §4.3.6：通过 `adb devices` 命令刷新（adb 二进制后端，libsu 执行）。
     * 若 adb 二进制不可用，则仅清理已断开的 WiFi 连接。
     */
    fun refreshDevices() {
        // 清理已断开的 WiFi 连接
        _deviceList.value = _deviceList.value.filterNot {
            it.type == RootAdbDeviceType.WIFI && !wifiConnections.containsKey(it.serial)
        }
        // 若 root 已授权，尝试用 libsu 执行 `adb devices` 刷新设备列表
        if (rootManager.isGranted) {
            // 在后台协程中执行 adb devices 刷新，避免阻塞调用方
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    val output = rootManager.executeLibsuCapture("adb devices")
                    parseAdbDevicesOutput(output)
                }
            }
        }
    }

    /**
     * 解析 `adb devices` 输出，更新设备列表。
     *
     * 输出格式示例：
     * ```
     * List of devices attached
     * 192.168.1.50:5555    device
     * emulator-5554    offline
     * ```
     */
    private fun parseAdbDevicesOutput(output: String) {
        val lines = output.lineSequence()
            .dropWhile { !it.contains("List of devices") }
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val parsed = mutableListOf<RootAdbDevice>()
        for (line in lines) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue
            val serial = parts[0]
            val state = parts[1]
            val type = when {
                serial.contains(":") -> RootAdbDeviceType.WIFI
                serial.startsWith("emulator-") -> RootAdbDeviceType.LOCAL
                else -> RootAdbDeviceType.USB
            }
            val deviceState = when (state) {
                "device" -> RootAdbDeviceState.DEVICE
                "offline" -> RootAdbDeviceState.OFFLINE
                "unauthorized" -> RootAdbDeviceState.UNAUTHORIZED
                else -> RootAdbDeviceState.UNKNOWN
            }
            parsed.add(RootAdbDevice(
                type = type,
                serial = serial,
                model = serial,
                state = deviceState,
            ))
        }
        if (parsed.isNotEmpty()) {
            // 合并：保留 LOCAL 本机设备，替换 WIFI/USB 设备
            val local = _deviceList.value.filter { it.type == RootAdbDeviceType.LOCAL }
            _deviceList.value = local + parsed.filter { it.type != RootAdbDeviceType.LOCAL }
        }
    }

    /**
     * 在活动设备上执行命令。按设备类型路由：
     * - LOCAL → [RootManager.executeRootCommand]
     * - WIFI → 通过该设备的 [AdbConnection] 开 `shell:<cmd>` 流
     * - USB → [OtgRepository.runOtgCommand]
     */
    fun execOnActiveDevice(command: String): Flow<OutputLine> = flow {
        val active = _deviceList.value.firstOrNull { it.isActive }
            ?: throw IllegalStateException("无活动设备")
        when (active.type) {
            RootAdbDeviceType.LOCAL -> {
                rootManager.executeRootCommand(command).collect { emit(it) }
            }
            RootAdbDeviceType.WIFI -> {
                val connection = wifiConnections[active.serial]
                    ?: throw IllegalStateException("设备未连接: ${active.serial}")
                val stream: AdbStream = connection.open("shell:$command")
                while (!stream.isClosed) {
                    val data = stream.read()
                    if (data == null) break
                    emit(OutputLine(text = String(data)))
                }
            }
            RootAdbDeviceType.USB -> {
                otgRepository.runOtgCommand(command).collect { emit(it) }
            }
        }
    }

    /**
     * 取消当前正在执行的命令。
     */
    fun stopCommand() {
        rootManager.stopRootCommand()
        otgRepository.stopCommand()
    }

    /**
     * 懒加载 AdbCrypto，与 OtgRepositoryImpl 一致的密钥存储策略。
     */
    private suspend fun getOrCreateCrypto(): AdbCrypto = withContext(Dispatchers.IO) {
        adbCrypto?.let { return@withContext it }
        val base64 = AdbBase64 { data -> Base64.encodeToString(data, Base64.NO_WRAP) }
        val priv = File(context.filesDir, "root_adb_priv_key")
        val pub = File(context.filesDir, "root_adb_pub_key")
        val crypto = if (priv.exists() && pub.exists()) {
            AdbCrypto.loadAdbKeyPair(base64, priv, pub)
        } else {
            AdbCrypto.generateAdbKeyPair(base64).apply {
                saveAdbKeyPair(priv, pub)
            }
        }
        adbCrypto = crypto
        crypto
    }
}