/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  Stub: AIDL + Socket (免 root)
 *  子项目 2 负责实现,本类只在 registry 调用时不阻塞编译。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.BaseDebugConnection
import com.itsaky.androidide.debugger.connection.ConnectionCapability
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ResolveInfo

class AidlSocketConnection(
    target: DebugTarget,
    @Suppress("UNUSED_PARAMETER") settings: DebugConnectionSettings,
) : BaseDebugConnection(ConnectionType.AidlSocket, target) {

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.NeedsHostForeground,
    )

    override suspend fun resolve(): Result<ResolveInfo> = notImplemented(2, "resolve")

    override suspend fun connect(): Result<Unit> = notImplemented(2, "connect")

    override suspend fun attach(): Result<AttachInfo> = notImplemented(2, "attach")

    override suspend fun detach() {
        notImplemented(2, "detach")
    }
}
