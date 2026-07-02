/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  AidlProcessProbe: 探测目标 app 是否已经在前台运行。
 *
 *  用法:
 *    val probe = AidlProcessProbe.create(appContext)
 *    val info = probe.findAppProcessInfo("com.example.app")
 *    if (info == null) -> HostAppNotRunning
 *    else -> 用 info.pid / info.uid 做后续 attach 决策
 *
 *  不依赖 ContentProvider,直接读 ActivityManager.getRunningAppProcesses(),
 *  对 target 5+ (Android 5+) 来说,该 API 对"自己的 debug app 自己的进程"
 *  是可见的 (在 IDE 自己的 UID 下读自己的 app 的子进程在 debug 变体里通常可见)。
 *
 *  注意: Android 8+ (API 26+) 收紧了这个 API 的访问范围,所以本类返回
 *  null 时,调用方不能 100% 断定"app 没运行",只能当作"可能没运行或不可见"。
 *  这正是 ConnectionRetryPolicy 重试的依据 — 失败一次就重试,给 host
 *  启动时间。
 */

package com.itsaky.androidide.debugger.connection.aidl

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.itsaky.androidide.utils.ILogger

/**
 * 对单次探测的产物: 进程 id / 进程名 / uid。
 */
data class AppProcessInfo(
    val pid: Int,
    val processName: String,
    val uid: Int,
)

/**
 * 探测器抽象。生产用 [AidlProcessProbe.create],测试用 fake impl。
 */
interface AidlProcessProbe {
    /**
     * 找 packageName 关联的 running app process,返回首个匹配项。
     * 找不到 (或平台 API 不允许) 时返回 null。
     */
    fun findAppProcessInfo(packageName: String): AppProcessInfo?

    companion object {
        /** 默认生产实现。 */
        @JvmStatic
        fun create(@Suppress("UNUSED_PARAMETER") context: Context): AidlProcessProbe =
            ActivityManagerProbe()
    }
}

/**
 * ActivityManager.getRunningAppProcesses() 实现。
 *
 * Android 8+ (API 26) 起该 API 对自家 UID 仅返回子进程;
 * 但对 IDE 自身来说 (它和 target app 通常不在同一 UID),严格来说
 * 这个调用需要 PROCESS_STATE_PERSISTENT 权限或 root 才行。
 *
 * 实际场景: IDE 是 system/privileged 还是 user-installed?
 *   - ZeroStudio 是 user app,这里会返回空 — 这是 Android 8+ 的预期行为
 *   - 这种情况 [AidlProcessProbe.findAppProcessInfo] 会返回 null,
 *     调用方应该回退到"按需 startActivity + 等 accept"的策略
 *
 * 仍然保留这段代码,因为:
 *   1) 老版本 Android 还能用
 *   2) 子项目 3 (Shizuku) 走 ShizukuBinderProbe 会有更高权限的版本
 *   3) 子项目 4 (Root) 直接 cat /proc 读,绕过 ActivityManager
 */
class ActivityManagerProbe(
    private val activityManager: ActivityManager? = null,
) : AidlProcessProbe {

    private val log = ILogger.ROOT

    @Suppress("DEPRECATION")
    override fun findAppProcessInfo(packageName: String): AppProcessInfo? {
        if (packageName.isBlank()) return null
        val am = activityManager ?: return null
        return try {
            val procs = am.runningAppProcesses ?: return null
            // 取首个进程名 == packageName 的项;主进程通常就是 packageName。
            val match = procs.firstOrNull { it.processName == packageName } ?: return null
            log.debug("AidlProcessProbe: found {} pid={} uid={}", match.processName, match.pid, match.uid)
            AppProcessInfo(
                pid = match.pid,
                processName = match.processName,
                uid = match.uid,
            )
        } catch (se: SecurityException) {
            // Android 8+: 非系统进程拿不到 runningAppProcesses,安全拒绝
            log.debug("AidlProcessProbe: runningAppProcesses denied (api={})", Build.VERSION.SDK_INT)
            null
        } catch (t: Throwable) {
            log.warn("AidlProcessProbe: query failed", t)
            null
        }
    }
}

/** 测试用 fake,可指定固定的返回结果或抛错行为。 */
class FakeAidlProcessProbe(
    private val result: AppProcessInfo? = null,
) : AidlProcessProbe {
    override fun findAppProcessInfo(packageName: String): AppProcessInfo? = result
}
