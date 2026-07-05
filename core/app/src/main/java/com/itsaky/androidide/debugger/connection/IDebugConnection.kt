/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  IDebugConnection: 5 种连接方式统一接口。
 *
 *  生命周期: Idle -> Resolving -> Connecting -> Handshaking -> Attached -> Closed -> Idle
 *
 *  字节流: attach 成功后,sendJdwp/receiveJdwp 收发 JDWP 帧。
 *
 *  错误处理: 任意阶段失败都会把 state 切到 Closed(error),
 *            UI 据此显示"重试"按钮(单方案内重试, 不跨方案降级)。
 *
 *  线程: 全部挂起函数都不应在 UI 线程调用。
 *        内部自行管理后台线程与协程 scope。
 */

package com.itsaky.androidide.debugger.connection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.net.Socket

interface IDebugConnection {

    val type: ConnectionType

    val target: DebugTarget

    /** 状态机当前状态,UI collectAsState() 即可。 */
    val state: StateFlow<ConnectionState>

    /** 实现自报家门,用来决定 UI 高级选项显示与否。 */
    val capabilities: Set<ConnectionCapability>

    /**
     * 探测阶段: 找到 jdwp 端口 / aidl 端点 / adb 设备。
     * 成功 -> Connecting, 失败 -> Closed(error)。
     */
    suspend fun resolve(): Result<ResolveInfo>

    /**
     * 建链阶段: 建 socket / binder / adb forward 通道。
     * 成功 -> Handshaking, 失败 -> Closed(error)。
     */
    suspend fun connect(): Result<Unit>

    /**
     * 握手 + JDWP attach VM。
     * 成功 -> Attached, 失败 -> Closed(error)。
     */
    suspend fun attach(): Result<AttachInfo>

    /**
     * 正常断开: 走完整 detach 协议。
     */
    suspend fun detach()

    /**
     * 强制释放: 不保证走完协议,通常用于出错路径或 release 后再 new。
     * 调完后 state = Idle,可以再次 resolve()。
     */
    fun release()

    // -------- JDWP 字节流(只有 state == Attached 时才能用) --------

    /** 发送一帧 JDWP 字节;只能在 Attached 状态调用,否则抛 IllegalStateException。 */
    suspend fun sendJdwp(bytes: ByteArray)

    /** 接收 JDWP 字节流;只有 Attached 状态才有数据。 */
    fun receiveJdwp(): Flow<ByteArray>

    // -------- 集成钩子(给 ConnectionBackedDebugger 用) --------

    /**
     * Attached 状态时返回用于 JDWP 通信的底层 Socket。
     * 默认抛 UnsupportedOperationException,只有真正用 socket 作为传输
     * (AIDL Socket / Shizuku C / Root / InnetVmAdb / UsbLan) 的实现才
     * override。
     *
     * <p>返回的 Socket 由 IDebugConnection 拥有,调 detach()/release() 时
     * 负责关闭。调用方不得自行 close。
     */
    fun attachedSocket(): Socket =
        throw UnsupportedOperationException("attachedSocket not supported for $type")
}
