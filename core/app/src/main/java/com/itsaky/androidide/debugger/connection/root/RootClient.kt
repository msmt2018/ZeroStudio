/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  RootClient: 走 su -c 起 root 进程做具体操作 (找 pid / open JDWP socket)。
 *
 *  真实实现分两步:
 *    1) findProcessId: su -c 'pidof <packageName>' 拿 pid
 *       Android 8+ pidof 可能不可用, 改用 `pgrep -f <packageName>`
 *    2) openJdwpSocket: su -c 'cat /proc/<host_pid>/net/unix' 找 jdwp socket path,
 *       然后 su -c 'socat - UNIX-CONNECT:<path>' 把它转成 stdin/stdout 暴露给 IDE
 *
 *  本子项目骨架: 这两个方法的真实实现依赖子项目 8 host runtime 一起提供,
 *  暂留 stub 实现 (返回 -1 / 抛 NotImplemented), 子项目 8 完成后补全。
 *
 *  测试用 FakeRootClient: 可预置 findProcessId / openJdwpSocket 返回值。
 *
 *  Phase 13h: findProcessId 走 ProcessRunner 公共实现 (替代直接 ProcessBuilder
 *  + daemon thread + CountDownLatch, 跟 DefaultAdbRunner 共享)。
 *  openJdwpStream 走 ProcessRunner.startLive (长生命周期 stream, 暴露
 *  inputStream/outputStream 给 RootConnection 接管, onClose 由 RootConnection
 *  lifecycle 调)。
 */

package com.itsaky.androidide.debugger.connection.root

import com.itsaky.androidide.debugger.connection.process.ProcessRunner
import com.itsaky.androidide.utils.ILogger
import java.io.IOException
import java.net.Socket

/**
 * Root 客户端抽象。
 */
interface RootClient {
    /**
     * 走 su 找 host app pid。
     * @return pid, 失败返回 -1
     */
    fun findProcessId(
        packageName: String,
        suBin: String,
        timeoutMs: Long,
    ): Int

    /**
     * 走 su + 内部 attach agent 拿 host JDWP socket fd, 转成 IDE 端
     * 的 InputStream/OutputStream (不是 java.net.Socket, 因 jdwp 是 unix
     * abstract namespace socket, su -c 通过 socat 把 stdin/stdout 接到 socket).
     *
     * @return RootJdwpStream (input/output 流), 失败抛 IOException
     */
    fun openJdwpStream(
        hostPid: Int,
        suBin: String,
        timeoutMs: Long,
    ): RootJdwpStream

    companion object {
        @JvmStatic
        fun create(): RootClient = DefaultRootClient()
    }
}

/**
 * 子项目 4: Root 路径下拿到的 JDWP 字节流 (不走 java.net.Socket, 因 jdwp 是
 * abstract unix socket, 通过 su -c socat 桥接出 stdin/stdout).
 *
 * close() 应该关掉 su -c 的进程 + 流。
 */
data class RootJdwpStream(
    val input: java.io.InputStream,
    val output: java.io.OutputStream,
    val onClose: () -> Unit = {},
) : java.io.Closeable {
    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { onClose() }
    }
}

/**
 * 默认生产实现。
 */
class DefaultRootClient(
    private val processRunner: ProcessRunner = ProcessRunner(),
) : RootClient {

    private val log = ILogger.ROOT

    override fun findProcessId(
        packageName: String,
        suBin: String,
        timeoutMs: Long,
    ): Int {
        // Android 8+ 用 pgrep / pidof 拿 pid
        // `pidof <packageName>` 拿第一行数字
        val cmd = listOf(suBin, "-c", "pidof $packageName || pgrep -f $packageName || echo -1")
        return try {
            val result = processRunner.run(cmd, timeoutMs, redirectErrorStream = true)
            val output = result.stdout.trim()
            val firstLine = output.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "-1"
            firstLine.split(Regex("\\s+")).firstOrNull()?.toIntOrNull() ?: -1
        } catch (t: Throwable) {
            log.warn("findProcessId: failed: {}", t.message)
            -1
        }
    }

    override fun openJdwpStream(
        hostPid: Int,
        suBin: String,
        timeoutMs: Long,
    ): RootJdwpStream {
        // 子项目 4 真实实现: 走 su exec + socat 把 host 的 @jdwp unix socket
        //   转成 stdin/stdout. RootConnection 走 InputStream/OutputStream 路径
        //   (与 AidlSocketConnection LocalBridge 同款, 不走 java.net.Socket).
        //
        //   1) su -c 'cat /proc/<host_pid>/net/unix' 校验 @jdwp socket 存在
        //   2) su -c 'socat - UNIX-CONNECT:@jdwp' 起 stdin/stdout 桥接
        //   3) IDE 端拿 Process.inputStream/outputStream 当 jdwp 字节流用
        try {
            val netUnixResult = processRunner.run(
                listOf(suBin, "-c", "cat /proc/$hostPid/net/unix"),
                timeoutMs,
                redirectErrorStream = true,
            )
            val netUnix = netUnixResult.stdout
            if (!netUnix.contains("@jdwp")) {
                throw IOException("no jdwp socket found in /proc/$hostPid/net/unix")
            }
            // Phase 12v: 两个真问题修
            //   1) 之前 redirectErrorStream(true) 把 stderr 合到 inputStream 一起给
            //      RootConnection, RootConnection 读到的 JDWP 字节流里会夹 stderr
            //      字节, JDWP 协议直接挂
            //   2) stderr 没人 drain: socat 写 stderr 满 kernel pipe buffer 会
            //      deadlock, socat 卡死, IDE 端 inputStream.read() 永久阻塞
            //   3) onClose destroyForcibly 不等 socat 实际退出, socat zombie
            //      短时间占 FDs
            // 修法: stderr 独立 (不 redirectErrorStream), 起 daemon thread drain
            //   stderr; onClose destroyForcibly + waitFor 兜底
            // Phase 13h: 走 ProcessRunner.startLive 拿 LiveProcess (内部已包 stderr
            //   drain + onClose destroyForcibly + waitFor 2s 兜底)
            val live = processRunner.startLive(
                listOf(suBin, "-c", "socat - UNIX-CONNECT:@jdwp")
            )
            return RootJdwpStream(
                input = live.inputStream,
                output = live.outputStream,
                onClose = { live.close() },
            )
        } catch (t: Throwable) {
            throw IOException(
                "RootClient.openJdwpStream failed (socat may not be installed): ${t.message}",
                t,
            )
        }
    }
}

/**
 * 测试用 fake: 可预置 findProcessId / openJdwpStream 返回值。
 */
class FakeRootClient(
    private val pidResult: Int = 0,
    private val streamResult: RootJdwpStream? = null,
) : RootClient {

    var findProcessIdCallCount: Int = 0
        private set
    var openJdwpStreamCallCount: Int = 0
        private set
    var lastPid: Int = 0
        private set
    var lastPackageName: String? = null
        private set
    var lastSuBin: String? = null
        private set

    override fun findProcessId(
        packageName: String,
        suBin: String,
        timeoutMs: Long,
    ): Int {
        findProcessIdCallCount++
        lastPackageName = packageName
        lastSuBin = suBin
        return pidResult
    }

    override fun openJdwpStream(
        hostPid: Int,
        suBin: String,
        timeoutMs: Long,
    ): RootJdwpStream {
        openJdwpStreamCallCount++
        lastPid = hostPid
        lastSuBin = suBin
        return streamResult ?: throw UnsupportedOperationException(
            "FakeRootClient: streamResult is null, can't open real stream"
        )
    }
}
