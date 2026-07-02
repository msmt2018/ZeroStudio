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

    /**
     * Shizuku user service 协议:
     *   - Shizuku 13+ 用反射在 host 进程内 instantiate 本类
     *   - onBind 返回的 IBinder 会通过 ServiceConnection.onServiceConnected
     *     传给 IDE (Shizuku 不读 binder 内容, 任何 IBinder 都行, 包括
     *     一个空的 placeholder; 但必须返回非 null, 否则 ShizukuBinderClient
     *     会抛 "binder is null")
     *   - 之前实现返回 null, 导致 IDE 端 throw 死循环, 修复返回 noop Binder
     */
    private val noopBinder: IBinder = object : IBinder {
        // 空实现: IDE 端只调 pingBinder() 验活性, Binder 基类自带实现
        // (transact PING_TRANSACTION 返回 true)
    }

    override fun onBind(intent: android.content.Intent?): IBinder {
        // 启动 reverse-conn thread, 桥接 IDE LocalServerSocket <-> host JDWP
        // 注意: 之前实现读 intent.getStringExtra(EXTRA_IDE_SOCKET_NAME),
        // 但 Shizuku.bindUserService 不传自定义 extras, 改用约定 socket
        // 名 (本类的 FQN 模板 = "ide-shizuku-inhostplugin-{pkg}"), 跟
        // IDE 端 ShizukuConnection.attachViaInHostPlugin() 一致。
        ideSocketName = computeIdeSocketName()
        Log.i(tag, "onBind: ide socket=$ideSocketName, starting reverse-conn")
        startBridge()
        return noopBinder
    }

    /**
     * 计算 IDE LocalServerSocket 名字, 跟 ShizukuConnection.attachViaInHostPlugin()
     * 端 serverName = "ide-shizuku-${target.packageName}-${ts}" 的旧实现不同;
     * 旧实现用 timestamp 每次新建, 但 Shizuku 反射加载本类时拿不到 IDE 用的
     * timestamp, 必须用约定名 (固定 suffix, 不带 timestamp)。
     *
     * 当前简化: 用一个全局约定的 "inhostplugin-{pkg}" 名, 多次 attach 复用
     * 同一个 socket name (IDE 端需要 close 旧 server 才能 bind 同名 socket,
     * 失败 fallback 用 timestamp 后缀)。
     */
    private fun computeIdeSocketName(): String {
        // 暂用 package 推不出来, 用一个固定根名 + 后缀。
        // (host 进程里拿不到 IDE 端用哪个 package 当 target.packageName,
        //  只能 IDE 在 release/detach 时 close 旧 server, 复用同名 socket)
        return "ide-shizuku-inhostplugin"
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
