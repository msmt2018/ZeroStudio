/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  UsbLanConnection: USB / 局域网 ADB 端口转发方案 (子项目 7)。
 *
 *  流程 (走 [AdbForwardConnection] 共享实现):
 *    1) resolve: TCP 探测 adb server 端口可达 (settings.usbLan.adbHost/adbPort,
 *                默认 127.0.0.1:5037 即本地 adb server)
 *    2) connect: 走 AdbRunner
 *                  a) adb devices                                // [本类 override] 校验设备已在列表
 *                  b) adb [-s serial] shell pidof <pkg>          // 拿 host PID
 *                  c) bind ServerSocket(0) 拿 L = localPort
 *                  d) adb [-s serial] forward tcp:L localabstract:jdwp-<pid>
 *    3) attach:  accept() + JDWP 握手 + VM.Version
 *    4) detach:  adb forward --remove + close
 *
 *  跟 InnetVmAdb (子项目 6) 的区别:
 *    - 不需要 adb connect: 设备假定已经在 adb server 列表
 *      (USB 插着自动出现 / LAN 已经 `adb connect` 过)
 *    - 走 adb devices 验证设备真的在列表里 (防止用户配了错的 adbSerial)
 *    - 默认 adbPort=5037 (本地 adb server) 而不是 5555 (VM adb daemon)
 *    - 复用 settings.usbLan.* (不是 settings.innetAdb.*)
 *
 *  跟 InnetVmSocks (子项目 5) 的区别: 走 ADB forward 通道, 不需要 SOCKS5 server。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.ConnectionRetryPolicy
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.adb.AdbRunner
import java.io.IOException

class UsbLanConnection(
    target: DebugTarget,
    settings: DebugConnectionSettings,
    adbRunner: AdbRunner? = null,
    retryPolicy: ConnectionRetryPolicy = ConnectionRetryPolicy(
        maxAttempts = settings.retryMaxAttempts,
        initialDelayMs = settings.retryInitialDelayMs,
    ),
) : AdbForwardConnection(
    type = ConnectionType.UsbLan,
    target = target,
    settings = settings,
    adbRunner = adbRunner,
    retryPolicy = retryPolicy,
) {

    override val resolveInfoKind: String = "adb-forward-usb"

    override val adbHost: String get() = settings.usbLan.adbHost
    override val adbPort: Int get() = settings.usbLan.adbPort
    override val adbSerial: String? get() = settings.usbLan.adbSerial
    override val connectTimeoutMs: Long get() = settings.usbLan.connectTimeoutMs

    /**
     * USB / LAN 方案: 走 `adb devices` 校验目标设备在 adb server 列表里。
     *
     * 行为:
     *   - 没配 adbSerial: 任何一台 device 都行 (assertExitCode0 + stdout 含 "device")
     *   - 配了 adbSerial:   那台 serial 必须出现且状态是 "device" (非 "offline" / "unauthorized")
     *
     * **Phase 13f 修复**:
     *   1) Android 11+ `adb devices` 输出格式从 `<serial>\t<state>` 变成
     *      `<serial> <state> <transport-id>` (3 列), 旧实现:
     *      - endsWith("device") 在没配 adbSerial 路径能用 (transport-id 是数字, 不会
     *        跟 "device" 冲突)
     *      - 配 adbSerial 路径 `split(Regex("\\s+"))` 拿 parts[1] == "device" 已对,
     *        但 parts.size 只判 >= 2, transport-id 列会被忽略
     *      改成: 统一 split 拿 [0] serial + [1] state, 兼容 2 列 / 3 列
     *   2) state == "unauthorized" / "offline" / "no permissions" 走特定 IOException
     *      message (之前一律 generic error, 用户看不出 "请点 '允许 USB 调试'")
     *
     * 失败抛 IOException 走 [AdbForwardConnection.connect] 的 retry。
     */
    @Throws(IOException::class)
    override fun runPreConnectCheck() {
        val cfg = settings.usbLan
        val r = runAdb(listOf("devices"))
        if (!r.isSuccess) {
            throw IOException("adb devices failed: exit=${r.exitCode}, stderr=${r.stderr.trim()}")
        }
        val stdout = r.stdout
        val targetSerial = cfg.adbSerial
        val deviceLines = stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("List of devices") }
            .toList()

        if (deviceLines.isEmpty()) {
            throw IOException("adb devices: no devices attached (is the device connected via USB / LAN?)")
        }
        // Phase 13f: 解析每行 [serial, state, transport-id?]
        // 输出例:
        //   Android <11:    "emulator-5554\tdevice"
        //   Android >=11:   "emulator-5554 device 1"  (多 transport)
        data class DeviceEntry(val serial: String, val state: String, val transportId: String?)
        val entries = deviceLines.mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) null else DeviceEntry(parts[0], parts[1], parts.getOrNull(2))
        }

        if (targetSerial.isNullOrBlank()) {
            // 不指定 serial: 至少要有一台状态是 "device"
            val hasReady = entries.any { it.state == "device" }
            if (!hasReady) {
                // Phase 13f: 区分 unauthorized / offline / no permissions 走特定错误
                val unauthorized = entries.firstOrNull { it.state == "unauthorized" }
                if (unauthorized != null) {
                    throw IOException(
                        "adb devices: serial '${unauthorized.serial}' is 'unauthorized'. " +
                            "Tap 'Always allow from this computer' on the device's 'Allow USB debugging' dialog, " +
                            "then re-try."
                    )
                }
                val offline = entries.firstOrNull { it.state == "offline" }
                if (offline != null) {
                    throw IOException(
                        "adb devices: serial '${offline.serial}' is 'offline'. " +
                            "Re-plug the USB cable or `adb disconnect && adb connect` for LAN."
                    )
                }
                val noPerm = entries.firstOrNull { it.state == "no permissions" }
                if (noPerm != null) {
                    throw IOException(
                        "adb devices: serial '${noPerm.serial}' is 'no permissions'. " +
                            "Check `adb kill-server && adb start-server` as root (udev rules on Linux)."
                    )
                }
                throw IOException(
                    "adb devices: no device in 'device' state, found: ${entries.map { "${it.serial}=${it.state}" }}"
                )
            }
            log.info(
                "UsbLanConnection: adb devices ok (no specific serial), {} device(s): {}",
                entries.size,
                entries.map { "${it.serial}=${it.state}" },
            )
        } else {
            // 指定 serial: 找这台
            val match = entries.firstOrNull { it.serial == targetSerial }
                ?: throw IOException(
                    "adb devices: serial '$targetSerial' not in list, found: " +
                        entries.map { "${it.serial}=${it.state}" }
                )
            // Phase 13f: state 走特定错误
            when (match.state) {
                "device" -> {
                    log.info(
                        "UsbLanConnection: adb devices ok, serial '{}' is 'device' (transport={})",
                        targetSerial, match.transportId ?: "default",
                    )
                }
                "unauthorized" -> throw IOException(
                    "adb devices: serial '$targetSerial' is 'unauthorized'. " +
                        "Tap 'Always allow from this computer' on the device's 'Allow USB debugging' dialog, " +
                        "then re-try."
                )
                "offline" -> throw IOException(
                    "adb devices: serial '$targetSerial' is 'offline'. " +
                        "Re-plug the USB cable or `adb disconnect && adb connect` for LAN."
                )
                "no permissions" -> throw IOException(
                    "adb devices: serial '$targetSerial' is 'no permissions'. " +
                        "Check `adb kill-server && adb start-server` as root (udev rules on Linux)."
                )
                else -> throw IOException(
                    "adb devices: serial '$targetSerial' state is '${match.state}', expected 'device'"
                )
            }
        }
    }
}
