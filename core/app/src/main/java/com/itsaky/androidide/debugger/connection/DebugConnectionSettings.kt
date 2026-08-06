/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  DebugConnectionSettings: 5 种连接方式的所有可配参数汇总。
 *  持久化由 DebugConnectionPreferences(utilities/preferences 模块)负责。
 *  运行时由 DebugConnectionFactory.create(type, target, settings) 传入具体实现。
 */

package com.itsaky.androidide.debugger.connection

/**
 * AIDL Socket 方案参数。
 * @param listenPort IDE 端在设备上起的 LocalServerSocket 端口 (0 = 随机)
 * @param hostStubPackage 宿主体内 stub 的包名 (默认 = target.packageName)
 * @param requireHostForeground 是否要求宿主 app 在前台
 */
data class AidlSocketConfig(
    val listenPort: Int = 0,
    val hostStubPackage: String? = null,
    val requireHostForeground: Boolean = true,
)

/**
 * Shizuku 方案参数。
 * @param subPath 手动锁定子路径;Auto = 按 A→B→C→D 顺序探测
 * @param binderTimeoutMs binder 调用超时
 * @param hostPluginAssetName C 路径注入的 stub apk asset 名
 * @param enableSocksFallback 失败时是否降级到 SOCKS5 出口 (子路径 D)
 * @param socksHost SOCKS5 子路径专用: IDE 端 Socks5Client 连的代理地址 (默认 127.0.0.1)
 * @param socksPort SOCKS5 子路径专用: SOCKS5 代理端口。默认 39939, 跟 host 端
 *        IdeShizukuSocksUserService.DEFAULT_SOCKS_PORT 一致, 端到端 Socks 路径
 *        默认跑通 (无需用户手动配)
 */
data class ShizukuConfig(
    val subPath: SubPath = SubPath.Auto,
    val binderTimeoutMs: Long = 3_000L,
    val hostPluginAssetName: String = "shizuku_host_bridge.apk",
    val enableSocksFallback: Boolean = true,
    val socksHost: String = "127.0.0.1",
    val socksPort: Int = 39939,
) {
    enum class SubPath { Auto, WifiAdb, Binder, InHostPlugin, Socks }
}

/**
 * Root 方案参数。
 * @param suBinPath `su` 路径 (默认 /system/bin/su)
 * @param probeTimeoutMs /proc/net/unix 探测超时
 * @param allowMagiskSu 是否接受 Magisk su
 */
data class RootConfig(
    val suBinPath: String = "/system/bin/su",
    val probeTimeoutMs: Long = 5_000L,
    val allowMagiskSu: Boolean = true,
)

/**
 * 内网虚拟机 SOCKS5 代理方案参数。
 * @param socksHost SOCKS5 代理地址 (VM 端提供的代理监听地址)
 * @param socksPort SOCKS5 代理端口
 * @param socksUser 代理用户名 (可选)
 * @param socksPassword 代理密码 (可选)
 * @param connectTimeoutMs SOCKS5 建链超时
 */
data class InnetSocksConfig(
    val socksHost: String = "127.0.0.1",
    val socksPort: Int = 1080,
    val socksUser: String? = null,
    val socksPassword: String? = null,
    val connectTimeoutMs: Long = 10_000L,
)

/**
 * 内网虚拟机 ADB 网络端口转发方案参数。
 * @param adbHost VM 端 adb 监听地址 (一般是 127.0.0.1 或 VM 内部 IP)
 * @param adbPort VM 端 adb 端口 (默认 5555)
 * @param adbSerial 多设备时指定 serial
 * @param connectTimeoutMs adb connect 超时
 */
data class InnetAdbConfig(
    val adbHost: String = "127.0.0.1",
    val adbPort: Int = 5555,
    val adbSerial: String? = null,
    val connectTimeoutMs: Long = 5_000L,
)

/**
 * USB / 局域网 ADB 方案参数。
 * @param adbHost adb 主机 IP
 * @param adbPort adb 端口 (USB 默认 5037, LAN 一般 5555)
 * @param adbSerial 多设备时指定 serial
 * @param connectTimeoutMs adb connect 超时
 */
data class UsbLanConfig(
    val adbHost: String = "127.0.0.1",
    val adbPort: Int = 5037,
    val adbSerial: String? = null,
    val connectTimeoutMs: Long = 5_000L,
)

/**
 *  6 种方案的参数汇总 + 全局开关。
 * 通过 [DebugConnectionPreferences] 读写持久化。
 */
data class DebugConnectionSettings(
    val activeType: ConnectionType = ConnectionType.UsbLan,
    val autoRetry: Boolean = true,
    val retryMaxAttempts: Int = 3,
    val retryInitialDelayMs: Long = 500L,
    val aidlSocket: AidlSocketConfig = AidlSocketConfig(),
    val shizuku: ShizukuConfig = ShizukuConfig(),
    val root: RootConfig = RootConfig(),
    val innetSocks: InnetSocksConfig = InnetSocksConfig(),
    val innetAdb: InnetAdbConfig = InnetAdbConfig(),
    val usbLan: UsbLanConfig = UsbLanConfig(),
)
