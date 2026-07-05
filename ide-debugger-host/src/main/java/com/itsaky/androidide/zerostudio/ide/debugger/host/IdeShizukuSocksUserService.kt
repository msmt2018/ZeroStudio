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
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
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
        Log.i(tag, "onBind: returning ISocksControl binder (Phase 12y)")
        // Phase 12y: 返真 binder (替代 noopBinder), 让 IDE 端 attachViaSocks
        // 能走 binder.transact(CODE_SET_SOCKS_PORT, port) 传自定义 port 给 host
        // (Shizuku 13.1.5 的 UserServiceArgs 没 .args(Bundle) API, 之前 port 永远默认
        //  39939, 用户改 settings.shizuku.socksPort 完全无效)。
        // Phase 13c 同步: detach 走 stopSocks, onUnbind / onDestroy 兜底 stop,
        // 多个 IDE attach 同一 host app 不再永远占用 39939 (binder 协议动态调
        //  setSocksPort, 每次调会 stop 上一个 server 再起新 server)。
        return socksControlBinder
    }

    /**
     * Phase 12y: ISocksControl 协议 — 走 Binder.onTransact 自定义, 不走 .aidl 编译
     * (避免新增 aidl 依赖, 沙箱无 gradle 不能验)。协议:
     *
     *   - `setSocksPort(port: Int)`: 启 SOCKS5 server 在 127.0.0.1:port (port=0 时
     *     OS 选随机端口), reply 写 int actualPort
     *   - `getSocksPort() -> Int`: 返 actualPort, -1 表示 server 没启
     *   - `stopSocks()`: 停 server
     *
     * Interface token 走本 FQN (跟 Aidl 协议 DESIGNATOR 等效), enforceInterface
     * 防 IDE 端 binder 错连别的 user service。
     */
    private val socksControlBinder: IBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return try {
                when (code) {
                    CODE_SET_SOCKS_PORT -> {
                        data.enforceInterface(DESCRIPTOR)
                        val requested = data.readInt()
                        handleSetSocksPort(requested, reply)
                        true
                    }
                    CODE_GET_SOCKS_PORT -> {
                        data.enforceInterface(DESCRIPTOR)
                        reply?.writeInt(actualSocksPort)
                        true
                    }
                    CODE_STOP_SOCKS -> {
                        data.enforceInterface(DESCRIPTOR)
                        handleStopSocks(reply)
                        true
                    }
                    else -> super.onTransact(code, data, reply, flags)
                }
            } catch (t: Throwable) {
                Log.w(tag, "onTransact code=$code failed: ${t.message}", t)
                reply?.writeException(t)
                true
            }
        }
    }

    /**
     * setSocksPort transact handler: 停老 server (如有), 启新 server 在 requested port。
     * reply 写 int actualPort (requested == 0 时 OS 选随机端口, 写实际端口)。
     */
    private fun handleSetSocksPort(requested: Int, reply: Parcel?) {
        if (requested != 0 && (requested < 1 || requested > 65535)) {
            val ex = IllegalArgumentException("port out of range: $requested (1..65535 or 0 for OS pick)")
            reply?.writeException(ex)
            return
        }
        val bindHost = DEFAULT_BIND_HOST
        runCatching { socksServer?.stop() }
        socksServer = null
        actualSocksPort = -1
        try {
            val server = HostSocksServer()
            val actual = server.startOnTcp(bindHost, requested)
            socksServer = server
            actualSocksPort = actual
            Log.i(tag, "Socks5 server started on $bindHost:$actual (requested=$requested)")
            reply?.writeInt(actual)
        } catch (t: Throwable) {
            Log.e(tag, "setSocksPort($requested) failed: ${t.message}", t)
            reply?.writeException(t)
        }
    }

    /**
     * stopSocks transact handler: 停 server, 返 void (reply 写 noException)。
     */
    private fun handleStopSocks(reply: Parcel?) {
        runCatching { socksServer?.stop() }
        socksServer = null
        actualSocksPort = -1
        Log.i(tag, "Socks5 server stopped via stopSocks transact")
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

        // Phase 12y: ISocksControl binder transact codes
        const val CODE_SET_SOCKS_PORT = 1
        const val CODE_GET_SOCKS_PORT = 2
        const val CODE_STOP_SOCKS = 3
        const val DESCRIPTOR =
            "com.itsaky.androidide.zerostudio.ide.debugger.host.IdeShizukuSocksUserService.ISocksControl"
    }
}
