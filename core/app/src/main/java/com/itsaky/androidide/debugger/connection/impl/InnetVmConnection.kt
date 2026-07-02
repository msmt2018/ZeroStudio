/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  Stub: 内网虚拟机 (光速虚拟机 / vmos / 虚拟大师)
 *  子项目 5 负责实现 (SOCKS5 代理 + 网络 adb)。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.BaseDebugConnection
import com.itsaky.androidide.debugger.connection.ConnectionCapability
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ResolveInfo

class InnetVmConnection(
    target: DebugTarget,
    @Suppress("UNUSED_PARAMETER") settings: DebugConnectionSettings,
) : BaseDebugConnection(ConnectionType.InnetVm, target) {

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
