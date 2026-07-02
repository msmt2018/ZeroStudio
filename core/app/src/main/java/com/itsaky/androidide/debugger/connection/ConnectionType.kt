/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ConnectionType: 6 种连接方式的密封枚举 + 元数据。
 *  默认 AIDL_SOCKET,运行时由用户在设置页手动切换。
 *  6 种实现完全独立,运行时只存在一种,无跨方案降级。
 *
 *  注: 内网虚拟机拆分成了 SOCKS5 代理 和 ADB 端口转发 两个独立方案,
 *  因为它们走完全不同的技术栈 (Proxy 协议 vs adb forward),
 *  在同一个 connection 内会引入不必要的复杂度。
 */

package com.itsaky.androidide.debugger.connection

sealed class ConnectionType(
    val id: String,
    val displayName: String,
    val requiresRoot: Boolean,
    val requiresShizuku: Boolean,
) {
    /** AIDL + LocalServerSocket 反向连,免 root,需要宿主体内有 stub */
    object AidlSocket : ConnectionType(
        id = "aidl_socket",
        displayName = "AIDL Socket (免Root)",
        requiresRoot = false,
        requiresShizuku = false,
    )

    /** Shizuku binder / ADB / 隐藏 API 综合方案,需要 Shizuku app 已授权 */
    object Shizuku : ConnectionType(
        id = "shizuku",
        displayName = "Shizuku 桥接",
        requiresRoot = false,
        requiresShizuku = true,
    )

    /** 直接走 `su -c ...` + /proc/net/unix 找 jdwp socket,需要 root */
    object Root : ConnectionType(
        id = "root",
        displayName = "Root 直连 JDWP",
        requiresRoot = true,
        requiresShizuku = false,
    )

    /**
     * 内网虚拟机 SOCKS5 代理方案。
     * 适用于光速虚拟机 / vmos / 虚拟大师 等自带 SOCKS5 出口的虚拟机:
     * 流量全部经 SOCKS5 走,再在本机用 SOCKS 客户端把 jdwp socket 接出来。
     */
    object InnetVmSocks : ConnectionType(
        id = "innet_vm_socks",
        displayName = "内网虚拟机 (SOCKS5 代理)",
        requiresRoot = false,
        requiresShizuku = false,
    )

    /**
     * 内网虚拟机 ADB 网络端口转发方案。
     * VM 端开 adbd 端口 (例如 5555),本机 adb connect 后 adb forward。
     * 跟 UsbLan 思路一致,差别在于目标在虚拟机内,不在物理设备/真机。
     */
    object InnetVmAdb : ConnectionType(
        id = "innet_vm_adb",
        displayName = "内网虚拟机 (ADB 端口转发)",
        requiresRoot = false,
        requiresShizuku = false,
    )

    /** USB / 局域网 ADB,物理设备或局域网内的真机走 `adb connect ip:port` */
    object UsbLan : ConnectionType(
        id = "usb_lan",
        displayName = "USB / 局域网 ADB",
        requiresRoot = false,
        requiresShizuku = false,
    )

    companion object {
        val ALL: List<ConnectionType> =
            listOf(AidlSocket, Shizuku, Root, InnetVmSocks, InnetVmAdb, UsbLan)

        fun fromId(id: String?): ConnectionType =
            ALL.firstOrNull { it.id == id } ?: AidlSocket

        fun isValidId(id: String?): Boolean = ALL.any { it.id == id }

        /**
         * 兼容旧 id "innet_vm"：如果用户的偏好是旧值,映射到新的 SOCKS5 方案
         * (默认走 SOCKS5 因为更通用,ADB 转发需要 VM 端开 adbd 端口)。
         */
        fun fromIdCompat(id: String?): ConnectionType = when (id) {
            "innet_vm" -> InnetVmSocks
            else -> fromId(id)
        }
    }
}
