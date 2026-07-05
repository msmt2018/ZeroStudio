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
import com.itsaky.androidide.debugger.connection.InnetAdbConfig
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
     * 列表里。
     *
     * **Phase 13e 修复**:
     *   内网虚拟机 (光速 VM / VMOS / 虚拟大师) 慢启动 1-3s, 期间
     *   `adb connect` 经常返回 "failed to connect" / "connection refused" /
     *   "no route to host" 之类, 旧实现只试一次, 直接抛 IOException 走
     *   上层 [AdbForwardConnection.connect] 的 retry (3 次 + 1-2-4s backoff),
     *   但每次 retry 跑整个 connect 流程 (重开 ServerSocket + 重 forward + 重新
     *   pidof 等), 重 cost + 用户等待时间累积。
     *
     *   改成: runPreConnectCheck 内部 poll VM adb status, 5s window 内每 500ms
     *   试一次 `adb connect` + `adb devices` 校验, 出现就 OK, 超时再 fail。
     *   上层 retry 拿到的"前置检查通过"信号已经是 VM 真起来了, 不再浪费
     *   ServerSocket / forward 资源。
     *
     * 失败抛 IOException 走 [AdbForwardConnection.connect] 的 retry。
     */
    @Throws(IOException::class)
    override fun runPreConnectCheck() {
        val cfg = settings.innetAdb
        val endpoint = "${cfg.adbHost}:${cfg.adbPort}"
        // Phase 13e: poll window 默认 5s, 步长 500ms, 最多 10 次
        val pollWindowMs = VM_STARTUP_POLL_WINDOW_MS
        val pollStepMs = VM_STARTUP_POLL_STEP_MS
        val maxAttempts = (pollWindowMs / pollStepMs).toInt()
        val deadline = System.currentTimeMillis() + pollWindowMs
        var lastStderr = ""
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            attempt++
            val r = runAdb(listOf("connect", endpoint))
            if (r.isSuccess) {
                // adb connect OK, 立即校验 adb devices 列表里是否真有 (避免 "already
                // connected" / "no such host" 之类 false positive)
                if (isVmInDevicesList(cfg)) {
                    log.info("InnetVmAdbConnection: adb connect ok on attempt {}: {}", attempt, r.stdout.trim())
                    return
                }
                lastStderr = "adb connect returned ok but device not in adb devices list"
            } else {
                lastStderr = r.stderr.trim()
                log.debug("InnetVmAdbConnection: adb connect attempt {} failed: {}", attempt, lastStderr)
            }
            // 等下一步, 避免 busy loop
            if (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(pollStepMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("VM startup poll interrupted", ie)
                }
            }
        }
        throw IOException(
            "VM adb did not come up within ${pollWindowMs}ms ($maxAttempts attempts): $lastStderr"
        )
    }

    /**
     * 调 `adb devices` 检查 VM serial 是否在列表里。
     * - 优先匹配 adbSerial (已配置, 走精确匹配)
     * - 否则 fallback 匹配 endpoint (host:port), 拿 <serial> <state> 第二列
     *
     * 注意: adb devices 输出可能是 `<serial>\t<state>` (单 transport) 或
     * `<serial>\t<state>\t<transport-id>` (Android 11+ 多 transport), split
     * by whitespace 拿 [0] serial + [1] state。
     */
    private fun isVmInDevicesList(cfg: InnetAdbConfig): Boolean {
        val r = runAdb(listOf("devices"))
        if (!r.isSuccess) return false
        val expected = cfg.adbSerial?.takeIf { it.isNotBlank() } ?: "${cfg.adbHost}:${cfg.adbPort}"
        return r.stdout.lines().any { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) return@any false
            val serial = parts[0]
            val state = parts[1]
            state == "device" && serial == expected
        }
    }

    companion object {
        // Phase 13e: VM 慢启动 poll window, 跟 adb connect 间隔 500ms 试到 5s。
        // adb 自身 default connect timeout 是 5s, 我们 5s window 跟它对齐。
        private const val VM_STARTUP_POLL_WINDOW_MS: Long = 5_000L
        private const val VM_STARTUP_POLL_STEP_MS: Long = 500L
    }
}
