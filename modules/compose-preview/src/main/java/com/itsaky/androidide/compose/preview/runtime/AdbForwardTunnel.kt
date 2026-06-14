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

package com.itsaky.androidide.compose.preview.runtime

import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * v2.5 P0 P3-FE-05: ADB forward 隧道.
 *
 * ## 设计目标
 *
 * 允许 IDE 端通过 USB / WiFi adb 监听 AndroidIDE 设备端发出的"远程预览"请求.
 * 典型场景: 用户在 IDE 改完代码后, 设备端 (AndroidIDE app) 自动 `adb reverse` 或
 * `adb forward` 一个本地端口, IDE 端通过这个端口接收"请渲染此函数"的请求, 把渲染
 * 结果 (PNG bytes) 推回设备.
 *
 * 实际工作原理:
 * ```
 *   adb forward tcp:PORT localabstract:androidide_preview
 *   (在 host 端监听 PORT, 设备端可通过 local socket `androidide_preview` 访问)
 * ```
 *
 * 反向 `adb reverse` 由 [reverse] 协助, 让设备访问 IDE 端服务.
 *
 * ## 用法
 *
 * ```
 * val tunnel = AdbForwardTunnel()
 * if (tunnel.isAdbAvailable()) {
 *   tunnel.forward(9876, "androidide_preview")
 *   // ... 设备端连接后, IDE 端通过 PreviewServer 处理 ...
 *   tunnel.removeForward(9876)
 * }
 * ```
 *
 * ## 线程模型
 *
 * 全部命令都是阻塞的 [ProcessBuilder] 调用, 推荐在 IO 线程使用. 每次调用最多等
 * 5 秒, 超时后 cancel.
 */
class AdbForwardTunnel(
    private val adbPath: String = "adb",
    private val commandTimeoutMs: Long = 5_000L,
) {

    private val LOG = LoggerFactory.getLogger(AdbForwardTunnel::class.java)

    /** adb 是否在 PATH 中. */
    fun isAdbAvailable(): Boolean = try {
        exec("--version").exitValue == 0
    } catch (e: IOException) {
        false
    }

    /** 已连接设备序列号列表. */
    fun devices(): List<String> = try {
        val out = exec("devices").stdout
        out.lineSequence()
            .drop(1)  // 跳过 header "List of devices attached"
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2 && parts[1] == "device") parts[0] else null
            }
            .toList()
    } catch (e: Throwable) {
        LOG.warn("adb devices failed: {}", e.message)
        emptyList()
    }

    /**
     * 主机 → 设备 forward.
     *
     * `adb forward tcp:HOST_PORT localabstract:DEVICE_SOCKET`
     *
     * @return 成功 / 失败 + 信息
     */
    fun forward(hostPort: Int, deviceSocket: String, deviceSerial: String? = null): Boolean {
        val cmd = mutableListOf("forward", "tcp:$hostPort", "localabstract:$deviceSocket")
        if (deviceSerial != null) {
            cmd.add(0, "-s")
            cmd.add(1, deviceSerial)
        }
        val res = exec(*cmd.toTypedArray())
        if (res.exitValue != 0) {
            LOG.error("adb forward failed: {}", res.stderr.take(200))
            return false
        }
        LOG.info("adb forward: tcp:{} -> localabstract:{}", hostPort, deviceSocket)
        return true
    }

    /**
     * 设备 → 主机 reverse.
     *
     * `adb reverse tcp:DEVICE_PORT tcp:HOST_PORT`
     */
    fun reverse(devicePort: Int, hostPort: Int, deviceSerial: String? = null): Boolean {
        val cmd = mutableListOf("reverse", "tcp:$devicePort", "tcp:$hostPort")
        if (deviceSerial != null) {
            cmd.add(0, "-s")
            cmd.add(1, deviceSerial)
        }
        val res = exec(*cmd.toTypedArray())
        if (res.exitValue != 0) {
            LOG.error("adb reverse failed: {}", res.stderr.take(200))
            return false
        }
        LOG.info("adb reverse: tcp:{} -> tcp:{}", devicePort, hostPort)
        return true
    }

    fun removeForward(hostPort: Int, deviceSerial: String? = null): Boolean {
        val cmd = mutableListOf("forward", "--remove", "tcp:$hostPort")
        if (deviceSerial != null) {
            cmd.add(0, "-s")
            cmd.add(1, deviceSerial)
        }
        return exec(*cmd.toTypedArray()).exitValue == 0
    }

    fun removeReverse(devicePort: Int, deviceSerial: String? = null): Boolean {
        val cmd = mutableListOf("reverse", "--remove", "tcp:$devicePort")
        if (deviceSerial != null) {
            cmd.add(0, "-s")
            cmd.add(1, deviceSerial)
        }
        return exec(*cmd.toTypedArray()).exitValue == 0
    }

    /** 列出当前所有 forward 规则. 解析输出 (每行 "host_port device_socket"). */
    fun listForward(): List<Pair<Int, String>> = try {
        val out = exec("forward", "--list").stdout
        out.lineSequence()
            .mapNotNull { line ->
                // 格式: "<serial> tcp:1234 localabstract:foo" 或 "tcp:1234 localabstract:foo"
                val m = Regex("""(?:[\w\-.]+\s+)?tcp:(\d+)\s+(\S+)""").find(line) ?: return@mapNotNull null
                val port = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                port to m.groupValues[2]
            }
            .toList()
    } catch (e: Throwable) {
        emptyList()
    }

    private data class ExecResult(val exitValue: Int, val stdout: String, val stderr: String)

    private fun exec(vararg args: String): ExecResult {
        val cmd = arrayOf(adbPath) + args
        val pb = ProcessBuilder(*cmd).redirectErrorStream(false)
        val process = pb.start()
        val finished = process.waitFor(commandTimeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IOException("adb command timeout: ${args.joinToString(" ")}")
        }
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        return ExecResult(process.exitValue(), out, err)
    }
}
