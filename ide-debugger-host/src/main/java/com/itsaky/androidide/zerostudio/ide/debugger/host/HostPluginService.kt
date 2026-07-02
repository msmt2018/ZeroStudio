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
     * 计算 IDE LocalServerSocket 名字, 跟 [com.itsaky.androidide.debugger.connection.impl.SizukuConnection]
     * 端一致: 拼上 host 进程包名, 避免多 host app 同一时间 attach 时冲突
     * (固定名 "ide-shizuku-inhostplugin" 在多 host app 并发调试场景会 race,
     * IDE 端 LocalServerSocket(name) bind 同名会失败)。
     *
     * 拼包名后, host app A 用 "ide-shizuku-inhostplugin-com.foo.A",
     * host app B 用 "ide-shizuku-inhostplugin-com.bar.B", 完全独立。
     *
     * Fallback: applicationContext 拿不到或 pkg 为空时用固定根名, 跟旧实现兼容
     * (理论不会发生, host app 一定有 packageName)。
     */
    private fun computeIdeSocketName(): String {
        val pkg = applicationContext?.packageName
        return if (pkg.isNullOrBlank()) {
            DEFAULT_IDE_SOCKET_NAME
        } else {
            "$DEFAULT_IDE_SOCKET_NAME-$pkg"
        }
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

        /**
         * IDE LocalServerSocket 名字的根名; host 端 [computeIdeSocketName] /
         * IDE 端 [com.itsaky.androidide.debugger.connection.impl.ShizukuConnection]
         * 都拼上 host 进程包名, 多 host app 并发调试不冲突。
         */
        const val DEFAULT_IDE_SOCKET_NAME = "ide-shizuku-inhostplugin"
    }
}
