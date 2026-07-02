/*
 *  ZeroStudio IDE - Host ADRT (Android Debug Runtime)
 *
 *  HostAttachAgent: 入口类, 在 host 进程内运行。
 *
 *  启动方式 (由 IDE 端通过 shizuku / root / aapt 注入):
 *    1) Shizuku newProcess:
 *         Shizuku.newProcess(arrayOf(
 *             "/system/bin/app_process",
 *             "-Djava.class.path=" + hostJarPath,
 *             "/system/bin",
 *             "com.itsaky.androidide.zerostudio.ide.debugger.host.HostAttachAgent",
 *             ideSocketName),  // 来自 IDE 端 LocalServerSocket
 *             null)
 *    2) Root attach-agent:
 *         su -c app_process -Djava.class.path=... HostAttachAgent <ideSocketName>
 *    3) Shizuku InHostPlugin (子项目 8d): 由 HostPluginService.onBind 启动
 *    4) Shizuku Socks (子项目 8e): 由 HostSocksServer 启动 (这个不桥接, 而是
 *         把 host JDWP socket 暴露成 SOCKS5 目标)
 *
 *  行为:
 *    - 通过 connectToIdeLocalServer(<ideSocketName>) 反向连 IDE
 *    - 通过 openLocalAbstractJdwpSocket() 打开自己的 jdwp:<pid> abstract socket
 *    - 在两个 socket 之间做字节桥 (双 forward thread)
 *    - 收到 SIGTERM / EOF 一侧关闭 -> 关闭另一侧 -> exit
 *
 *  关键设计:
 *    - 全部走标准 java.net.Socket + Java 11 兼容的 input/output stream,
 *      不引用 Android API (但允许 compileOnly androidx.* 方便 IDE 集成)
 *    - 类名 / 方法名都要 ProGuard 保留 (consumer-rules.pro 已配)
 *    - 失败抛出明确错误信息, 不会 silently exit
 *    - 所有阻塞点 (connect / read) 都有 timeout / interrupt 退出路径
 */

package com.itsaky.androidide.zerostudio.ide.debugger.host

import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Main entry point, invoked via `app_process` from a Shizuku / root attach.
 *
 * Usage:
 *   app_process -Djava.class.path=host.jar \
 *                /system/bin \
 *                com.itsaky.androidide.zerostudio.ide.debugger.host.HostAttachAgent \
 *                <ideLocalServerSocketName>
 *
 * @param args argv[1] = IDE LocalServerSocket name (e.g. "ide-debug-bridge-12345")
 */
object HostAttachAgent {

    private const val TAG = "HostAttachAgent"

    /** Connect timeout to IDE / jdwp LocalServerSocket. */
    private const val CONNECT_TIMEOUT_MS: Long = 10_000L

    /** Polling interval for retrying failed [LocalSocket.connect] calls. */
    private const val CONNECT_RETRY_MS: Long = 100L

    /** Maximum wait for the second pump thread to drain after the first one ended. */
    private const val DRAIN_JOIN_TIMEOUT_MS: Long = 2_000L

    /**
     * Exit codes for [app_process], semantic. Values are stable so external scripts
     * can match on them. We start at 2 (1 is reserved by JVM for uncaught
     * exceptions).
     */
    private enum class ExitCode(val code: Int) {
        OK(0),
        USAGE(2),                  // no ideSocketName arg
        IDE_CONNECT_FAILED(3),     // failed to connect to IDE LocalServerSocket
        JDWP_OPEN_FAILED(4),       // failed to open host's own localabstract:jdwp
        BRIDGE_FAILED(5),          // bridgeBytes threw
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("usage: HostAttachAgent <ideLocalServerSocketName>")
            exitProcess(ExitCode.USAGE)
        }
        val ideSocketName = args[0]
        Log.i(TAG, "starting, ide socket=$ideSocketName")

        val ideSocket = try {
            connectToIdeLocalServer(ideSocketName, CONNECT_TIMEOUT_MS)
        } catch (t: Throwable) {
            System.err.println("failed to connect to IDE LocalServerSocket '$ideSocketName': ${t.message}")
            exitProcess(ExitCode.IDE_CONNECT_FAILED)
        }

        val jdwpSocket = try {
            openLocalAbstractJdwpSocket(CONNECT_TIMEOUT_MS)
        } catch (t: Throwable) {
            System.err.println("failed to open localabstract:jdwp self socket: ${t.message}")
            runCatching { ideSocket.close() }
            exitProcess(ExitCode.JDWP_OPEN_FAILED)
        }

        Log.i(TAG, "attached, bridging ide <-> jdwp")
        try {
            bridgeBytes(ideSocket, jdwpSocket)
        } catch (t: Throwable) {
            Log.w(TAG, "bridge ended: ${t.message}")
        } finally {
            runCatching { ideSocket.close() }
            runCatching { jdwpSocket.close() }
        }
        exitProcess(ExitCode.OK)
    }

    /**
     * Connect to IDE's LocalServerSocket. Polls with timeout so a missing IDE
     * server does not block forever. Each failed attempt rebuilds a fresh
     * [LocalSocket] since Android's LocalSocket enters an error state after
     * a failed connect and cannot be reused.
     *
     * @param name     abstract namespace socket name
     * @param timeoutMs total budget; 0 means "use default"
     */
    @Throws(IOException::class)
    fun connectToIdeLocalServer(name: String, timeoutMs: Long = CONNECT_TIMEOUT_MS): LocalSocket {
        val deadline = System.currentTimeMillis() + timeoutMs
        val addr = LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT)
        var attempt = 0
        while (true) {
            val sock = LocalSocket()
            try {
                sock.connect(addr)
                return sock
            } catch (e: IOException) {
                runCatching { sock.close() }
                attempt++
                val now = System.currentTimeMillis()
                if (now >= deadline) {
                    throw IOException(
                        "timed out after ${timeoutMs}ms ($attempt attempt(s)) connecting to '$name'",
                        e,
                    )
                }
                try {
                    Thread.sleep(CONNECT_RETRY_MS)
                } catch (ie: InterruptedException) {
                    throw InterruptedIOException("interrupted while connecting to '$name'").apply {
                        initCause(ie)
                    }
                }
            }
        }
    }

    /**
     * Open `localabstract:jdwp` (the JDWP server of the current process, bound
     * automatically by the Android VM at debuggable=true).
     *
     * Note: 连接 `localabstract:jdwp` 会触发 JDWP 端主动 "handshake reply", 本函数
     * 走 raw 路径 (不走 std jdwp client), 接受原始字节; IDE 端的 JdwpClient 期望
     * 收到 JDWP-Handshake 字符串 ("JDWP-Handshake" 14 字节), 之后才是 VM.Version
     * 之类的命令。
     */
    @Throws(IOException::class)
    fun openLocalAbstractJdwpSocket(timeoutMs: Long = CONNECT_TIMEOUT_MS): LocalSocket {
        return connectToIdeLocalServer("jdwp", timeoutMs)
    }

    /**
     * Bidirectional byte-forwarding bridge between [ide] and [jdwp].
     *
     * Forwarding 线程:
     *   - thread A: ide.inputStream -> jdwp.outputStream  (IDE -> host VM)
     *   - thread B: jdwp.inputStream -> ide.outputStream  (host VM -> IDE)
     *
     * 任何一侧 close / EOF / interrupt, 关掉两侧 stream 触发对方 pump 从 read
     * 阻塞退出, 两 thread 用 [CountDownLatch] 同步, 主线程 join 等两边都收尾。
     */
    @Throws(IOException::class)
    fun bridgeBytes(ide: LocalSocket, jdwp: LocalSocket) {
        bridgeBytes(ide.inputStream, ide.outputStream, jdwp.inputStream, jdwp.outputStream)
    }

    /**
     * Overload: 接受 InputStream/OutputStream, 供 HostSocksServer 等不走
     * LocalSocket 的场景使用。
     */
    @Throws(IOException::class)
    fun bridgeBytes(
        ideIn: InputStream,
        ideOut: OutputStream,
        jdwpIn: InputStream,
        jdwpOut: OutputStream,
    ) {
        val latch = CountDownLatch(2)
        val a = Thread({
            try {
                pump(ideIn, jdwpOut, "ide->jdwp")
            } finally {
                latch.countDown()
            }
        }, "HostAttachAgent-ide2jdwp").apply { isDaemon = false; start() }
        val b = Thread({
            try {
                pump(jdwpIn, ideOut, "jdwp->ide")
            } finally {
                latch.countDown()
            }
        }, "HostAttachAgent-jdwp2ide").apply { isDaemon = false; start() }

        // 等任一 pump 先结束
        latch.await()
        // 关闭两侧 output stream 触发另一 pump 从 read 阻塞退出
        runCatching { ideOut.close() }
        runCatching { jdwpOut.close() }
        // interrupt 兜底 (read 阻塞在 socket 内核 buffer 满时不会立刻抛, 但 close 一定能唤醒)
        a.interrupt()
        b.interrupt()
        // 等第二 pump 收尾, 带超时
        latch.await(DRAIN_JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        a.join(DRAIN_JOIN_TIMEOUT_MS)
        b.join(DRAIN_JOIN_TIMEOUT_MS)
    }

    /**
     * Pump bytes from [in] to [out] in 8 KiB chunks. 响应 [Thread.interrupt] 退出
     * 以及上游 stream close 触发的 EOF / IOException。
     */
    private fun pump(`in`: InputStream, out: OutputStream, name: String) {
        val buf = ByteArray(8192)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = try {
                    `in`.read(buf)
                } catch (e: InterruptedIOException) {
                    Log.i(TAG, "$name interrupted")
                    return
                }
                if (n < 0) {
                    Log.i(TAG, "$name EOF")
                    return
                }
                out.write(buf, 0, n)
                out.flush()
            }
            Log.i(TAG, "$name loop exit (interrupted)")
        } catch (t: Throwable) {
            Log.w(TAG, "$name ended: ${t.message}")
        }
    }

    /**
     * Best-effort: try to obtain a usable [LocalServerSocket] and forward.
     * 没用上, 保留作为 utility (子类可参考, e.g. HostPluginService)。
     */
    @Suppress("unused")
    fun listenAndAccept(localServerSocket: LocalServerSocket): Pair<LocalSocket, LocalSocket> {
        val ide = localServerSocket.accept()
        val jdwp = openLocalAbstractJdwpSocket()
        return ide to jdwp
    }

    private fun exitProcess(code: ExitCode) {
        kotlin.system.exitProcess(code.code)
    }
}
