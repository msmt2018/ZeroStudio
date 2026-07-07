/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuFdTransporter: Shizuku Binder 路径专用, 把 host 进程的 JDWP socket
 *  FileDescriptor 转成 IDE 端可用的 java.net.Socket。
 *
 *  步骤:
 *    1. IDE 端用 Shizuku newProcess 跑一个 root 进程 (子项目 8 host runtime 配合),
 *       该进程 attach host app 的 JDWP agent, open /proc/<host_pid>/fd/<jdwp_socket>
 *       得到一个 FileDescriptor。
 *    2. 这个 FileDescriptor 通过 Shizuku binder 转回 IDE 端 (走
 *       ShizukuBinderClient.transferFileDescriptor)。
 *    3. IDE 端把 ParcelFileDescriptor 包成 java.net.Socket (用 socket adaptor:
 *       文件描述符本身就是 socket fd, 包成 Socket 即可用)。
 *
 *  Android 24+ 提供 [java.net.Socket(ParcelFileDescriptor)] 不存在, 但可用
 *  [java.nio.channels.SocketChannel.open(StandardProtocolFamily.UNIX)] 或者
 *  自己包一个 SocketImpl。简化方案: 用 java.io.FileInputStream / FileOutputStream
 *  包 fd, 然后用我们自己的 Socket 适配器 (JdkSocketAdapter) 暴露 getInputStream
 *  / getOutputStream 给 JdwpClient。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import android.os.ParcelFileDescriptor
import com.itsaky.androidide.utils.ILogger
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketOption
import java.nio.channels.SocketChannel

/**
 * FD 传输器抽象: ParcelFileDescriptor (host 进程视角) -> IDE 端 Socket。
 */
interface ShizukuFdTransporter {
    /**
     * 把 host 进程的 fd 包成 IDE 端 java.net.Socket。
     * @param pfd 来自 Shizuku binder 的 ParcelFileDescriptor
     * @return 不为 null 的 Socket, 调用方负责 close()
     */
    fun toSocket(pfd: ParcelFileDescriptor): Socket

    companion object {
        @JvmStatic
        fun create(): ShizukuFdTransporter = DefaultShizukuFdTransporter()
    }
}

/**
 * 默认生产实现: 用 SocketChannel 包装 fd + 暴露 Socket 适配器。
 *
 * 实现思路: ParcelFileDescriptor.fileDescriptor 是底层 FileDescriptor,
 * 对 socket fd 来说, 可以用 SocketChannel.open(SocketChannel) + 关联到 fd,
 * 然后用 SocketChannel.socket() 拿到 Socket。
 *
 * 但 SocketChannel.open 不支持已有 fd; 替代方案: 自己包一个 SocketImpl
 * (继承 AbstractPlainSocketImpl), 不过那是 internal API, 兼容性差。
 *
 * 最稳妥: 用 java.io.FileInputStream / FileOutputStream 包 fd, 然后
 * 用 [PfdSocket] 这个自实现 Socket 子类暴露 input/output stream。
 */
class DefaultShizukuFdTransporter : ShizukuFdTransporter {
    override fun toSocket(pfd: ParcelFileDescriptor): Socket = PfdSocket(pfd)
}

/**
 * 一个最小的 Socket 子类, 包一个 ParcelFileDescriptor 暴露的 fd。
 * JdwpClient 只用到 getInputStream / getOutputStream / close, 不需要真的
 * 实现 connect/bind 那些网络操作。
 */
class PfdSocket(private val pfd: ParcelFileDescriptor) : Socket() {

    private val log = ILogger.ROOT
    @Volatile private var closed = false
    private val rawFd: FileDescriptor = pfd.fileDescriptor

    private val input by lazy { FileInputStream(rawFd) }
    private val output by lazy { FileOutputStream(rawFd) }

    override fun getInputStream(): java.io.InputStream = input
    override fun getOutputStream(): java.io.OutputStream = output
    override fun isConnected(): Boolean = !closed
    override fun isClosed(): Boolean = closed
    override fun isBound(): Boolean = true

    override fun connect(endpoint: SocketAddress?) {
        // host fd 已经连好, 不需要 IDE 再 connect
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        connect(endpoint)
    }

    override fun bind(endpoint: SocketAddress?) {
        // 不需要
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { pfd.close() }
    }

    // 下面这些 JdwpClient 不调用, 留 throw 让调试时知道出了问题
    override fun getChannel(): SocketChannel = throw UnsupportedOperationException("PfdSocket: no channel")
    override fun <T> getOption(name: SocketOption<T>): T =
        throw UnsupportedOperationException("PfdSocket: getOption")
    override fun <T> setOption(name: SocketOption<T>, value: T): Socket =
        throw UnsupportedOperationException("PfdSocket: setOption")

    private fun ensureOpen() {
        if (closed) throw IOException("PfdSocket is closed")
    }
}
