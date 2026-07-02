/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ConnectionCapability: 实现自报家门,告诉 UI 自己能做什么。
 *  UI 用来决定显示哪些高级开关。
 */

package com.itsaky.androidide.debugger.connection

sealed class ConnectionCapability {

    /** 能往宿主进程注入 stub (Shizuku C 路径、Root 方案) */
    object CanInstallInHost : ConnectionCapability()

    /** 能读 /proc/net/unix 找 jdwp socket (Root、Shizuku B 路径) */
    object CanReadProcNet : ConnectionCapability()

    /** 能开 SOCKS5 出口 (Shizuku D 路径、InnetVmSocks) */
    object CanExposeSocks : ConnectionCapability()

    /** 依赖宿主应用前台运行 (AIDL Socket 等) */
    object NeedsHostForeground : ConnectionCapability()
}
