/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuProbe: 探测 Shizuku 服务端是否在运行 + 是否授权当前 IDE app。
 *
 *  Shizuku 通过 adb root 启动一个特权进程 (Shizuku server) 暴露 binder 接口,
 *  任何用户 app 都可以调 `rikka.shizuku.Shizuku` 拿到这个 binder。
 *  但:
 *    - Shizuku 必须先在用户设备上启动 (从 Shizuku Manager app 启动, 或 adb 命令)
 *    - 用户必须先在 Shizuku Manager app 给 IDE app 授权
 *    - IDE 重装后会丢失授权, 需要重新授权
 *
 *  这 3 件事都靠 ShizukuProbe 探测 + 报错给 UI。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import com.itsaky.androidide.utils.ILogger
import rikka.shizuku.Shizuku

/**
 * 探测结果: Shizuku 状态 + 授权状态 + 服务端信息。
 */
data class ShizukuStatus(
    /** Shizuku server 是否在 binder 上存活 (pingBinder 通过) */
    val isRunning: Boolean,
    /** 当前 IDE app 是否被 Shizuku 授权 (callShizuku 不抛 SecurityException) */
    val isGranted: Boolean,
    /** Shizuku server uid (仅当 isRunning=true 有意义, 否则 -1) */
    val serverUid: Int,
    /** Shizuku server API version (12/13, 仅当 isRunning=true 有意义, 否则 -1) */
    val serverApiVersion: Int,
    /** 如果 isRunning=false 时的原因 (给 UI 显示) */
    val notRunningReason: String? = null,
) {
    /** 状态是否健康 (能开始 attach 流程) */
    val isReady: Boolean get() = isRunning && isGranted
}

/**
 * 探测器抽象,生产用 [DefaultShizukuProbe],测试用 [FakeShizukuProbe]。
 */
interface ShizukuProbe {
    /** 同步探测一次当前状态。 */
    fun probe(): ShizukuStatus

    /**
     * 拉起 Shizuku 权限请求对话框 (如果 Shizuku 已运行但未授权)。
     * 阻塞直到用户接受/拒绝, 或 [timeoutMs] 之后超时。
     *
     * @return true 表示已授权
     */
    fun requestPermissionIfNeeded(
        requestCode: Int = REQUEST_PERMISSION,
        timeoutMs: Long = 5_000L,
    ): Boolean

    companion object {
        const val REQUEST_PERMISSION = 0x5B1A
    }
}

/** 默认生产实现: 走 rikka.shizuku.Shizuku 静态 API。 */
class DefaultShizukuProbe : ShizukuProbe {

    private val log = ILogger.ROOT

    override fun probe(): ShizukuStatus {
        val running = try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            log.debug("Shizuku.pingBinder failed: {}", t.message)
            false
        }
        if (!running) {
            return ShizukuStatus(
                isRunning = false,
                isGranted = false,
                serverUid = -1,
                serverApiVersion = -1,
                notRunningReason = "Shizuku 服务端未运行, 请先在 Shizuku Manager 启动",
            )
        }
        val granted = try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (se: SecurityException) {
            // Shizuku pre-v11: 旧版 checkSelfPermission 抛 SecurityException
            log.debug("Shizuku.checkSelfPermission: SecurityException (pre-v11): {}", se.message)
            false
        } catch (t: Throwable) {
            log.warn("Shizuku.checkSelfPermission failed", t)
            false
        }
        val uid = try {
            Shizuku.getUid()
        } catch (t: Throwable) {
            -1
        }
        val version = try {
            Shizuku.getVersion()
        } catch (t: Throwable) {
            -1
        }
        return ShizukuStatus(
            isRunning = true,
            isGranted = granted,
            serverUid = uid,
            serverApiVersion = version,
            notRunningReason = if (!granted) "Shizuku 未给当前 IDE app 授权" else null,
        )
    }

    override fun requestPermissionIfNeeded(requestCode: Int, timeoutMs: Long): Boolean {
        val status = probe()
        if (!status.isRunning) return false
        if (status.isGranted) return true
        return try {
            val latch = java.util.concurrent.CountDownLatch(1)
            val granted = booleanArrayOf(false)
            val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                granted[0] = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                latch.countDown()
            }
            rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener)
            try {
                rikka.shizuku.Shizuku.requestPermission(requestCode)
                latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                granted[0]
            } finally {
                rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener)
            }
        } catch (t: Throwable) {
            log.warn("requestPermissionIfNeeded failed", t)
            false
        }
    }
}

/** 测试用 fake probe,可指定固定返回结果。 */
class FakeShizukuProbe(
    var status: ShizukuStatus = ShizukuStatus(
        isRunning = true,
        isGranted = true,
        serverUid = 1000,
        serverApiVersion = 13,
    ),
    private val grantResult: Boolean = true,
) : ShizukuProbe {
    var probeCount: Int = 0
        private set
    var requestCount: Int = 0
        private set

    override fun probe(): ShizukuStatus {
        probeCount++
        return status
    }

    override fun requestPermissionIfNeeded(requestCode: Int, timeoutMs: Long): Boolean {
        requestCount++
        if (status.isRunning && grantResult) {
            status = status.copy(isGranted = true)
        }
        return grantResult
    }
}
