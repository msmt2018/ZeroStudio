/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  InnetVmSocksConnection: 内网虚拟机 SOCKS5 代理方案 stub。
 *  子项目 5 负责实现。技术栈: JSOCKS / Apache MINA SSHD 等 SOCKS 客户端
 *  + jdwp-tunnel over SOCKS。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.BaseDebugConnection
import com.itsaky.androidide.debugger.connection.ConnectionCapability
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ResolveInfo

class InnetVmSocksConnection(
    target: DebugTarget,
    @Suppress("UNUSED_PARAMETER") settings: DebugConnectionSettings,
) : BaseDebugConnection(ConnectionType.InnetVmSocks, target) {

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.CanExposeSocks,
    )

    override suspend fun resolve(): Result<ResolveInfo> = notImplemented(5, "resolve")

    override suspend fun connect(): Result<Unit> = notImplemented(5, "connect")

    override suspend fun attach(): Result<AttachInfo> = notImplemented(5, "attach")

    override suspend fun detach() {
        notImplemented(5, "detach")
    }
}
