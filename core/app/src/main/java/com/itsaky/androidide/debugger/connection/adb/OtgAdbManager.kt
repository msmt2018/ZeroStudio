/*
 * ZeroStudio IDE - ADB 连接管理
 *
 * OtgAdbManager: USB OTG ADB 设备管理器。
 *
 * 基于 adblib 模块的 UsbChannel + Android UsbManager,
 * 枚举支持 ADB 协议的 USB 设备并建立连接。
 *
 * 跟 debugger/android-adb-shell 参考工程的 OtgAdbConnectionManager 一致,
 * 但暴露为 StateFlow 供 Compose UI 响应式观察。
 */

package com.itsaky.androidide.debugger.connection.adb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一个被发现的 OTG ADB USB 设备。
 */
data class OtgAdbDevice(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val interfaceCount: Int,
    val hasAdbInterface: Boolean,
)

/**
 * OTG ADB USB 设备管理器。
 *
 * 使用 Android [UsbManager] 枚举 USB 设备, 识别支持 ADB 协议的接口:
 *   - Interface Class: 0xFF (Vendor Specific)
 *   - Interface Subclass: 0x42 (ADB)
 *   - Interface Protocol: 0x01 (ADB Protocol)
 *
 * 发现到的设备通过 [devices] StateFlow 推送, 连接后通过 adblib 的
 * [android.zero.studio.adblib.UsbChannel] 建立 ADB 通信。
 */
class OtgAdbManager(private val context: Context) {

    companion object {
        private const val TAG = "OtgAdbManager"
        private const val ACTION_USB_PERMISSION = "android.zero.studio.otg.USB_PERMISSION"

        // ADB USB 接口标识 (跟 android-adb-shell 参考工程一致)
        private const val ADB_INTERFACE_CLASS = 0xFF
        private const val ADB_INTERFACE_SUBCLASS = 0x42
        private const val ADB_INTERFACE_PROTOCOL = 0x01
    }

    private val _devices = MutableStateFlow<List<OtgAdbDevice>>(emptyList())
    val devices: StateFlow<List<OtgAdbDevice>> = _devices.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var usbReceiver: BroadcastReceiver? = null
    private var isRegistered = false

    /**
     * 开始监听 USB 设备插拔事件。
     */
    fun start() {
        if (_isRunning.value) return
        _isRunning.value = true

        registerReceivers()
        refreshDeviceList()
    }

    /**
     * 停止监听。
     */
    fun stop() {
        if (!_isRunning.value) return
        _isRunning.value = false
        unregisterReceivers()
        _devices.value = emptyList()
    }

    /**
     * 手动刷新 USB 设备列表。
     */
    fun refreshDeviceList() {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val deviceList = manager.deviceList.values.map { device ->
            OtgAdbDevice(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                interfaceCount = device.interfaceCount,
                hasAdbInterface = findAdbInterface(device) != null,
            )
        }
        _devices.value = deviceList
    }

    /**
     * 请求 USB 设备访问权限。
     *
     * @return true 表示已发起权限请求, false 表示设备不存在或无需请求。
     */
    fun requestPermission(deviceName: String): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val device = manager.deviceList.values.firstOrNull { it.deviceName == deviceName } ?: return false
        if (manager.hasPermission(device)) return true
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        manager.requestPermission(device, pendingIntent)
        return true
    }

    /**
     * 检查是否已获得 USB 设备访问权限。
     */
    fun hasPermission(deviceName: String): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val device = manager.deviceList.values.firstOrNull { it.deviceName == deviceName } ?: return false
        return manager.hasPermission(device)
    }

    private fun registerReceivers() {
        if (isRegistered) return
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED,
                    ACTION_USB_PERMISSION -> refreshDeviceList()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        ContextCompat.registerReceiver(
            context, usbReceiver!!, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isRegistered = true
    }

    private fun unregisterReceivers() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        usbReceiver = null
        isRegistered = false
    }

    /**
     * 在 USB 设备上查找 ADB 接口。
     */
    private fun findAdbInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == ADB_INTERFACE_CLASS &&
                intf.interfaceSubclass == ADB_INTERFACE_SUBCLASS &&
                intf.interfaceProtocol == ADB_INTERFACE_PROTOCOL
            ) {
                return intf
            }
        }
        return null
    }

    /**
     * 判断 USB 设备是否支持 ADB 协议。
     */
    fun isAdbDevice(device: UsbDevice): Boolean = findAdbInterface(device) != null
}
