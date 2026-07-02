/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  InnetVmAdbConnection: 内网虚拟机 ADB 端口转发方案 stub。
 *  子项目 5 负责实现。技术栈: adb 命令 (Runtime.exec / Shizuku / Root)
 *  起 `adb connect host:port` + `adb forward tcp:local jdwp:remote`。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.BaseDebugConnection
import com.itsaky.androidide.debugger.connection.ConnectionCapability
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ResolveInfo

class InnetVmAdbConnection(
    target: DebugTarget,
    @Suppress("UNUSED_PARAMETER") settings: DebugConnectionSettings,
) : BaseDebugConnection(ConnectionType.InnetVmAdb, target) {

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
