/*
 *  ZeroStudio IDE - Host ADRT (Android Debug Runtime)
 *
 *  IdeShizukuSocksUserService: Shizuku 13+ 在 host 进程内实例化的 user service,
 *  负责启动 HostSocksServer (SOCKS5 server), 让 IDE 端走 Socks5Client 连过来。
 *
 *  设计动机 (子项目 3.4 Socks 子路径):
 *    - Shizuku 13+ 把 newProcess/transferFileDescriptor 都设 private, 第三方
 *      不能像子项目 4 那样直接 push 进程或 transfer fd
 *    - 改方案: 在 host 进程内启 SOCKS5 server, IDE 端走通用 SOCKS5 客户端
 *      (子项目 5 的 Socks5Client) 连这个 server
 *    - 启动时机: IDE 调 Shizuku.bindUserService, Shizuku 把这个 class 在 host
 *      进程内 load 起来
 *    - 入口是 onBind (Shizuku 不读 binder 内容, 只要回调 fire 就算成功)
 *    - onBind 返回 null 也行, Shizuku 只关心 onServiceConnected 触发
 *
 *  跟 HostPluginService 的关系:
 *    - HostPluginService 是子项目 3.3 InHostPlugin 路径, 走 LocalServerSocket
 *      反向连 IDE
 *    - IdeShizukuSocksUserService 是子项目 3.4 Socks 路径, 主动起 Socks5 server
 *      等 IDE 来连
 *    - 两者都在 host 进程内跑, 都能被 Shizuku 用 bindUserService 启动
 *
 *  ComponentName (从 IDE 端 ShizukuConnection.attachViaSocks 传入):
 *    - package:  host.packageName (被调试应用的包名, 也就是本 class 实际
 *      所在进程的包名)
 *    - class:    com.itsaky.androidide.zerostudio.ide.debugger.host.IdeShizukuSocksUserService
 *    - 即 ComponentName(target.packageName, this class FQN)
 *
 *  consumer-rules.pro 必须保留本类 FQN (Shizuku 通过反射加载), 不能混淆。
 */

package com.itsaky.androidide.zerostudio.ide.debugger.host

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Shizuku 13+ user service for Socks sub-path.
 *
 * Lifecycle:
 *  1. IDE: Shizuku.bindUserService(ComponentName(host.pkg, this FQN), conn)
 *  2. Shizuku: 用 host 进程的 classloader 加载本类, 启动 onCreate + onBind
 *  3. onBind 触发 HostSocksServer.startOnTcp() 在后台线程 accept
 *  4. IDE: 监到 onServiceConnected 后, 用 Socks5Client 连 localhost:port
 *     (Socks5 server 监听端口通过 extra "socksPort" 回传, 但 Shizuku 的
 *      onServiceConnected 不读 binder, 所以这里用约定端口 + 配置文件 / Settings
 *      把 port 传过去; 实际做法见 ShizukuConnection 端 socksPort 配置)
 *  5. detach: Shizuku.unbindUserService 触发 onUnbind / onDestroy, 这里 stop Socks5 server
 */
class IdeShizukuSocksUserService : Service() {

    private val tag = "IdeShizukuSocksUserService"
    private var socksServer: HostSocksServer? = null
    /**
     * Socks5 server 真实监听端口 (startOnTcp 返回的 port)。
     * IDE 端用 [socksPort] 字段拿到这个值去连。
     */
    @Volatile var actualSocksPort: Int = -1
        private set

    /**
     * Shizuku 13+ user service 协议: onBind 返回的 IBinder 会被 ShizukuBinderClient
     * 读到, 返回 null 会抛 "binder is null" 让 IDE 端 throw 死循环。返回 [noopBinder]
     * 即可 (Shizuku 不读 binder 内容, 只调 pingBinder() 验活性, Binder 基类自带实现)。
     *
     * 跟 [HostPluginService] 同样的修复 (Phase 12c), 之前实现返回 null + 注释说
     * "返回 null 也行" 是错的, 改 noopBinder 保持一致。
     */
    private val noopBinder: IBinder = object : IBinder {
        // 空实现: Shizuku / IDE 只调 pingBinder(), Binder 基类自带 transact
        // PING_TRANSACTION 返回 true 的实现, 无需 override
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "onCreate in host process pid=${android.os.Process.myPid()}")
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(tag, "onBind: triggering HostSocksServer startup")
        // 启动 SOCKS5 server, listen 在 127.0.0.1:39939 (IDE 端固定默认端口)
        // Shizuku 反射加载本类不传自定义 intent extras, 跟 IDE 端
        // ShizukuConfig.socksPort = 39939 默认值保持一致, 端到端默认跑通。
        // 用户想改端口: IDE 端改 settings.shizuku.socksPort, host 端用
        // getSystemProperty / 约定 file 读到约定值 (尚未实装, 留 TODO)。
        val requestedPort = intent?.getIntExtra(EXTRA_SOCKS_PORT, DEFAULT_SOCKS_PORT) ?: DEFAULT_SOCKS_PORT
        val bindHost = intent?.getStringExtra(EXTRA_BIND_HOST) ?: DEFAULT_BIND_HOST
        try {
            val server = HostSocksServer()
            val port = server.startOnTcp(bindHost, requestedPort)
            socksServer = server
            actualSocksPort = port
            Log.i(tag, "Socks5 server started on $bindHost:$port")
        } catch (t: Throwable) {
            Log.e(tag, "startOnTcp failed: ${t.message}", t)
        }
        return noopBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(tag, "onUnbind: stopping Socks5 server")
        runCatching { socksServer?.stop() }
        socksServer = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(tag, "onDestroy: cleaning up")
        runCatching { socksServer?.stop() }
        socksServer = null
        super.onDestroy()
    }

    companion object {
        /** Intent extra: IDE 想 Socks5 server 监听的 TCP 端口 (0 = OS 分配随机) */
        const val EXTRA_SOCKS_PORT = "ide.shizuku.socks.port"
        /** Intent extra: bind host (默认 127.0.0.1) */
        const val EXTRA_BIND_HOST = "ide.shizuku.socks.bindHost"
        /** 默认监听地址: 127.0.0.1 (host 进程内, 不对外暴露) */
        const val DEFAULT_BIND_HOST = "127.0.0.1"
        /**
         * 默认监听端口: 39939, 跟 IDE 端 [com.itsaky.androidide.debugger.connection.DebugConnectionSettings.ShizukuConfig.socksPort]
         * 默认值保持一致, 端到端 Socks 路径默认跑通。
         */
        const val DEFAULT_SOCKS_PORT = 39939

        /**
         * host 端 FQN. Shizuku 反射加载本类时使用, 必须跟 manifest 里的一致
         * (虽然本服务不在 manifest 注册, Shizuku 直接 instantiate).
         */
        const val CLASS_NAME =
            "com.itsaky.androidide.zerostudio.ide.debugger.host.IdeShizukuSocksUserService"
    }
}
