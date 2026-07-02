/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ConnectionBackedDebugger: 把 IDebugConnection(子项目 1 新抽象)
 *  适配到现有 com.zerostudio.debugger.api.Debugger(ide-debugger 老 API)。
 *
 *  用法:
 *    val conn = DebugConnectionRegistry.createForActive(target, settings)
 *    val adapter = ConnectionBackedDebugger(conn)
 *    adapter.run()  // 走 resolve -> connect -> attach 串成 flow,失败抛
 *    val debugger = adapter.debugger  // 之后用这个 Debugger 做 JDWP 操作
 *
 *  设计要点:
 *  - 不重复实现 JDWP 协议,只做"取 Socket -> 给 JdwpClient"的桥
 *  - 字节流由 JdwpClient 内部管,我们不碰
 *  - 5 种实现都通过 attachedSocket() 暴露 Socket(子项目 2~5 补齐 override)
 *  - 当前阶段 stub impl 的 attachedSocket() 抛 UnsupportedOperationException,
 *    所以调 run() 一定会失败,符合"先架构后实现"原则
 */

package com.itsaky.androidide.debugger.connection

import com.itsaky.androidide.utils.ILogger
import com.zerostudio.debugger.api.Debugger
import com.zerostudio.debugger.jdwp.JdwpClient

class ConnectionBackedDebugger(
    private val connection: IDebugConnection,
) {

    private val log = ILogger.ROOT

    /** 现有 JDWP Debugger 实例;run() 成功后才非 null */
    @Volatile
    var debugger: Debugger? = null
        private set

    /**
     * 走完 resolve -> connect -> attach,然后用 attachedSocket() 构造
     * 一个新的 JdwpClient 并喂给 Debugger。
     *
     * @return AttachInfo on success
     * @throws IllegalStateException 任何阶段失败 (state 会切到 Closed(error))
     */
    suspend fun run(): Result<AttachInfo> {
        // 1) resolve
        val resolveResult = connection.resolve()
        if (resolveResult.isFailure) {
            return Result.failure(resolveResult.exceptionOrNull()!!)
        }

        // 2) connect
        val connectResult = connection.connect()
        if (connectResult.isFailure) {
            return Result.failure(connectResult.exceptionOrNull()!!)
        }

        // 3) attach
        val attachResult = connection.attach()
        if (attachResult.isFailure) {
            return Result.failure(attachResult.exceptionOrNull()!!)
        }
        val attachInfo = attachResult.getOrThrow()

        // 4) 取 Socket,桥接到 JdwpClient
        try {
            val socket = connection.attachedSocket()
            val client = JdwpClient()
            client.connect(socket, "", 0)
            debugger = Debugger.forClient(client)
            log.info("ConnectionBackedDebugger attached: ${connection.type} pid=${attachInfo.pid}")
        } catch (UnsupportedOperationException uoe) {
            log.warn("ConnectionBackedDebugger: $uoe (this is expected for stub impls)")
            return Result.failure(uoe)
        } catch (Throwable t) {
            log.error("ConnectionBackedDebugger: bridging failed", t)
            return Result.failure(t)
        }

        return Result.success(attachInfo)
    }

    /**
     * 释放: detach + release 连接,然后置空 debugger。
     */
    suspend fun shutdown() {
        try {
            connection.detach()
        } catch (Throwable t) {
            log.warn("detach failed: ${t.message}")
        } finally {
            try {
                debugger?.disconnect()
            } catch (Throwable ignored) {}
            debugger = null
            connection.release()
        }
    }
}
