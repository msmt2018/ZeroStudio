/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  HostPluginProbe: 探测 host app 是否装了 ide-debugger-host aar (能 bind UserService)。
 *
 *  Phase 14 之前, ShizukuSubPathCapabilities 的 InHostPluginCapability / SocksCapability
 * 走 `hostPluginProbe: (DebugTarget) -> Boolean = { _ -> true }` placeholder, Auto 模式
 * 永远返 true, 实际不可用也走 InHostPlugin / Socks 路径, 失败后才 fallback WifiAdb。
 *
 *  Phase 14 实装真探测: 走 `rikka.shizuku.Shizuku.bindUserService` 试 bind 一个
 *  ServiceConnection, 1.5s timeout, 拿 binder 立即 unbind (防止 host 端 user
 *  service 留资源 - Socks 路径下会启 SOCKS5 server 占用端口)。
 *
 *  返 true  = host 装了 aar (ServiceConnection.onServiceConnected 调过)
 *  返 false = 没装 / binder 死了 / security / 任何 throw
 *
 *  注: ShizukuBinderClient 没暴露 unbindUserService (Phase 15 修), 这里直接
 *  走 rikka.shizuku.Shizuku 公共 API, 不走抽象层。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.itsaky.androidide.utils.ILogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 探测 host app 装了 ide-debugger-host aar: 试 bind [componentName] user service。
 *
 *  1.5s timeout, 拿 binder 后立即 unbind (避免 host 端 user service leak)。
 *
 * @param componentName host app 内的 user service ComponentName
 *                      (package = target.packageName, class = Service FQN)
 * @param processName  host app 进程名 (e.g. target.packageName 主进程)
 * @param timeoutMs    probe timeout, 默认 1500ms (1.5s - 探测要给真 attach 留时间)
 * @return true = host 装了 aar 且 bind 成功
 */
suspend fun probeHostPluginUsable(
    componentName: ComponentName,
    processName: String,
    timeoutMs: Long = 1_500L,
): Boolean = withContext(Dispatchers.IO) {
    val log = ILogger.ROOT
    val latch = CountDownLatch(1)
    var connected = false
    val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            connected = true
            latch.countDown()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }
    val builder = Shizuku.UserServiceArgs(componentName)
        .processNameSuffix(processName)
        .daemon(false)
        .debuggable(false)
    try {
        // 跟 ShizukuBinderClient.DefaultShizukuBinderClient.bindUserService 走同款
        // UserServiceArgs 配置 (Shizuku 13.1.5 内部类)。
        Shizuku.bindUserService(builder, conn)
        val got = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (!got) {
            log.debug("probeHostPluginUsable: bind timed out after {}ms (component={})",
                timeoutMs, componentName)
        }
    } catch (t: Throwable) {
        // 任何 throw: security (Shizuku 权限问题) / RemoteException / SecurityException
        log.debug("probeHostPluginUsable: bind threw {} (component={})",
            t.message, componentName)
        return@withContext false
    } finally {
        // 立即 unbind, host 端 user service 不要留着 (Socks 路径下会启 SOCKS5 server
        // 占用端口, 留到下次 attach 会冲突; InHostPlugin 路径下 user service 也别 leak)。
        runCatching { Shizuku.unbindUserService(builder, conn, true) }
            .onFailure { log.debug("probeHostPluginUsable: unbind failed: {}", it.message) }
    }
    connected
}
