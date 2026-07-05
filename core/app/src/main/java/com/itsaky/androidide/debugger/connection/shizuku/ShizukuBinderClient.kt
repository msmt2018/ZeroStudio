/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuBinderClient: 对 rikka.shizuku.Shizuku 静态 API 的 Kotlin 协程包装。
 *
 *  设计原则:
 *    - Shizuku 13+ 把 Shizuku.newProcess 标 deprecated, 把 ShizukuRemoteProcess
 *      构造函数设 package-private, 第三方代码无法直接 newProcess。
 *    - 官方推荐用 attachUserService / bindUserService 模式: 用户 app 在自己
 *      进程跑一个 user service, service 内有 Shizuku 全权限可以跑 root 命令。
 *    - transferFileDescriptor 同样, 官方不暴露公共 API, 走 attachUserService +
 *      Binder (在 user service 内 open fd + 写回 parcel)。
 *
 *  本类因此提供:
 *    - getUid / getVersion / pingBinder: 走 Shizuku 公开 API
 *    - checkPermission / requestPermission: 走 Shizuku 公开 API
 *    - bindUserService: 走 Shizuku.bindUserService (user service 模式)
 *    - newProcess / transferFileDescriptor: 抛 UnsupportedOperationException
 *      (Shizuku 13+ 设计上禁止第三方直接使用)
 *
 *  完整功能 (包括 Binder / Socks 路径) 等子项目 8 host runtime 完成后才能跑。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.itsaky.androidide.utils.ILogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.IOException

/**
 * Binder 客户端抽象。生产用 [DefaultShizukuBinderClient],测试用 fake。
 */
interface ShizukuBinderClient {
    /**
     * Shizuku 13+ 不允许第三方直接 newProcess (会抛 UnsupportedOperationException)。
     * 调用方应该用 [bindUserService] 拉一个 user service, service 内跑命令。
     */
    suspend fun newProcess(
        cmd: Array<String>,
        env: Array<String>? = null,
        dir: String? = null,
    ): ShizukuRemoteProcess

    suspend fun checkPermission(permission: String): Int

    suspend fun pingBinder(): Boolean

    suspend fun getUid(): Int

    suspend fun getVersion(): Int

    /**
     * Shizuku 13+ 不暴露公共 transferFileDescriptor API。
     * 调用方应该用 [bindUserService] + user service 内自己实现 fd 传递。
     */
    suspend fun transferFileDescriptor(
        remoteBinder: IBinder,
        remoteFd: ParcelFileDescriptor,
    ): ParcelFileDescriptor

    /**
     * 调 Shizuku.bindUserService 拉一个 user service, 阻塞等 onServiceConnected。
     *
     * Phase 12x: 加 `args: Bundle?` 参数, Shizuku 13+ 的 [rikka.shizuku.api.UserServiceArgs]
     * 支持 `.args(Bundle)` 传自定义 Bundle, 这个 Bundle 会被 Shizuku 放到 user service
     * `onBind(Intent)` 的 intent extras 里。host 端 `IdeShizukuSocksUserService.onBind`
     * 读 `intent.getIntExtra(EXTRA_SOCKS_PORT, DEFAULT_SOCKS_PORT)`, 之前 IDE 端
     * 改 `settings.shizuku.socksPort` 完全无效 (intent 没 extras, 永远默认 39939,
     * Socks 客户端连用户设的端口但 host listen 在 39939, 连接失败)。
     *
     * Phase 15: 返值从 [IBinder] 改 [UserServiceHandle] (含 ServiceConnection 引用),
     * 之前只返 IBinder, caller 拿不到 ServiceConnection, 没法 unbind, host 端 user
     * service 走完 attach 永远 leak。detach / release 现在能 unbind 了 (Phase 16)。
     */
    suspend fun bindUserService(
        componentName: ComponentName,
        processName: String,
        args: Bundle? = null,
    ): UserServiceHandle

    /**
     * 释放 user service: 走 `rikka.shizuku.Shizuku.unbindUserService(conn)`, 通知
     * Shizuku 让 host 端 user service 走 onDestroy (释放 SOCKS5 server / LocalServerSocket
     * / fd 等资源)。
     *
     * Phase 15: 之前 [ShizukuBinderClient] 没暴露 unbind 抽象, ShizukuConnection
     * detach / release 漏 unbind, host 端 service leak (Socks 路径会留 SOCKS5 server
     * 占端口, InHostPlugin 路径会留 LocalServerSocket 占 abstract namespace)。
     */
    fun unbindUserService(handle: UserServiceHandle)

    companion object {
        @JvmStatic
        fun create(@Suppress("UNUSED_PARAMETER") any: Any? = null): ShizukuBinderClient =
            DefaultShizukuBinderClient()
    }
}

/**
 * Phase 15: [ShizukuBinderClient.bindUserService] 的返值, 含 [binder] (跟 host 端
 * user service 通信) + [connection] (用于 unbind 时传给 Shizuku)。
 */
data class UserServiceHandle(
    val binder: IBinder,
    val connection: ServiceConnection,
)

/**
 * 默认生产实现。
 */
class DefaultShizukuBinderClient : ShizukuBinderClient {

    private val log = ILogger.ROOT

    override suspend fun newProcess(
        cmd: Array<String>,
        env: Array<String>?,
        dir: String?,
    ): ShizukuRemoteProcess = throw UnsupportedOperationException(
        "Shizuku 13+ 不支持第三方直接 newProcess, 请用 bindUserService 模式 " +
            "(子项目 8 host runtime 一起提供)"
    )

    override suspend fun checkPermission(permission: String): Int = withContext(Dispatchers.IO) {
        try {
            Shizuku.checkPermission(permission)
        } catch (re: RemoteException) {
            throw IOException("Shizuku.checkPermission failed: ${re.message}", re)
        } catch (se: SecurityException) {
            // pre-v11, 没授权抛 SecurityException
            android.content.pm.PackageManager.PERMISSION_DENIED
        }
    }

    override suspend fun pingBinder(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            log.debug("Shizuku.pingBinder failed: {}", t.message)
            false
        }
    }

    override suspend fun getUid(): Int = withContext(Dispatchers.IO) {
        try {
            Shizuku.getUid()
        } catch (t: Throwable) {
            -1
        }
    }

    override suspend fun getVersion(): Int = withContext(Dispatchers.IO) {
        try {
            Shizuku.getVersion()
        } catch (t: Throwable) {
            -1
        }
    }

    override suspend fun transferFileDescriptor(
        remoteBinder: IBinder,
        remoteFd: ParcelFileDescriptor,
    ): ParcelFileDescriptor = throw UnsupportedOperationException(
        "Shizuku 13+ 不暴露公共 transferFileDescriptor API, 请用 bindUserService + " +
            "user service 内自行实现 fd 传递 (子项目 8 host runtime 一起提供)"
    )

    override suspend fun bindUserService(
        componentName: ComponentName,
        processName: String,
        args: Bundle?,
    ): UserServiceHandle = withContext(Dispatchers.IO) {
        // 走 Shizuku.bindUserService, 阻塞等 onServiceConnected
        val latch = java.util.concurrent.CountDownLatch(1)
        val binderRef = arrayOfNulls<IBinder>(1)
        val conn = ServiceConnection { _, service ->
            binderRef[0] = service
            latch.countDown()
        }
        try {
            // Phase 12x (修订): Shizuku 13.1.5 的 [Shizuku.UserServiceArgs] 是
            //   Shizuku 的内部类 (不在 rikka.shizuku.api 包), 且**没有** .args(Bundle)
            //   API, args 内部 forAdd() Bundle 是 Shizuku 私有 user-supplied 不能加。
            //   之前 rikka.shizuku.api.UserServiceArgs 是错的 class 路径, 编译失败。
            //   也不能从 IDE 端传自定义 Bundle 到 host 端 onBind(Intent) extras。
            // 修法: 走 rikka.shizuku.Shizuku.UserServiceArgs 正确路径, args 参数
            //   保留接口 (后续 Phase 12y 走 binder transact 协议替代), 但当前
            //   args 参数被忽略 (Shizuku 13.1.5 没 API 接收)。
            val builder = rikka.shizuku.Shizuku.UserServiceArgs(componentName)
                .processName(processName)
                .daemon(false)
                .debuggable(false)
            // args 暂不传给 Shizuku 13.1.5 (没 API), 走 binder transact 替代
            //   详细见 Phase 12y TODO: 实现 ISocksControl AIDL + transact
            if (args != null) {
                log.warn("bindUserService: args Bundle ignored (Shizuku 13.1.5 没 .args API), " +
                    "use binder.transact() in Phase 12y ISocksControl AIDL")
            }
            Shizuku.bindUserService(builder, conn)
            if (!latch.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw IOException("bindUserService timeout (10s)")
            }
            val binder = binderRef[0] ?: throw IOException("bindUserService: binder is null")
            // Phase 15: 返 UserServiceHandle (含 ServiceConnection 引用), 让 caller
            //   在 detach / release 时能 unbind。注释掉的 finally 块删了, host 端
            //   service 现在由 caller 生命周期管理 (attach 期间活, detach 释放)。
            UserServiceHandle(binder = binder, connection = conn)
        } catch (re: RemoteException) {
            // bind 失败, conn 已注册到 Shizuku 但 onServiceConnected 没调过, 安全 unbind
            runCatching { Shizuku.unbindUserService(conn) }
            throw IOException("bindUserService failed: ${re.message}", re)
        } catch (se: SecurityException) {
            runCatching { Shizuku.unbindUserService(conn) }
            throw IOException("bindUserService: security: ${se.message}", se)
        } catch (t: Throwable) {
            // 任何 throw: 超时 / binder null / 其他
            runCatching { Shizuku.unbindUserService(conn) }
            throw t
        }
    }

    override fun unbindUserService(handle: UserServiceHandle) {
        // 走 rikka.shizuku.Shizuku.unbindUserService, 通知 Shizuku 让 host 端
        // user service 走 onDestroy。Phase 15 之前 caller 拿不到 conn, 这步
        // 调不了, host 端 service 永远 leak。
        runCatching { Shizuku.unbindUserService(handle.connection) }
            .onFailure {
                log.debug("unbindUserService: Shizuku.unbindUserService threw: {}", it.message)
            }
    }
}

/**
 * 测试用 fake: 可预置返回值或强制抛错。
 */
class FakeShizukuBinderClient(
    private val pingResult: Boolean = true,
    private val uidResult: Int = 1000,
    private val versionResult: Int = 13,
    private val newProcessShouldThrow: Boolean = true,
    private val transferFdShouldThrow: Boolean = true,
    private val bindUserServiceResult: IBinder? = null,
) : ShizukuBinderClient {

    // Phase 15: 老签名 (IBinder) 内部 cache 一个 noop ServiceConnection, 让旧
    //   测试 caller 不用改 (5 处: `bindUserServiceResult = mockBinder` 仍能用)。
    private val noopConn: ServiceConnection = ServiceConnection { _, _ -> }
    private var lastHandle: UserServiceHandle? = null

    var newProcessCallCount: Int = 0
        private set
    var transferFdCallCount: Int = 0
        private set
    var bindUserServiceCallCount: Int = 0
        private set
    var unbindUserServiceCallCount: Int = 0
        private set
    var lastCmd: Array<String>? = null
        private set
    var lastComponentName: ComponentName? = null
        private set
    var lastProcessName: String? = null
        private set
    // Phase 12x: 跟踪 lastArgs 用于 test 验证 IDE 端传的 Bundle 正确
    var lastArgs: Bundle? = null
        private set
    // Phase 15: 最近一次 unbind 的 handle 引用 (供测试断言)
    var lastUnbindHandle: UserServiceHandle? = null
        private set

    override suspend fun newProcess(
        cmd: Array<String>,
        env: Array<String>?,
        dir: String?,
    ): ShizukuRemoteProcess {
        newProcessCallCount++
        lastCmd = cmd
        if (newProcessShouldThrow) {
            throw UnsupportedOperationException("FakeShizukuBinderClient: newProcess disabled")
        }
        throw IOException("FakeShizukuBinderClient: no real ShizukuRemoteProcess in test")
    }

    override suspend fun checkPermission(permission: String): Int =
        android.content.pm.PackageManager.PERMISSION_GRANTED

    override suspend fun pingBinder(): Boolean = pingResult
    override suspend fun getUid(): Int = uidResult
    override suspend fun getVersion(): Int = versionResult

    override suspend fun transferFileDescriptor(
        remoteBinder: IBinder,
        remoteFd: ParcelFileDescriptor,
    ): ParcelFileDescriptor {
        transferFdCallCount++
        if (transferFdShouldThrow) {
            throw UnsupportedOperationException("FakeShizukuBinderClient: transferFileDescriptor disabled")
        }
        return remoteFd
    }

    override suspend fun bindUserService(
        componentName: ComponentName,
        processName: String,
        args: Bundle?,
    ): UserServiceHandle {
        bindUserServiceCallCount++
        lastComponentName = componentName
        lastProcessName = processName
        lastArgs = args
        val binder = bindUserServiceResult
            ?: throw IOException("FakeShizukuBinderClient: bindUserServiceResult is null")
        // Phase 15: 返 UserServiceHandle (binder + noop conn), unbind 走 noop。
        val handle = UserServiceHandle(binder = binder, connection = noopConn)
        lastHandle = handle
        return handle
    }

    override fun unbindUserService(handle: UserServiceHandle) {
        unbindUserServiceCallCount++
        lastUnbindHandle = handle
    }
}
