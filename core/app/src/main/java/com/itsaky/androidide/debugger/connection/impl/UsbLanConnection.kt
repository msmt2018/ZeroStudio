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
        if (targetSerial.isNullOrBlank()) {
            // 不指定 serial: 至少要有一台状态是 "device"
            val hasReady = deviceLines.any { it.contains(Regex("\\bdevice\\b$")) || it.endsWith("\tdevice") }
            if (!hasReady) {
                throw IOException("adb devices: no device in 'device' state, found: $deviceLines")
            }
            log.info("UsbLanConnection: adb devices ok (no specific serial), {} device(s): {}", deviceLines.size, deviceLines)
        } else {
            // 指定 serial: 找这台
            val match = deviceLines.firstOrNull { it.startsWith(targetSerial) }
                ?: throw IOException("adb devices: serial '$targetSerial' not in list, found: $deviceLines")
            // 状态列在 serial 后 (tab-separated)
            val parts = match.split(Regex("\\s+"))
            if (parts.size < 2 || parts[1] != "device") {
                throw IOException("adb devices: serial '$targetSerial' state is '${parts.getOrNull(1) ?: "?"}', expected 'device'")
            }
            log.info("UsbLanConnection: adb devices ok, serial '{}' is 'device'", targetSerial)
        }
    }
}
