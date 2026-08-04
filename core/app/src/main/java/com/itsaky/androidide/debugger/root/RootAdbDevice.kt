package com.itsaky.androidide.debugger.root

/**
 * Root ADB 桥接器管理的设备。
 *
 * @param type 设备类型
 * @param serial 设备序列号 / 地址（本机为 "this-device"，WiFi 为 "ip:port"，USB 为 usb serial）
 * @param model 设备型号（可空）
 * @param state 设备状态
 * @param isActive 是否为当前活动设备（决定命令执行页命令的目标）
 */
data class RootAdbDevice(
    val type: RootAdbDeviceType,
    val serial: String,
    val model: String? = null,
    val state: RootAdbDeviceState = RootAdbDeviceState.DEVICE,
    val isActive: Boolean = false,
)

enum class RootAdbDeviceType(val displayName: String) {
    LOCAL("本机"),
    WIFI("无线设备"),
    USB("USB 设备"),
}

enum class RootAdbDeviceState {
    DEVICE,      // 已连接可用
    OFFLINE,     // 离线
    UNAUTHORIZED, // 未授权
    CONNECTING,  // 连接中
}