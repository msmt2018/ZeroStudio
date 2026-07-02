/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  AidlHostLauncher: 拉起目标 host app 并把 IDE 端 ServerSocket 端口
 *  通过 intent extra 传过去,让 host 端的 ADRT runtime (子项目 8) 拿到
 *  这个端口后 reverse-connect 回来。
 *
 *  intent extra key (子项目 8 同样使用): "ide.debug.aidl.port"
 *  intent 优先用 packageManager.getLaunchIntentForPackage()(系统标准),
 *  找不到时回退到显式 component (packageName / mainActivity) 拼装。
 *
 *  调用入口在主线程;startActivity 本身是同步的,失败时返回 false 让
 *  调用方重试或上报 HostAppNotRunning 错误。
 */

package com.itsaky.androidide.debugger.connection.aidl

import android.content.Context
import android.content.Intent
import com.itsaky.androidide.utils.ILogger

/**
 * Host 启动器抽象。生产用 [AidlHostLauncher.create],测试用 fake。
 */
interface AidlHostLauncher {
    /**
     * @param packageName 目标 app 包名
     * @param mainActivity 目标 app 的主 Activity 全限定名
     * @param port IDE 端 ServerSocket 监听端口
     * @return true 表示 startActivity 调用成功 (不代表 host 已经运行)
     */
    fun launch(packageName: String, mainActivity: String, port: Int): Boolean

    /** intent extra key,子项目 8 host 端 ADRT runtime 读这个 key。 */
    val intentExtraPortKey: String get() = EXTRA_PORT

    companion object {
        const val EXTRA_PORT = "ide.debug.aidl.port"

        @JvmStatic
        fun create(@Suppress("UNUSED_PARAMETER") context: Context): AidlHostLauncher =
            IntentHostLauncher()
    }
}

/** 基于 [Intent] + [Context.startActivity] 的标准实现。 */
class IntentHostLauncher : AidlHostLauncher {

    private val log = ILogger.ROOT

    override fun launch(packageName: String, mainActivity: String, port: Int): Boolean {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(mainActivity.isNotBlank()) { "mainActivity must not be blank" }
        require(port in 1..65535) { "port out of range: $port" }

        // 1) 优先用系统的 launch intent (会带 CATEGORY_LAUNCHER 等),
        //    这对调试体验最好(用户切回 host app 时不会重新拉起新的实例)。
        // 2) 拿不到时再用显式 component。
        val intent: Intent? = try {
            // 静态 API,任何 Android 版本都可用。
            // 但 API 33+ 推荐 getLaunchIntentSenderForPackage + IntentSender,
            // 我们这里延用 startActivity 因为它简单、调试体验稳定。
            @Suppress("DEPRECATION")
            packageManagerIntent(packageName)
        } catch (t: Throwable) {
            log.warn("AidlHostLauncher: getLaunchIntentForPackage failed: {}", t.message)
            null
        }
        val effectiveIntent = intent ?: run {
            log.debug("AidlHostLauncher: no launch intent for {}, falling back to component", packageName)
            Intent().apply {
                component = android.content.ComponentName(packageName, mainActivity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        effectiveIntent.putExtra(EXTRA_PORT, port)
        effectiveIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            log.info("AidlHostLauncher: launching {} with port={}", packageName, port)
            appContext.startActivity(effectiveIntent)
            true
        } catch (se: SecurityException) {
            log.warn("AidlHostLauncher: SecurityException: {}", se.message)
            false
        } catch (t: Throwable) {
            log.warn("AidlHostLauncher: startActivity failed for {}", packageName, t)
            false
        }
    }

    // 让测试能注入 context;生产路径上从 IDEApplication.instance 拿。
    private val appContext: Context
        get() = com.itsaky.androidide.app.IDEApplication.instance

    @Suppress("DEPRECATION")
    private fun packageManagerIntent(packageName: String): Intent? =
        appContext.packageManager.getLaunchIntentForPackage(packageName)
}

/** 测试用 fake launcher,记录最后一次调用的参数,可指定返回结果。 */
class FakeAidlHostLauncher(
    private val shouldSucceed: Boolean = true,
) : AidlHostLauncher {
    var lastPackageName: String? = null
        private set
    var lastMainActivity: String? = null
        private set
    var lastPort: Int = 0
        private set
    var callCount: Int = 0
        private set

    override fun launch(packageName: String, mainActivity: String, port: Int): Boolean {
        callCount++
        lastPackageName = packageName
        lastMainActivity = mainActivity
        lastPort = port
        return shouldSucceed
    }
}
