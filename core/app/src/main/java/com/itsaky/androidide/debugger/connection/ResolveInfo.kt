/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  辅助类型: resolve / attach 阶段的返回值。
 */

package com.itsaky.androidide.debugger.connection

/**
 * resolve() 阶段的产物: 找到的传输端点描述。
 *
 * @param transportKind "tcp" / "unix" / "aidl" / "binder" / "adb-forward"
 * @param endpoint "127.0.0.1:5005" / "@jdwp-1234" / "binder:0x12" / "adb:serial:5005"
 * @param requiresHostRunning 是否要求宿主 app 在前台
 */
data class ResolveInfo(
    val transportKind: String,
    val endpoint: String,
    val requiresHostRunning: Boolean = false,
)

/**
 * attach() 阶段的产物: 成功 attach 到目标 VM 后的描述。
 */
data class AttachInfo(
    val pid: Int,
    val jdwpSessionId: Long,
    val jdwpDescription: String,
)
