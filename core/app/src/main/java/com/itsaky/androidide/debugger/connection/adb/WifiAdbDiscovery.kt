/*
 * ZeroStudio IDE - ADB 连接管理
 *
 * WifiAdbDiscovery: WiFi ADB mDNS 服务发现封装。
 *
 * 基于 libadb 模块的 AdbMdns, 发现 _adb-tls-connect._tcp 和
 * _adb-tls-pairing._tcp 服务, 用于 WiFi ADB 无线连接。
 *
 * 跟 debugger/android-adb-shell 参考工程的 AdbMdnsDiscoverer 一致,
 * 但暴露为 StateFlow 供 Compose UI 响应式观察。
 */

package com.itsaky.androidide.debugger.connection.adb

import android.content.Context
import android.net.nsd.NsdServiceInfo
import android.zero.studio.adb.android.AdbMdns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一个被发现的 WiFi ADB 服务。
 */
data class DiscoveredAdbService(
    val host: String,
    val port: Int,
    val serviceName: String,
    val serviceType: ServiceType,
) {
    enum class ServiceType { CONNECT, PAIRING }
}

/**
 * WiFi ADB mDNS 发现管理器。
 *
 * 使用 libadb 的 [AdbMdns] 发现局域网内的 ADB TLS 服务:
 *   - _adb-tls-connect._tcp: 可直接连接的 ADB 服务 (已配对)
 *   - _adb-tls-pairing._tcp: 需要配对的 ADB 服务 (新设备)
 *
 * 使用方式:
 *   val discovery = WifiAdbDiscovery(context)
 *   discovery.start()
 *   discovery.services.collect { services -> ... }
 *   discovery.stop()
 */
class WifiAdbDiscovery(private val context: Context) {

    private val _services = MutableStateFlow<List<DiscoveredAdbService>>(emptyList())
    val services: StateFlow<List<DiscoveredAdbService>> = _services.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var connectDiscovery: AdbMdns? = null
    private var pairingDiscovery: AdbMdns? = null

    /**
     * 启动 mDNS 服务发现。
     *
     * 同时监听 connect 和 pairing 两类服务,
     * 发现到的服务会通过 [services] StateFlow 推送。
     */
    fun start() {
        if (_isRunning.value) return
        _isRunning.value = true

        connectDiscovery = AdbMdns(
            context,
            AdbMdns.SERVICE_TYPE_TLS_CONNECT,
            object : AdbMdns.OnAdbDaemonDiscoveredListener {
                override fun onPortChanged(hostAddress: java.net.InetAddress?, port: Int, serviceName: String?) {
                    if (port > 0 && hostAddress != null && serviceName != null) {
                        addService(hostAddress.hostAddress ?: return, port, serviceName,
                            DiscoveredAdbService.ServiceType.CONNECT)
                    } else if (port < 0) {
                        // port < 0 表示服务丢失
                        removeService(serviceName)
                    }
                }
            },
        ).also { it.start() }

        pairingDiscovery = AdbMdns(
            context,
            AdbMdns.SERVICE_TYPE_TLS_PAIRING,
            object : AdbMdns.OnAdbDaemonDiscoveredListener {
                override fun onPortChanged(hostAddress: java.net.InetAddress?, port: Int, serviceName: String?) {
                    if (port > 0 && hostAddress != null && serviceName != null) {
                        addService(hostAddress.hostAddress ?: return, port, serviceName,
                            DiscoveredAdbService.ServiceType.PAIRING)
                    } else if (port < 0) {
                        removeService(serviceName)
                    }
                }
            },
        ).also { it.start() }
    }

    /**
     * 停止 mDNS 服务发现。
     */
    fun stop() {
        if (!_isRunning.value) return
        _isRunning.value = false
        connectDiscovery?.stop()
        pairingDiscovery?.stop()
        connectDiscovery = null
        pairingDiscovery = null
        _services.value = emptyList()
    }

    @Synchronized
    private fun addService(host: String, port: Int, serviceName: String, type: DiscoveredAdbService.ServiceType) {
        val current = _services.value.toMutableList()
        val newService = DiscoveredAdbService(host, port, serviceName, type)
        // 用 serviceName 去重, 同名服务更新端口/类型
        current.removeAll { it.serviceName == serviceName }
        current.add(newService)
        _services.value = current
    }

    @Synchronized
    private fun removeService(serviceName: String?) {
        if (serviceName == null) return
        val current = _services.value.toMutableList()
        current.removeAll { it.serviceName == serviceName }
        _services.value = current
    }
}
