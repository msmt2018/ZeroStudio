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
     */
    suspend fun bindUserService(
        componentName: ComponentName,
        processName: String,
    ): IBinder

    companion object {
        @JvmStatic
        fun create(@Suppress("UNUSED_PARAMETER") any: Any? = null): ShizukuBinderClient =
            DefaultShizukuBinderClient()
    }
}

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
    ): IBinder = withContext(Dispatchers.IO) {
        // 走 Shizuku.bindUserService, 阻塞等 onServiceConnected
        val latch = java.util.concurrent.CountDownLatch(1)
        val binderRef = arrayOfNulls<IBinder>(1)
        val conn = ServiceConnection { _, service ->
            binderRef[0] = service
            latch.countDown()
        }
        try {
            val args = rikka.shizuku.api.UserServiceArgs(componentName)
                .processName(processName)
                .daemon(false)
                .debuggable(false)
            Shizuku.bindUserService(args, conn)
            if (!latch.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw IOException("bindUserService timeout (10s)")
            }
            binderRef[0] ?: throw IOException("bindUserService: binder is null")
        } catch (re: RemoteException) {
            throw IOException("bindUserService failed: ${re.message}", re)
        } catch (se: SecurityException) {
            throw IOException("bindUserService: security: ${se.message}", se)
        } finally {
            // 注意: 这里不 unbindService, 让 host 端 service 持续运行
            // (detach 时再 unbind)
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

    var newProcessCallCount: Int = 0
        private set
    var transferFdCallCount: Int = 0
        private set
    var bindUserServiceCallCount: Int = 0
        private set
    var lastCmd: Array<String>? = null
        private set
    var lastComponentName: ComponentName? = null
        private set
    var lastProcessName: String? = null
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
    ): IBinder {
        bindUserServiceCallCount++
        lastComponentName = componentName
        lastProcessName = processName
        return bindUserServiceResult ?: throw IOException("FakeShizukuBinderClient: bindUserServiceResult is null")
    }
}
