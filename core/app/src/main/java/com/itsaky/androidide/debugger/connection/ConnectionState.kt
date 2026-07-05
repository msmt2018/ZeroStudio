/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ConnectionState: 6 状态状态机。
 *  UI 端只 collectAsState() 即可;debug 按钮在 Attached 之前禁用。
 */

package com.itsaky.androidide.debugger.connection

sealed class ConnectionState {

    /** 初始/已释放,等待下一次 start() */
    object Idle : ConnectionState()

    /** 探测阶段:找 jdwp 端口 / aidl 端点 / adb 设备 */
    object Resolving : ConnectionState()

    /** 建链阶段:建 socket / binder / adb forward */
    object Connecting : ConnectionState()

    /** 握手阶段:JDWP 14 字节 handshake + VM.Version */
    object Handshaking : ConnectionState()

    /** 已成功 attach 到目标 VM,可以收发 JDWP 包 */
    data class Attached(
        val pid: Int,
        val jdwpSessionId: Long,
    ) : ConnectionState()

    /** 已关闭;error == null 表示正常 detach,非 null 表示带错关闭 */
    data class Closed(
        val error: ConnectionError? = null,
    ) : ConnectionState()

    /**
     * 是否处于"占用"状态 (Idle 之外都属于占用,UI 应禁用调试动作)。
     * Closed 也算占用因为 detached 后还在内存里,等用户调 release()。
     */
    fun isActive(): Boolean = this !is Idle
}
