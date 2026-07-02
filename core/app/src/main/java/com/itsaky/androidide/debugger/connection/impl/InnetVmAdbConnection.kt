/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  InnetVmAdbConnection: 内网虚拟机 (光速虚拟机/VMOS/虚拟大师) ADB
 *  端口转发方案 (子项目 6)。
 *
 *  流程 (走 [AdbForwardConnection] 共享实现):
 *    1) resolve: TCP 探测 adb 端口可达 (settings.innetAdb.adbHost/adbPort)
 *    2) connect: 走 AdbRunner
 *                  a) adb connect <adbHost>:<adbPort>     // [本类 override] VM 还没在 adb server 里
 *                  b) adb [-s serial] shell pidof <pkg>   // 拿 host PID
 *                  c) bind ServerSocket(0) 拿 L = localPort
 *                  d) adb [-s serial] forward tcp:L localabstract:jdwp-<pid>
 *    3) attach:  accept() on ServerSocket, 走 JDWP 握手 + VM.Version
 *    4) detach:  adb forward --remove + close socket
 *
 *  跟 InnetVmSocks (子项目 5) 的区别: 走 ADB forward 通道, 不需要 SOCKS5 server。
 *  跟 UsbLan (子项目 7) 的区别: 必须先 adb connect, 因为 VM 的 adb daemon 不在
 *                                本地 adb server 列表里。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.adb.AdbRunner
import com.itsaky.androidide.debugger.connection.ConnectionRetryPolicy
import java.io.IOException

class InnetVmAdbConnection(
    target: DebugTarget,
    settings: DebugConnectionSettings,
    adbRunner: AdbRunner? = null,
    retryPolicy: ConnectionRetryPolicy = ConnectionRetryPolicy(
        maxAttempts = settings.retryMaxAttempts,
        initialDelayMs = settings.retryInitialDelayMs,
    ),
) : AdbForwardConnection(
    type = ConnectionType.InnetVmAdb,
    target = target,
    settings = settings,
    adbRunner = adbRunner,
    retryPolicy = retryPolicy,
) {

    override val resolveInfoKind: String = "adb-forward-vm"

    override val adbHost: String get() = settings.innetAdb.adbHost
    override val adbPort: Int get() = settings.innetAdb.adbPort
    override val adbSerial: String? get() = settings.innetAdb.adbSerial
    override val connectTimeoutMs: Long get() = settings.innetAdb.connectTimeoutMs

    /**
     * 内网 VM 方案: 先 `adb connect <adbHost>:<adbPort>` 把 VM 加到 adb server
     * 列表里。失败抛 IOException 走 [AdbForwardConnection.connect] 的 retry。
     */
    @Throws(IOException::class)
    override fun runPreConnectCheck() {
        val cfg = settings.innetAdb
        val r = runAdb(listOf("connect", "${cfg.adbHost}:${cfg.adbPort}"))
        if (!r.isSuccess) {
            throw IOException("adb connect failed: exit=${r.exitCode}, stderr=${r.stderr.trim()}")
        }
        log.info("InnetVmAdbConnection: adb connect ok: {}", r.stdout.trim())
    }
}
