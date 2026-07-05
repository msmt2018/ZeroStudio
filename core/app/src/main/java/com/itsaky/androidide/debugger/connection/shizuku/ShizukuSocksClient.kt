/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuSocksClient: Shizuku 方案的 Socks 子路径专用。
 *  底层委托给通用 [com.itsaky.androidide.debugger.connection.socks5.Socks5Client],
 *  保留这个类是为了 ShizukuConnection 里的注入签名稳定 (后续可能加 Socks5
 *  路径专属逻辑, 比如 requireShizuku 预检查)。
 *
 *  本类不再独立实现 SOCKS5 协议, 全部走 Socks5Client。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import com.itsaky.androidide.debugger.connection.socks5.Socks5Client
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class ShizukuSocksClient {
    private val client = Socks5Client()

    @Throws(IOException::class)
    fun connect(
        proxyAddr: InetSocketAddress,
        targetHost: String,
        targetPort: Int,
        connectTimeoutMs: Int = 5_000,
    ): Socket = client.connect(proxyAddr, targetHost, targetPort, connectTimeoutMs)
}
