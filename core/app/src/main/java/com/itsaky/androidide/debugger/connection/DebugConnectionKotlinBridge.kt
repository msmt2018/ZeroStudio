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
     *
     * Java 调用方无法方便地使用 Kotlin [Result], 因此这个 Java 友好版本
     * 直接返回 [AttachInfo], 失败时抛 [RuntimeException] (cause = 原始异常)。
     *
     * Kotlin 调用方应直接用 [ConnectionBackedDebugger.run] 获取 [Result]。
     *
     * @return 成功的 AttachInfo
     * @throws RuntimeException 连接失败时抛出, cause 为原始异常
     */
    @JvmStatic
    @Throws(RuntimeException::class)
    fun runConnectVia(
        conn: IDebugConnection,
    ): AttachInfo {
        val result: Result<AttachInfo> = runBlocking { ConnectionBackedDebugger(conn).run() }
        return result.getOrThrow()
    }

    /**
     * 阻塞地跑 ConnectionBackedDebugger.shutdown(),给 Java 调用方用。
     */
    @JvmStatic
    fun runShutdown(
        conn: IDebugConnection,
    ) = runBlocking { ConnectionBackedDebugger(conn).shutdown() }
}
