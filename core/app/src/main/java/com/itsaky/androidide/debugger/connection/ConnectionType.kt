/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ConnectionType: 5 种连接方式的密封枚举 + 元数据。
 *  默认 AIDL_SOCKET,运行时由用户在设置页手动切换。
 *  5 种实现完全独立,运行时只存在一种,无跨方案降级。
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

    /** 内网虚拟机(光速虚拟机 / vmos / 虚拟大师),SOCKS5 代理 + 端口转发 */
    object InnetVm : ConnectionType(
        id = "innet_vm",
        displayName = "内网虚拟机",
        requiresRoot = false,
        requiresShizuku = false,
    )

    /** USB / 局域网 ADB, `adb connect ip:port` 后用 adb forward */
    object UsbLan : ConnectionType(
        id = "usb_lan",
        displayName = "USB / 局域网 ADB",
        requiresRoot = false,
        requiresShizuku = false,
    )

    companion object {
        val ALL: List<ConnectionType> = listOf(AidlSocket, Shizuku, Root, InnetVm, UsbLan)

        fun fromId(id: String?): ConnectionType =
            ALL.firstOrNull { it.id == id } ?: AidlSocket

        fun isValidId(id: String?): Boolean = ALL.any { it.id == id }
    }
}
