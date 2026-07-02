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
     * 走 su + 内部 attach agent 拿 host JDWP socket, 转成 IDE 端 java.net.Socket。
     * 真实实现依赖子项目 8 host runtime (需要在 host 进程跑 attach agent 命令,
     * 拿到 JDWP fd 后转成 stdin/stdout 走 su -c 回到 IDE 端)。
     *
     * 当前实现: 留 stub, 抛 NotImplementedError。子项目 8 完成后实现。
     */
    fun openJdwpSocket(
        hostPid: Int,
        suBin: String,
        timeoutMs: Long,
    ): Socket

    companion object {
        @JvmStatic
        fun create(): RootClient = DefaultRootClient()
    }
}

/**
 * 默认生产实现 (骨架, 等子项目 8 一起补全)。
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

    override fun openJdwpSocket(
        hostPid: Int,
        suBin: String,
        timeoutMs: Long,
    ): Socket {
        // 留 stub, 等子项目 8 host runtime (attach agent + socat) 一起提供
        // 预期实现:
        //   1) su -c 'cat /proc/<host_pid>/net/unix' 找 jdwp socket path
        //   2) su -c 'cat /proc/<host_pid>/cmdline' 校验 host process
        //   3) su -c 'socat - UNIX-CONNECT:<path>' 起 stdin/stdout 转发
        //   4) IDE 端拿 Process 的 stdout / 写 stdin 当 Socket 用
        throw UnsupportedOperationException(
            "RootClient.openJdwpSocket 依赖子项目 8 host runtime 一起提供, " +
                "暂未实装"
        )
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
 * 测试用 fake: 可预置 findProcessId / openJdwpSocket 返回值。
 */
class FakeRootClient(
    private val pidResult: Int = 0,
    private val socketResult: Socket? = null,
) : RootClient {

    var findProcessIdCallCount: Int = 0
        private set
    var openJdwpSocketCallCount: Int = 0
        private set
    var lastPid: Int = 0
        private set

    override fun findProcessId(
        packageName: String,
        suBin: String,
        timeoutMs: Long,
    ): Int {
        findProcessIdCallCount++
        return pidResult
    }

    override fun openJdwpSocket(
        hostPid: Int,
        suBin: String,
        timeoutMs: Long,
    ): Socket {
        openJdwpSocketCallCount++
        lastPid = hostPid
        return socketResult ?: throw UnsupportedOperationException(
            "FakeRootClient: socketResult is null, can't open real socket"
        )
    }
}
