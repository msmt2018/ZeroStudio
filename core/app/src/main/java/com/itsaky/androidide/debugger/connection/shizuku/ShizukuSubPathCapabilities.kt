/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  Phase 13a: 实装 4 个 ShizukuSubPathCapability, 之前 ShizukuConnection
 * 传 listOf() 空 capabilities, Auto 模式 for 循环空迭代直接走 fallback
 * WifiAdb, 4 个 capability 全部 missing。
 *
 *  Auto 模式只保留不引入 AIDL 数据通道的 JDWP 字节流路径:
 *    - InHostPluginCapability  探测 host app 装了 ide-debugger-host aar
 *    - SocksCapability         通过 host 内 SOCKS5 server 转发到 localabstract:jdwp
 *
 *  WifiAdb/AIDL 与 Binder-fd 路径不再参与 Auto,避免跟 JDWP 握手/包流竞争。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ShizukuConfig
import com.itsaky.androidide.utils.ILogger

/**
 * WifiAdb 子路径 capability: 走 adb connect + adb forward, 不依赖 Shizuku。
 * 探测: 检查 target.adbSerial 可达 + adb forward 可建。
 *
 * 注: 这是最低门槛, Shizuku 没运行时 Auto 模式直接走这条 (由 resolver 走
 * `!status.isRunning` 短路, 这里 capability 是显式 WifiAdb 模式时用)。
 */
class WifiAdbCapability(
    private val adbProbe: (DebugTarget) -> Boolean = { _ ->
        // 默认: trust caller's pre-flight adb connect probe; 实际可用性
        // 由 AdbForwardConnection.runPreConnectCheck 在 connect() 阶段验证。
        // 这里返 true 是 "WifiAdb 总是探得到" (在 Shizuku 跑了之后, Auto 模式
        // 至少能给个回退选项; 如果 adb 不可达后续 connect 阶段会报错)。
        true
    },
) : ShizukuSubPathCapability {
    override val subPath = ShizukuConfig.SubPath.WifiAdb
    override fun probeUsable(target: DebugTarget): SubPathUsability {
        return if (adbProbe(target)) {
            SubPathUsability(isUsable = true)
        } else {
            SubPathUsability(isUsable = false, reason = "adbSerial 为空, WifiAdb 不可用")
        }
    }
}

/**
 * Binder 子路径 capability: 走 Shizuku.transferFileDescriptor 拿 host JDWP fd。
 *
 * 限制: Shizuku 12+ transferFileDescriptor 接口已弃用, 14+ 才有真实现。
 * 当前 (12/13) 走 binder 不能拿 fd, Auto 模式不选这条; 显式 Binder 模式走
 * ShizukuConnection.attachViaBinder 走 InHostPlugin workaround (Phase 12k 实装)。
 *
 * Phase 13d TODO: Shizuku 14+ 之后实装真 transferFileDescriptor 路径。
 */
class BinderCapability(
    private val serverApiVersion: Int = -1,
) : ShizukuSubPathCapability {
    override val subPath = ShizukuConfig.SubPath.Binder
    override fun probeUsable(target: DebugTarget): SubPathUsability {
        // Shizuku 12/13: transferFileDescriptor 不可用, 返 false
        // Shizuku 14+: 检测 serverApiVersion >= 14, 返 true
        return if (serverApiVersion >= 14) {
            SubPathUsability(isUsable = true)
        } else {
            SubPathUsability(
                isUsable = false,
                reason = "Shizuku 12/13 transferFileDescriptor 已弃用, " +
                    "需 14+ (当前 serverApiVersion=$serverApiVersion)",
            )
        }
    }
}

/**
 * InHostPlugin 子路径 capability: 走 Shizuku.attachUserService 拉 host 端
 * HostPluginService (走 InHostPlugin path, Phase 12q 修的资源关闭 + daemon 修过)。
 *
 * 探测: 检查 host app 装了 ide-debugger-host aar (走 host ContentProvider probe,
 * Phase 12p 修的 LocalServerSocket "HostAttachAgentServer" 路径, IDE 端
 * 走 adb shell content query 试连, 1s 内返则有 aar)。
 */
class InHostPluginCapability(
    private val hostPluginProbe: (DebugTarget) -> Boolean = { _ -> true },
) : ShizukuSubPathCapability {
    override val subPath = ShizukuConfig.SubPath.InHostPlugin
    override fun probeUsable(target: DebugTarget): SubPathUsability {
        return if (hostPluginProbe(target)) {
            SubPathUsability(isUsable = true)
        } else {
            SubPathUsability(
                isUsable = false,
                reason = "host app 未装 ide-debugger-host aar, InHostPlugin 不可用",
            )
        }
    }
}

/**
 * Socks 子路径 capability: 走 Shizuku.newProcess 起 SOCKS5 server 或 Shizuku
 * attachUserService 拉 host IdeShizukuSocksUserService。
 *
 * 限制: Phase 12u 锁死 ShizukuBinderClient.newProcess throw UOE, 当前走
 * attachUserService + default 39939 端口 (Phase 12j 实装)。
 *
 * 探测: 走 InHostPluginCapability 同款 host plugin probe (host app 装了
 * ide-debugger-host aar 就有 IdeShizukuSocksUserService)。Phase 12y 实装
 * ISocksControl AIDL + transact 后, custom port 走 binder 协议可用。
 */
class SocksCapability(
    private val hostPluginProbe: (DebugTarget) -> Boolean = { _ -> true },
) : ShizukuSubPathCapability {
    override val subPath = ShizukuConfig.SubPath.Socks
    override fun probeUsable(target: DebugTarget): SubPathUsability {
        return if (hostPluginProbe(target)) {
            SubPathUsability(isUsable = true)
        } else {
            SubPathUsability(
                isUsable = false,
                reason = "host app 未装 ide-debugger-host aar, Socks 路径不可用",
            )
        }
    }
}

/**
 * JDWP-only capability 默认组装 (按 ShizukuSubPathResolver 探测顺序):
 * 1. InHostPlugin (host 装 plugin 返 true)
 * 2. Socks (host 装 plugin 返 true)
 *
 * WifiAdb/AIDL 与 Binder-fd 均不再作为断点调试器自动路径。
 */
fun defaultShizukuSubPathCapabilities(
    serverApiVersion: Int = -1,
    @Suppress("UNUSED_PARAMETER") adbProbe: (DebugTarget) -> Boolean = { _ -> true },
    hostPluginProbe: (DebugTarget) -> Boolean = { _ -> true },
): List<ShizukuSubPathCapability> = listOf(
    InHostPluginCapability(hostPluginProbe),
    SocksCapability(hostPluginProbe),
)
