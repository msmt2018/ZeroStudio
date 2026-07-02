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
 */

package com.itsaky.androidide.zerostudio.ide.debugger.host

import java.io.IOException
import java.net.Socket
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log

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

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("usage: HostAttachAgent <ideLocalServerSocketName>")
            exitProcess(2)
        }
        val ideSocketName = args[0]
        Log.i(TAG, "starting, ide socket=$ideSocketName")

        val ideSocket = try {
            connectToIdeLocalServer(ideSocketName)
        } catch (t: Throwable) {
            System.err.println("failed to connect to IDE LocalServerSocket '$ideSocketName': ${t.message}")
            exitProcess(3)
        }

        val jdwpSocket = try {
            openLocalAbstractJdwpSocket()
        } catch (t: Throwable) {
            System.err.println("failed to open localabstract:jdwp self socket: ${t.message}")
            runCatching { ideSocket.close() }
            exitProcess(4)
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
        exitProcess(0)
    }

    /**
     * Connect to IDE's LocalServerSocket. Uses Android's [LocalSocket] which
     * talks the Linux abstract UNIX domain socket protocol that
     * [android.net.LocalServerSocket] binds.
     */
    @Throws(IOException::class)
    fun connectToIdeLocalServer(name: String): LocalSocket {
        val sock = LocalSocket()
        sock.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
        return sock
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
    fun openLocalAbstractJdwpSocket(): LocalSocket {
        val sock = LocalSocket()
        sock.connect(LocalSocketAddress("jdwp", LocalSocketAddress.Namespace.ABSTRACT))
        return sock
    }

    /**
     * Bidirectional byte-forwarding bridge between [ide] and [jdwp].
     *
     * Forwarding 线程:
     *   - thread A: ide.inputStream -> jdwp.outputStream  (IDE -> host VM)
     *   - thread B: jdwp.inputStream -> ide.outputStream  (host VM -> IDE)
     *
     * 任何一侧 close / EOF, 关掉另一侧, join 线程, 返回。
     */
    @Throws(IOException::class)
    fun bridgeBytes(ide: LocalSocket, jdwp: LocalSocket) {
        val a = Thread({
            pump(ide.inputStream, jdwp.outputStream, "ide->jdwp")
        }, "HostAttachAgent-ide2jdwp").apply { isDaemon = false; start() }
        val b = Thread({
            pump(jdwp.inputStream, ide.outputStream, "jdwp->ide")
        }, "HostAttachAgent-jdwp2ide").apply { isDaemon = false; start() }
        // 阻塞主线程直到任一转发线程结束
        a.join()
        runCatching { ide.close() }
        runCatching { jdwp.close() }
        b.join(2000)
    }

    /**
     * Overload: 接受 InputStream/OutputStream, 供 HostSocksServer 等不走
     * LocalSocket 的场景使用。
     */
    @Throws(IOException::class)
    fun bridgeBytes(ideIn: java.io.InputStream, ideOut: java.io.OutputStream, jdwpIn: java.io.InputStream, jdwpOut: java.io.OutputStream) {
        val a = Thread({
            pump(ideIn, jdwpOut, "ide->jdwp")
        }, "HostAttachAgent-ide2jdwp").apply { isDaemon = false; start() }
        val b = Thread({
            pump(jdwpIn, ideOut, "jdwp->ide")
        }, "HostAttachAgent-jdwp2ide").apply { isDaemon = false; start() }
        a.join()
        runCatching { ideOut.close() }
        runCatching { jdwpOut.close() }
        b.join(2000)
    }

    private fun pump(`in`: java.io.InputStream, out: java.io.OutputStream, name: String) {
        val buf = ByteArray(8192)
        try {
            while (true) {
                val n = `in`.read(buf)
                if (n < 0) {
                    Log.i(TAG, "$name EOF")
                    return
                }
                out.write(buf, 0, n)
                out.flush()
            }
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

    private fun exitProcess(code: Int) {
        kotlin.system.exitProcess(code)
    }
}
