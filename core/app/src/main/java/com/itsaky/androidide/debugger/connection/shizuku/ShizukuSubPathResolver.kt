/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuSubPathResolver: Auto 模式探测可用子路径, 返回第一个能用的。
 *
 *  JDWP-only Auto 子路径 (按探测顺序):
 *    1. InHostPlugin  - 走 Shizuku attachUserService,宿主内插件只做 JDWP 字节转发
 *    2. Socks         - 走宿主内 SOCKS5 server,CONNECT 到 localabstract:jdwp
 *
 *  旧 WifiAdb/AIDL 与 Binder-fd 子路径保留枚举兼容,但 Auto 不再选择。
 *
 *  ShizukuConfig.SubPath 枚举是子路径 id, 5 个值:
 *    Auto / WifiAdb / Binder / InHostPlugin / Socks
 *  本解析器就是围绕这个枚举做探测。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ShizukuConfig
import com.itsaky.androidide.utils.ILogger

/** SubPath 是 ShizukuConfig 的内部枚举。 */
typealias ShizukuSubPath = ShizukuConfig.SubPath

/**
 * 单条子路径的可用性。
 */
data class SubPathUsability(
    val isUsable: Boolean,
    val reason: String? = null,
)

/**
 * 单条子路径的能力 (探测用)。生产用 4 个 internal impl, 测试用 fake。
 */
interface ShizukuSubPathCapability {
    val subPath: ShizukuSubPath
    fun probeUsable(target: DebugTarget): SubPathUsability
}

/**
 * 解析器: Auto 模式按顺序问 4 条路径, 返回第一个 isUsable=true 的。
 * 显式模式 (subPath != Auto) 直接返回对应路径 (不探测, 等 connect 时再试)。
 */
class ShizukuSubPathResolver(
    private val probe: ShizukuProbe,
    private val capabilities: List<ShizukuSubPathCapability>,
) {
    private val log = ILogger.ROOT

    /**
     * 选最佳子路径。
     * @param subPath 显式指定 (非 Auto) 时直接返回, 不探测。
     */
    fun resolve(
        subPath: ShizukuSubPath,
        target: DebugTarget,
    ): ShizukuSubPath {
        if (subPath != ShizukuSubPath.Auto) {
            return subPath
        }
        val status = probe.probe()
        if (!status.isRunning) {
            log.info("ShizukuSubPathResolver: Shizuku not running; JDWP-only Shizuku paths unavailable")
            return ShizukuSubPath.Socks
        }
        for (cap in capabilities) {
            val usability = cap.probeUsable(target)
            log.info("ShizukuSubPathResolver: probing {} -> usable={}, reason={}",
                cap.subPath, usability.isUsable, usability.reason)
            if (usability.isUsable) {
                return cap.subPath
            }
        }
        // 全部不可用, 退回 Socks,由 attach 阶段给出明确错误; 不再回退 AIDL/WifiAdb。
        log.warn("ShizukuSubPathResolver: all JDWP-only sub-paths unusable, falling back to Socks")
        return ShizukuSubPath.Socks
    }
}
