/*
 *  ZeroStudio IDE - Debugger Connection Layer
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
 */

package com.itsaky.androidide.debugger.connection.root

import com.itsaky.androidide.utils.ILogger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

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
class DefaultRootClient : RootClient {

    private val log = ILogger.ROOT

    override fun findProcessId(
        packageName: String,
        suBin: String,
        timeoutMs: Long,
    ): Int {
        // Android 8+ 用 pgrep / pidof 拿 pid
        // `pidof <packageName>` 拿第一行数字
        val cmd = arrayOf(suBin, "-c", "pidof $packageName || pgrep -f $packageName || echo -1")
        return try {
            val output = execWithTimeout(cmd, timeoutMs).trim()
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
            val netUnix = execWithTimeout(
                arrayOf(suBin, "-c", "cat /proc/$hostPid/net/unix"),
                timeoutMs,
            )
            if (!netUnix.contains("@jdwp")) {
                throw IOException("no jdwp socket found in /proc/$hostPid/net/unix")
            }
            // 起 socat (如设备装了)
            val socat = ProcessBuilder(suBin, "-c", "socat - UNIX-CONNECT:@jdwp")
                .redirectErrorStream(true)
                .start()
            return RootJdwpStream(
                input = socat.inputStream,
                output = socat.outputStream,
                onClose = {
                    runCatching { socat.destroyForcibly() }
                },
            )
        } catch (t: Throwable) {
            throw IOException(
                "RootClient.openJdwpStream failed (socat may not be installed): ${t.message}",
                t,
            )
        }
    }

    private fun execWithTimeout(cmd: Array<String>, timeoutMs: Long): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val done = java.util.concurrent.CountDownLatch(1)
        val outRef = arrayOfNulls<String>(null)
        val errRef = arrayOfNulls<Throwable>(null)
        thread(name = "RootExec", isDaemon = true) {
            try {
                outRef[0] = p.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            } catch (t: Throwable) {
                errRef[0] = t
            } finally {
                done.countDown()
            }
        }
        val finished = done.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            runCatching { p.destroyForcibly() }
            throw IOException("exec timeout (${timeoutMs}ms): ${cmd.joinToString(" ")}")
        }
        if (errRef[0] != null) throw errRef[0]!!
        return outRef[0] ?: ""
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
