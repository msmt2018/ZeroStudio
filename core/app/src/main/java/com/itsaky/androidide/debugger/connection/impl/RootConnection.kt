/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  Stub: Root 直连 JDWP
 *  子项目 4 负责实现 (`su -c ...` + /proc/net/unix 探测)。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.BaseDebugConnection
import com.itsaky.androidide.debugger.connection.ConnectionCapability
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ResolveInfo

class RootConnection(
    target: DebugTarget,
    @Suppress("UNUSED_PARAMETER") settings: DebugConnectionSettings,
) : BaseDebugConnection(ConnectionType.Root, target) {

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.CanInstallInHost,
        ConnectionCapability.CanReadProcNet,
    )

    override suspend fun resolve(): Result<ResolveInfo> = notImplemented(4, "resolve")

    override suspend fun connect(): Result<Unit> = notImplemented(4, "connect")

    override suspend fun attach(): Result<AttachInfo> = notImplemented(4, "attach")

    override suspend fun detach() {
        notImplemented(4, "detach")
    }
}
