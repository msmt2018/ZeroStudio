/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  Stub: Shizuku 桥接
 *  子项目 3 负责实现 (4 子路径 A/B/C/D)。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.BaseDebugConnection
import com.itsaky.androidide.debugger.connection.ConnectionCapability
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ResolveInfo

class ShizukuConnection(
    target: DebugTarget,
    @Suppress("UNUSED_PARAMETER") settings: DebugConnectionSettings,
) : BaseDebugConnection(ConnectionType.Shizuku, target) {

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.CanInstallInHost,
        ConnectionCapability.CanReadProcNet,
        ConnectionCapability.CanExposeSocks,
    )

    override suspend fun resolve(): Result<ResolveInfo> = notImplemented(3, "resolve")

    override suspend fun connect(): Result<Unit> = notImplemented(3, "connect")

    override suspend fun attach(): Result<AttachInfo> = notImplemented(3, "attach")

    override suspend fun detach() {
        notImplemented(3, "detach")
    }
}
