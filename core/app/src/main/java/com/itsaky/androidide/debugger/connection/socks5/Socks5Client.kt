/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  Socks5Client: 通用 SOCKS5 客户端协议实现 (RFC 1928)。
 *
 *  用法:
 *    val client = Socks5Client()
 *    val socket = client.connect(
 *        proxyAddr = InetSocketAddress("127.0.0.1", 1080),
 *        targetHost = "10.0.0.1",
 *        targetPort = 5005,
 *    )
 *    // socket 已完成 SOCKS5 握手, 内层流量是 JDWP
 *
 *  支持:
 *    - no-auth method (0x00) only
 *    - IPv4 (ATYP=01) + domain (ATYP=03) addresses
 *    - 错误 REP code (01-08) -> IOException
 *
 *  不支持:
 *    - auth (username/password 0x02)
 *    - IPv6 (ATYP=04) (简化实现)
 *    - BIND / UDP ASSOCIATE 命令 (只用 CONNECT)
 */

package com.itsaky.androidide.debugger.connection.socks5

import com.itsaky.androidide.utils.ILogger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class Socks5Client(
    private val log: ILogger = ILogger.ROOT,
) {

    /**
     * SOCKS5 协议握手阶段的 read 超时 (ms)。握手完成 (REP=0 收到后) 立即
     * 重置回 0 (无限), 让上层 JDWP 流量正常用阻塞 read 等 IDE 命令。
     */
    private val handshakeReadTimeoutMs: Int = 5_000

    /**
     * 通过 SOCKS5 代理连到目标 host:port。
     *
     * @param proxyAddr SOCKS5 server 地址
     * @param targetHost 目标 host (IPv4 dotted-quad 或 domain name)
     * @param targetPort 目标端口
     * @param connectTimeoutMs TCP 建链超时
     * @return 已经完成 SOCKS5 握手的 Socket, 内层流量直接是目标协议
     */
    @Throws(IOException::class)
    fun connect(
        proxyAddr: InetSocketAddress,
        targetHost: String,
        targetPort: Int,
        connectTimeoutMs: Int = 5_000,
    ): Socket {
        // 允许 targetPort = 0: 按 RFC 1928, port 是 16-bit, 0 是保留值, SOCKS5 server
        // 收到 0x0000 可视作 "server-side routing" (client 不关心实际端口, 由 SOCKS5
        // server 自行决定转发到哪个 host 端口)。InnetVmSocksConnection 路径下
        // SOCKS5 server 固定转发到 host:jdwp, client 写 0 即可。
        // (之前 require 1..65535 太严格, 把 InnetVmSocks 路径直接挡在 IllegalArgumentException)
        require(targetPort in 0..65535) { "targetPort out of range: $targetPort" }
        val socket = Socket()
        socket.connect(proxyAddr, connectTimeoutMs)
        // 握手阶段 read 超时保护: 防止 host 端 SOCKS5 server 半死导致无限阻塞。
        // 完成后 (REP=0 收到后) 重置回 0, 让上层 JDWP 流量正常阻塞 read。
        socket.soTimeout = handshakeReadTimeoutMs
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
            // IPv4 检测: 必须 split 出 4 段且每段都能解析为 0..255
            // 之前实现只检查 toIntOrNull 后的 parts 数, 遇到 "127.0.0.1.foo"
            // (split 5 段, "foo" 解析失败, parts.size=4) 会被误判为 IPv4
            // 并丢掉 "foo" 一段; 改用 split 后的 raw 数 + 每段都能解析。
            val splitParts = targetHost.split(".")
            val isIpv4 = splitParts.size == 4 &&
                splitParts.all { it.toIntOrNull()?.let { v -> v in 0..255 } == true }
            if (isIpv4) {
                out.writeByte(0x01) // ATYP=IPv4
                for (p in splitParts) {
                    out.writeByte(p.toInt() and 0xff)
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
            when (atyp) {
                0x01 -> input.readFully(ByteArray(4))
                0x03 -> {
                    val len = input.readUnsignedByte()
                    input.readFully(ByteArray(len))
                }
                0x04 -> input.readFully(ByteArray(16))
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
            // 握手完成, 重置 read timeout 回 0 (无限), 让上层 JDWP 流量正常用阻塞 read
            socket.soTimeout = 0
            log.info("Socks5Client: connected via SOCKS5 to {}:{}", targetHost, targetPort)
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
