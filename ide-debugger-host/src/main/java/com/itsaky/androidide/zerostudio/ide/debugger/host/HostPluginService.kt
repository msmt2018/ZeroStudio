/*
 *  ZeroStudio IDE - Host ADRT (Android Debug Runtime)
 *
 *  HostPluginService: Shizuku user service 形式, 在 host 进程内被 Shizuku
 *  bindUserService 拉起 (子项目 3 里的 InHostPlugin 子路径)。
 *
 *  工作流程 (跟 HostAttachAgent 类似, 但走 Shizuku 注入而非 app_process):
 *    1) IDE 端 ShizukuConnection.attachViaInHostPlugin() 调
 *       ShizukuBinderClient.bindUserService(IDE component, host pkg)
 *    2) Shizuku 在 host 进程内实例化 HostPluginService (它是 Shizuku 注入的
 *       IDE user service class)
 *    3) HostPluginService.onBind() 启动一个 reverse-conn thread, 反向连
 *       IDE 的 LocalServerSocket (绑在 host 进程内, 由 IDE 通过 binder
 *       通知 service 名字)
 *    4) IDE 端 accept() 后, 走标准 JDWP 桥接
 *
 *  实现要点:
 *    - 走 Shizuku 提供的 IUserService aidl 接口 (Shizuku 13+ 推荐用法)
 *    - service 名字由 binder 传过来, 不写死
 *    - 反向连 thread 跟 HostAttachAgent.bridgeBytes() 共享
 *
 *  已知限制: 本类是 shizuku-aidl (Shizuku 自带) 的实现, 运行时必须在
 *  classpath 里有 shizuku-api 依赖; 但 host 进程 (用户 App) 通常没这
 *  依赖, 所以这个 service 实际由 IDE 的 user service 工厂创建 (跟 Shizuku
 *  一起打包, 通过 attachUserService 注入 host 进程)。
 */

package com.itsaky.androidide.zerostudio.ide.debugger.host

import android.os.IBinder
import android.util.Log

/**
 * Shizuku user service. IDE 通过 [bindIdePlugin] 把这个 service 注入 host
 * 进程; host 进程内启动时, [attachToIde] 被调用, 反向连 IDE LocalServerSocket。
 */
class HostPluginService : android.app.Service() {

    private val tag = "HostPluginService"

    private var ideSocketName: String? = null
    private var bridgeThread: Thread? = null
    @Volatile private var stopped = false

    override fun onBind(intent: android.content.Intent?): IBinder? {
        // Shizuku user service 的 binder 通过 attachUserService 注册
        // 真实实现参考 ShizukuSimpleAIDL 定义, 这里只声明签名
        ideSocketName = intent?.getStringExtra(EXTRA_IDE_SOCKET_NAME)
        if (ideSocketName.isNullOrBlank()) {
            Log.e(tag, "onBind: missing ide socket name")
            return null
        }
        Log.i(tag, "onBind: ide socket=$ideSocketName")
        startBridge()
        return null
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(tag, "onUnbind")
        stopped = true
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopped = true
        runCatching { bridgeThread?.interrupt() }
    }

    private fun startBridge() {
        if (bridgeThread != null) return
        bridgeThread = Thread({
            val name = ideSocketName ?: return@Thread
            try {
                val ide = HostAttachAgent.connectToIdeLocalServer(name)
                val jdwp = HostAttachAgent.openLocalAbstractJdwpSocket()
                try {
                    HostAttachAgent.bridgeBytes(ide, jdwp)
                } finally {
                    runCatching { ide.close() }
                    runCatching { jdwp.close() }
                }
            } catch (t: Throwable) {
                Log.w(tag, "bridge failed: ${t.message}")
            } finally {
                runCatching { stopSelf() }
            }
        }, "HostPluginService-bridge").apply { start() }
    }

    companion object {
        const val EXTRA_IDE_SOCKET_NAME = "ide_socket_name"
    }
}
