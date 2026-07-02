/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  BaseDebugConnection: 5 种实现共享的状态机管理。
 *  子类只需要在合适时机调用 transitionTo(newState)。
 *
 *  注: 字节流的 send/receive 抽象方法留空实现 + 抛 NotImplementedError,
 *       真正的实现放在子项目 2~5。
 */

package com.itsaky.androidide.debugger.connection

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseDebugConnection(
    override val type: ConnectionType,
    override val target: DebugTarget,
) : IDebugConnection {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    protected fun currentState(): ConnectionState = _state.value

    protected fun transitionTo(newState: ConnectionState) {
        _state.value = newState
    }

    /**
     * 把一个 Throwable 包成 ConnectionError.Unknown;
     * 子类可以在自己的 resolve/connect/attach 里 catch + wrap。
     */
    protected fun wrapError(t: Throwable): ConnectionError =
        ConnectionError.Unknown(t)

    /**
     * 子类入口: 子项目 N 没实现时统一抛这个错。
     * 错误带 subProject 编号便于排查。
     */
    protected fun notImplemented(subProject: Int, op: String): Nothing {
        throw UnsupportedOperationException(
            "Debugger connection: $type sub-project $subProject '$op' not implemented yet"
        )
    }

    /**
     * 收尾 attach() 的双步: 验证 post-condition, 然后决定是进 Attached 还是 Closed。
     *
     * 为什么需要这个 helper: 直接在 `attempt.onSuccess { info -> ... }` 里调
     *   `transitionTo(Closed(...))` 然后 `return@onSuccess` 是错的, 因为
     *   `onSuccess` 的返回值是 Unit, 实际返回的是外层 `Result.success(info)`;
     *   调用方会拿到 success 但观察 state 看到 Closed, 行为不一致。
     *
     * 用法:
     *   val info = attempt.getOrNull() ?: return attempt
     *   val sock = clientSocket
     *   return finishAttach(
     *       info = info,
     *       ok = sock != null,
     *       failureMsg = "client socket missing",
     *       onAttached = { startReadLoop(sock!!) },
     *   )
     */
    protected fun finishAttach(
        info: AttachInfo,
        ok: Boolean,
        failureMsg: String,
        onAttached: () -> Unit,
    ): Result<AttachInfo> {
        if (!ok) {
            transitionTo(ConnectionState.Closed(ConnectionError.IoFailure(IOException(failureMsg))))
            return Result.failure(IllegalStateException(failureMsg))
        }
        transitionTo(ConnectionState.Attached(info.pid, info.jdwpSessionId))
        onAttached()
        return Result.success(info)
    }

    // 字节流默认抛 NotImplementedError; 子类真正实现时 override 即可。
    override suspend fun sendJdwp(bytes: ByteArray) {
        throw UnsupportedOperationException("sendJdwp not implemented for $type")
    }

    override fun receiveJdwp(): Flow<ByteArray> {
        throw UnsupportedOperationException("receiveJdwp not implemented for $type")
    }

    override fun release() {
        _state.value = ConnectionState.Idle
    }
}
