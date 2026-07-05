/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  DebugConnectionKotlinBridge: Java 调 suspend 函数的桥。
 *  DebuggerController (Java) 不想直接写 BuildersKt.runBlocking(...) + 反射
 *  SuspendFunction0 的样板,这里直接给一个 blocking 版本。
 */

package com.itsaky.androidide.debugger.connection

import kotlinx.coroutines.runBlocking

object DebugConnectionKotlinBridge {

    /**
     * 阻塞地跑 ConnectionBackedDebugger.run(),给 Java 调用方用。
     * @return 成功: AttachInfo;失败: ConnectionError 包在 RuntimeException 里
     */
    @JvmStatic
    fun runConnectVia(
        conn: IDebugConnection,
    ): Result<AttachInfo> = runBlocking { ConnectionBackedDebugger(conn).run() }

    /**
     * 阻塞地跑 ConnectionBackedDebugger.shutdown(),给 Java 调用方用。
     */
    @JvmStatic
    fun runShutdown(
        conn: IDebugConnection,
    ) = runBlocking { ConnectionBackedDebugger(conn).shutdown() }
}
