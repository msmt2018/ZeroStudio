/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  DebugConnectionPreferences: 在 core/app 模块里持有 DebugConnectionSettings
 *  的持久化访问。
 *
 *  之所以没放进 utilities/preferences: utilities/preferences 只依赖
 *  core/common,反向引用 core/app/.../connection/ConnectionType 会形成
 *  循环依赖。所以 settings 数据类留在 core/app,持久化访问也放这。
 *
 *  后续如果 ConnectionType 等抽象类被上移到 core/common,这一层就可以
 *  下沉到 utilities/preferences。
 */

package com.itsaky.androidide.debugger.connection

import com.itsaky.androidide.preferences.internal.prefManager

/**
 * 把 DebugConnectionSettings 的字段摊平存到 prefManager (类似 BuildPreferences)。
 */
@Suppress("MemberVisibilityCanBePrivate")
object DebugConnectionPreferences {

    // ---- 顶层 key ----
    const val ACTIVE_TYPE = "ide.debugger.connection.activeType"
    const val AUTO_RETRY = "ide.debugger.connection.autoRetry"
    const val RETRY_MAX_ATTEMPTS = "ide.debugger.connection.retryMaxAttempts"
    const val RETRY_INITIAL_DELAY_MS = "ide.debugger.connection.retryInitialDelayMs"

    // ---- AIDL Socket ----
    const val AIDL_LISTEN_PORT = "ide.debugger.connection.aidl.listenPort"
    const val AIDL_HOST_STUB_PACKAGE = "ide.debugger.connection.aidl.hostStubPackage"
    const val AIDL_REQUIRE_FOREGROUND = "ide.debugger.connection.aidl.requireForeground"

    // ---- Shizuku ----
    const val SHIZUKU_SUB_PATH = "ide.debugger.connection.shizuku.subPath"
    const val SHIZUKU_BINDER_TIMEOUT_MS = "ide.debugger.connection.shizuku.binderTimeoutMs"
    const val SHIZUKU_PLUGIN_ASSET = "ide.debugger.connection.shizuku.pluginAsset"
    const val SHIZUKU_SOCKS_FALLBACK = "ide.debugger.connection.shizuku.socksFallback"

    // ---- Root ----
    const val ROOT_SU_BIN = "ide.debugger.connection.root.suBin"
    const val ROOT_PROBE_TIMEOUT_MS = "ide.debugger.connection.root.probeTimeoutMs"
    const val ROOT_ALLOW_MAGISK = "ide.debugger.connection.root.allowMagisk"

    // ---- InnetVmSocks 代理 ----
    const val INNET_SOCKS_HOST = "ide.debugger.connection.innetSocks.socksHost"
    const val INNET_SOCKS_PORT = "ide.debugger.connection.innetSocks.socksPort"
    const val INNET_SOCKS_USER = "ide.debugger.connection.innetSocks.socksUser"
    const val INNET_SOCKS_PASSWORD = "ide.debugger.connection.innetSocks.socksPassword"
    const val INNET_SOCKS_CONNECT_TIMEOUT_MS = "ide.debugger.connection.innetSocks.connectTimeoutMs"

    // ---- InnetVmAdb 端口转发 ----
    const val INNET_ADB_HOST = "ide.debugger.connection.innetAdb.adbHost"
    const val INNET_ADB_PORT = "ide.debugger.connection.innetAdb.adbPort"
    const val INNET_ADB_SERIAL = "ide.debugger.connection.innetAdb.adbSerial"
    const val INNET_ADB_CONNECT_TIMEOUT_MS = "ide.debugger.connection.innetAdb.connectTimeoutMs"

    // ---- UsbLan ----
    const val USB_ADB_HOST = "ide.debugger.connection.usb.adbHost"
    const val USB_ADB_PORT = "ide.debugger.connection.usb.adbPort"
    const val USB_ADB_SERIAL = "ide.debugger.connection.usb.adbSerial"
    const val USB_CONNECT_TIMEOUT_MS = "ide.debugger.connection.usb.connectTimeoutMs"

    // ---- 顶层 var ----
    var activeTypeId: String
        get() = prefManager.getString(ACTIVE_TYPE, ConnectionType.AidlSocket.id)!!
        set(value) { prefManager.putString(ACTIVE_TYPE, value) }

    /**
     * 读时用 fromIdCompat 把旧 id "innet_vm" 映射到新的 SOCKS5 方案,
     * 保证从旧版本升级上来的用户不会回到默认 AIDL socket。
     */
    var activeType: ConnectionType
        get() = ConnectionType.fromIdCompat(prefManager.getString(ACTIVE_TYPE, null))
        set(value) { prefManager.putString(ACTIVE_TYPE, value.id) }

    var autoRetry: Boolean
        get() = prefManager.getBoolean(AUTO_RETRY, true)
        set(value) { prefManager.putBoolean(AUTO_RETRY, value) }

    var retryMaxAttempts: Int
        get() = prefManager.getInt(RETRY_MAX_ATTEMPTS, 3)
        set(value) { prefManager.putInt(RETRY_MAX_ATTEMPTS, value) }

    var retryInitialDelayMs: Long
        get() = prefManager.getLong(RETRY_INITIAL_DELAY_MS, 500L)
        set(value) { prefManager.putLong(RETRY_INITIAL_DELAY_MS, value) }

    // ---- AIDL Socket ----
    var aidlListenPort: Int
        get() = prefManager.getInt(AIDL_LISTEN_PORT, 0)
        set(value) { prefManager.putInt(AIDL_LISTEN_PORT, value) }

    var aidlHostStubPackage: String?
        get() = prefManager.getString(AIDL_HOST_STUB_PACKAGE, null)
        set(value) { prefManager.putString(AIDL_HOST_STUB_PACKAGE, value) }

    var aidlRequireForeground: Boolean
        get() = prefManager.getBoolean(AIDL_REQUIRE_FOREGROUND, true)
        set(value) { prefManager.putBoolean(AIDL_REQUIRE_FOREGROUND, value) }

    // ---- Shizuku ----
    var shizukuSubPath: ShizukuConfig.SubPath
        get() = prefManager.getString(SHIZUKU_SUB_PATH, null)
            ?.let { runCatching { ShizukuConfig.SubPath.valueOf(it) }.getOrNull() }
            ?: ShizukuConfig.SubPath.Auto
        set(value) { prefManager.putString(SHIZUKU_SUB_PATH, value.name) }

    var shizukuBinderTimeoutMs: Long
        get() = prefManager.getLong(SHIZUKU_BINDER_TIMEOUT_MS, 3_000L)
        set(value) { prefManager.putLong(SHIZUKU_BINDER_TIMEOUT_MS, value) }

    var shizukuPluginAsset: String
        get() = prefManager.getString(SHIZUKU_PLUGIN_ASSET, "shizuku_host_bridge.apk")!!
        set(value) { prefManager.putString(SHIZUKU_PLUGIN_ASSET, value) }

    var shizukuSocksFallback: Boolean
        get() = prefManager.getBoolean(SHIZUKU_SOCKS_FALLBACK, true)
        set(value) { prefManager.putBoolean(SHIZUKU_SOCKS_FALLBACK, value) }

    // ---- Root ----
    var rootSuBin: String
        get() = prefManager.getString(ROOT_SU_BIN, "/system/bin/su")!!
        set(value) { prefManager.putString(ROOT_SU_BIN, value) }

    var rootProbeTimeoutMs: Long
        get() = prefManager.getLong(ROOT_PROBE_TIMEOUT_MS, 5_000L)
        set(value) { prefManager.putLong(ROOT_PROBE_TIMEOUT_MS, value) }

    var rootAllowMagisk: Boolean
        get() = prefManager.getBoolean(ROOT_ALLOW_MAGISK, true)
        set(value) { prefManager.putBoolean(ROOT_ALLOW_MAGISK, value) }

    // ---- InnetVmSocks ----
    var innetSocksHost: String
        get() = prefManager.getString(INNET_SOCKS_HOST, "127.0.0.1")!!
        set(value) { prefManager.putString(INNET_SOCKS_HOST, value) }

    var innetSocksPort: Int
        get() = prefManager.getInt(INNET_SOCKS_PORT, 1080)
        set(value) { prefManager.putInt(INNET_SOCKS_PORT, value) }

    var innetSocksUser: String?
        get() = prefManager.getString(INNET_SOCKS_USER, null)
        set(value) { prefManager.putString(INNET_SOCKS_USER, value) }

    var innetSocksPassword: String?
        get() = prefManager.getString(INNET_SOCKS_PASSWORD, null)
        set(value) { prefManager.putString(INNET_SOCKS_PASSWORD, value) }

    var innetSocksConnectTimeoutMs: Long
        get() = prefManager.getLong(INNET_SOCKS_CONNECT_TIMEOUT_MS, 10_000L)
        set(value) { prefManager.putLong(INNET_SOCKS_CONNECT_TIMEOUT_MS, value) }

    // ---- InnetVmAdb ----
    var innetAdbHost: String
        get() = prefManager.getString(INNET_ADB_HOST, "127.0.0.1")!!
        set(value) { prefManager.putString(INNET_ADB_HOST, value) }

    var innetAdbPort: Int
        get() = prefManager.getInt(INNET_ADB_PORT, 5555)
        set(value) { prefManager.putInt(INNET_ADB_PORT, value) }

    var innetAdbSerial: String?
        get() = prefManager.getString(INNET_ADB_SERIAL, null)
        set(value) { prefManager.putString(INNET_ADB_SERIAL, value) }

    var innetAdbConnectTimeoutMs: Long
        get() = prefManager.getLong(INNET_ADB_CONNECT_TIMEOUT_MS, 5_000L)
        set(value) { prefManager.putLong(INNET_ADB_CONNECT_TIMEOUT_MS, value) }

    // ---- UsbLan ----
    var usbAdbHost: String
        get() = prefManager.getString(USB_ADB_HOST, "127.0.0.1")!!
        set(value) { prefManager.putString(USB_ADB_HOST, value) }

    var usbAdbPort: Int
        get() = prefManager.getInt(USB_ADB_PORT, 5037)
        set(value) { prefManager.putInt(USB_ADB_PORT, value) }

    var usbAdbSerial: String?
        get() = prefManager.getString(USB_ADB_SERIAL, null)
        set(value) { prefManager.putString(USB_ADB_SERIAL, value) }

    var usbConnectTimeoutMs: Long
        get() = prefManager.getLong(USB_CONNECT_TIMEOUT_MS, 5_000L)
        set(value) { prefManager.putLong(USB_CONNECT_TIMEOUT_MS, value) }

    // ---- 整体读写 ----
    fun load(): DebugConnectionSettings = DebugConnectionSettings(
        activeType = activeType,
        autoRetry = autoRetry,
        retryMaxAttempts = retryMaxAttempts,
        retryInitialDelayMs = retryInitialDelayMs,
        aidlSocket = AidlSocketConfig(
            listenPort = aidlListenPort,
            hostStubPackage = aidlHostStubPackage,
            requireHostForeground = aidlRequireForeground,
        ),
        shizuku = ShizukuConfig(
            subPath = shizukuSubPath,
            binderTimeoutMs = shizukuBinderTimeoutMs,
            hostPluginAssetName = shizukuPluginAsset,
            enableSocksFallback = shizukuSocksFallback,
        ),
        root = RootConfig(
            suBinPath = rootSuBin,
            probeTimeoutMs = rootProbeTimeoutMs,
            allowMagiskSu = rootAllowMagisk,
        ),
        innetSocks = InnetSocksConfig(
            socksHost = innetSocksHost,
            socksPort = innetSocksPort,
            socksUser = innetSocksUser,
            socksPassword = innetSocksPassword,
            connectTimeoutMs = innetSocksConnectTimeoutMs,
        ),
        innetAdb = InnetAdbConfig(
            adbHost = innetAdbHost,
            adbPort = innetAdbPort,
            adbSerial = innetAdbSerial,
            connectTimeoutMs = innetAdbConnectTimeoutMs,
        ),
        usbLan = UsbLanConfig(
            adbHost = usbAdbHost,
            adbPort = usbAdbPort,
            adbSerial = usbAdbSerial,
            connectTimeoutMs = usbConnectTimeoutMs,
        ),
    )

    fun save(settings: DebugConnectionSettings) {
        activeType = settings.activeType
        autoRetry = settings.autoRetry
        retryMaxAttempts = settings.retryMaxAttempts
        retryInitialDelayMs = settings.retryInitialDelayMs
        aidlListenPort = settings.aidlSocket.listenPort
        aidlHostStubPackage = settings.aidlSocket.hostStubPackage
        aidlRequireForeground = settings.aidlSocket.requireHostForeground
        shizukuSubPath = settings.shizuku.subPath
        shizukuBinderTimeoutMs = settings.shizuku.binderTimeoutMs
        shizukuPluginAsset = settings.shizuku.hostPluginAssetName
        shizukuSocksFallback = settings.shizuku.enableSocksFallback
        rootSuBin = settings.root.suBinPath
        rootProbeTimeoutMs = settings.root.probeTimeoutMs
        rootAllowMagisk = settings.root.allowMagiskSu
        innetSocksHost = settings.innetSocks.socksHost
        innetSocksPort = settings.innetSocks.socksPort
        innetSocksUser = settings.innetSocks.socksUser
        innetSocksPassword = settings.innetSocks.socksPassword
        innetSocksConnectTimeoutMs = settings.innetSocks.connectTimeoutMs
        innetAdbHost = settings.innetAdb.adbHost
        innetAdbPort = settings.innetAdb.adbPort
        innetAdbSerial = settings.innetAdb.adbSerial
        innetAdbConnectTimeoutMs = settings.innetAdb.connectTimeoutMs
        usbAdbHost = settings.usbLan.adbHost
        usbAdbPort = settings.usbLan.adbPort
        usbAdbSerial = settings.usbLan.adbSerial
        usbConnectTimeoutMs = settings.usbLan.connectTimeoutMs
    }
}
