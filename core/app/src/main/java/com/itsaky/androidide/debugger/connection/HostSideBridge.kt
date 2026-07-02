/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  HostSideBridge: Shizuku C 路径专用。
 *  Shizuku 把 stub 注入到宿主进程,stub 起来后会反向连 IDE,
 *  IDE 端通过 awaitBridgeChannel() 拿到这条 Socket。
 */

package com.itsaky.androidide.debugger.connection

import java.net.Socket

interface HostSideBridge {

    /**
     * 等待宿主内 stub 反向连入。超时由调用方控制(用 withTimeout 包裹)。
     * 返回的 Socket 已 attach 到 JDWP 流。
     */
    suspend fun awaitBridgeChannel(): Socket

    /**
     * stub 上报的宿主进程元信息。
     */
    fun isAlive(): Boolean
}
