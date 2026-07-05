/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuSubPathResolver: Auto 模式探测可用子路径, 返回第一个能用的。
 *
 *  4 个子路径 (按探测顺序):
 *    1. WifiAdb       - 复用 AIDL 方案, 不依赖 Shizuku 权限, 只要 host app 已装
 *    2. Binder        - 走 Shizuku binder 拿 host JDWP fd, 需要 Shizuku 已运行 + 授权
 *    3. InHostPlugin  - 走 Shizuku attachUserService, 需要 host 装了 plugin runtime
 *    4. Socks         - 走 Shizuku newProcess 启动 SOCKS5, 需要 Shizuku 已运行 + 授权
 *
 *  Auto 模式先尝试 WifiAdb (最宽松), 再 Binder (需 Shizuku), 再
 *  InHostPlugin (需 host plugin), 再 Socks (兜底, 走 SOCKS5)。
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
            // Shizuku 没运行, 只能走 WifiAdb (不依赖 Shizuku)
            log.info("ShizukuSubPathResolver: Shizuku not running, using WifiAdb")
            return ShizukuSubPath.WifiAdb
        }
        for (cap in capabilities) {
            val usability = cap.probeUsable(target)
            log.info("ShizukuSubPathResolver: probing {} -> usable={}, reason={}",
                cap.subPath, usability.isUsable, usability.reason)
            if (usability.isUsable) {
                return cap.subPath
            }
        }
        // 全部不可用, 退回 WifiAdb
        log.warn("ShizukuSubPathResolver: all sub-paths unusable, falling back to WifiAdb")
        return ShizukuSubPath.WifiAdb
    }
}
