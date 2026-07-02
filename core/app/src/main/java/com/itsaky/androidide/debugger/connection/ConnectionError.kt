/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ConnectionError: 错误分类,retryable 字段告诉 UI 是不是应该显示"重试"按钮。
 *  retryable = true -> 当前方案内重试(单方案 3 次指数退避)
 *  retryable = false -> 不重试,提示用户去设置切方案
 */

package com.itsaky.androidide.debugger.connection

sealed class ConnectionError(val retryable: Boolean) {

    /** 没拿到 Shizuku 权限 / 没 root / 设备未授权 */
    object PermissionDenied : ConnectionError(retryable = false)

    /** 目标 app 没在前台 / 没运行 */
    object HostAppNotRunning : ConnectionError(retryable = true)

    /** 目标 app 的 AndroidManifest.xml android:debuggable != true */
    object DebugFlagMissing : ConnectionError(retryable = false)

    /** 目标 app 的 BuildConfig.DEBUG != true (非 debug 变体) */
    object BuildConfigNotDebug : ConnectionError(retryable = false)

    /** 通用超时 */
    object Timeout : ConnectionError(retryable = true)

    /** JDWP 14 字节握手失败 */
    object JdwpHandshakeFailed : ConnectionError(retryable = true)

    /** 端口探测失败 (/proc/net/unix / aidl / jdwp-port 文件都没拿到) */
    object PortResolveFailed : ConnectionError(retryable = true)

    /** 网络不可达 (内网 VM / USB/LAN 方案可能遇到) */
    object NetworkUnreachable : ConnectionError(retryable = true)

    /** 通用 IO 失败 */
    data class IoFailure(val cause: Throwable) : ConnectionError(retryable = true)

    /** 兜底 */
    data class Unknown(val cause: Throwable) : ConnectionError(retryable = true)

    /** 给 UI 显示的短描述 */
    fun describe(): String = when (this) {
        PermissionDenied -> "权限不足"
        HostAppNotRunning -> "宿主应用未运行"
        DebugFlagMissing -> "宿主应用 android:debuggable != true"
        BuildConfigNotDebug -> "宿主应用 BuildConfig.DEBUG != true"
        Timeout -> "操作超时"
        JdwpHandshakeFailed -> "JDWP 握手失败"
        PortResolveFailed -> "JDWP 端口解析失败"
        NetworkUnreachable -> "网络不可达"
        is IoFailure -> "IO 失败: ${cause.message ?: cause.javaClass.simpleName}"
        is Unknown -> "未知错误: ${cause.message ?: cause.javaClass.simpleName}"
    }
}
