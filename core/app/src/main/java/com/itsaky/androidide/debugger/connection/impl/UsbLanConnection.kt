/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  Stub: USB / 局域网 ADB
 *  子项目 5 负责实现 (`adb connect ip:port` + adb forward)。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.BaseDebugConnection
import com.itsaky.androidide.debugger.connection.ConnectionCapability
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ResolveInfo

class UsbLanConnection(
    target: DebugTarget,
    @Suppress("UNUSED_PARAMETER") settings: DebugConnectionSettings,
) : BaseDebugConnection(ConnectionType.UsbLan, target) {

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.CanReadProcNet,
    )

    override suspend fun resolve(): Result<ResolveInfo> = notImplemented(5, "resolve")

    override suspend fun connect(): Result<Unit> = notImplemented(5, "connect")

    override suspend fun attach(): Result<AttachInfo> = notImplemented(5, "attach")

    override suspend fun detach() {
        notImplemented(5, "detach")
    }
}
