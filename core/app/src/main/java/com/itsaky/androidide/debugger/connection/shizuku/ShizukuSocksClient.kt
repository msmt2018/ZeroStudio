/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuSocksClient: Shizuku Socks 路径专用, SOCKS5 客户端协议实现。
 *
 *  流程:
 *    1. IDE 端用 Shizuku newProcess 启动一个 SOCKS5 server (在 Shizuku server
 *       进程), 监听某个本地端口, 接受 IDE 端 SOCKS5 连接后通过 host JDWP
 *       socket 转发 (走 Shizuku 自己 root 权限访问 host 进程 fd)。
 *    2. IDE 端用 SOCKS5 协议连到 SOCKS5 server:
 *       - 客户端问候: [VER=05] [NMETHODS=01] [METHODS=00 (no auth)]
 *       - 服务端响应: [VER=05] [METHOD=00]
 *       - 客户端请求: [VER=05 CMD=01 RSV=00 ATYP=01 ADDR=127.0.0.1 PORT=<host_jdwp_port>]
 *       - 服务端响应: [VER=05 REP=00 RSV=00 ATYP=01 BND_ADDR=... BND_PORT=...]
 *    3. 之后就是内层的 JDWP 字节流 (SOCKS5 不再解析, 仅做转发)
 *
 *  本类只做 IDE 端的 SOCKS5 客户端, 不实现 SOCKS5 server (那是 Shizuku
 *  server 进程里的代码, 在子项目 8 host runtime 一起提供)。
 *
 *  IPv4 only (ATYP=01); 简化实现, 不支持 IPv6 (ATYP=04) / domain (ATYP=03)。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import com.itsaky.androidide.utils.ILogger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * SOCKS5 客户端。
 */
class ShizukuSocksClient(
    private val log: ILogger = ILogger.ROOT,
) {

    /**
     * 通过 SOCKS5 代理连到目标 host:port。
     *
     * @param proxyAddr SOCKS5 server 地址 (Shizuku newProcess 启动的)
     * @param targetHost 目标 host app JDWP 端口对应的 "local" 地址 (SOCKS5 server 知道怎么转发)
     * @param targetPort 目标端口
     * @param connectTimeoutMs 建链超时
     * @return 已经完成 SOCKS5 握手的 Socket, 内层流量走 JDWP
     */
    @Throws(IOException::class)
    fun connect(
        proxyAddr: InetSocketAddress,
        targetHost: String,
        targetPort: Int,
        connectTimeoutMs: Int = 5_000,
    ): Socket {
        val socket = Socket()
        socket.connect(proxyAddr, connectTimeoutMs)
        try {
            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            // 1) 客户端问候: VER=05 NMETHODS=01 METHODS=00
            out.writeByte(0x05)
            out.writeByte(0x01)
            out.writeByte(0x00) // no auth
            out.flush()
            // 2) 服务端响应: VER=05 METHOD=00
            val ver = input.readUnsignedByte()
            val method = input.readUnsignedByte()
            require(ver == 0x05) { "SOCKS5 server returned version=$ver (expected 5)" }
            require(method == 0x00) {
                "SOCKS5 server returned method=$method (no-auth only supported)"
            }
            // 3) 客户端连接请求: VER CMD RSV ATYP ADDR PORT
            out.writeByte(0x05) // VER
            out.writeByte(0x01) // CMD=CONNECT
            out.writeByte(0x00) // RSV
            // ATYP: IPv4 (1) or domain (3)
            val parts = targetHost.split(".").mapNotNull { it.toIntOrNull() }
            val isIpv4 = parts.size == 4 && parts.all { it in 0..255 }
            if (isIpv4) {
                out.writeByte(0x01) // ATYP=IPv4
                for (p in parts) {
                    out.writeByte(p and 0xff)
                }
            } else {
                val bytes = targetHost.toByteArray(Charsets.US_ASCII)
                require(bytes.size <= 255) { "SOCKS5 domain too long: ${bytes.size}" }
                out.writeByte(0x03) // ATYP=domain
                out.writeByte(bytes.size)
                out.write(bytes)
            }
            out.writeShort(targetPort and 0xffff)
            out.flush()
            // 4) 服务端响应: VER REP RSV ATYP BND_ADDR BND_PORT
            val rver = input.readUnsignedByte()
            val rep = input.readUnsignedByte()
            input.readUnsignedByte() // RSV
            val atyp = input.readUnsignedByte()
            // 读 BND_ADDR
            when (atyp) {
                0x01 -> { // IPv4
                    input.readFully(ByteArray(4))
                }
                0x03 -> { // domain
                    val len = input.readUnsignedByte()
                    input.readFully(ByteArray(len))
                }
                0x04 -> { // IPv6
                    input.readFully(ByteArray(16))
                }
                else -> throw IOException("SOCKS5: unknown ATYP=$atyp")
            }
            input.readShort() // BND_PORT
            if (rver != 0x05) {
                throw IOException("SOCKS5 response version=$rver (expected 5)")
            }
            if (rep != 0x00) {
                val msg = socks5ErrorMessage(rep)
                throw IOException("SOCKS5 CONNECT failed: REP=$rep ($msg)")
            }
            log.info("ShizukuSocksClient: connected via SOCKS5 to {}:{}", targetHost, targetPort)
            return socket
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    private fun socks5ErrorMessage(rep: Int): String = when (rep) {
        0x01 -> "general SOCKS server failure"
        0x02 -> "connection not allowed by ruleset"
        0x03 -> "Network unreachable"
        0x04 -> "Host unreachable"
        0x05 -> "Connection refused"
        0x06 -> "TTL expired"
        0x07 -> "Command not supported"
        0x08 -> "Address type not supported"
        else -> "unknown error"
    }
}
