/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  DebugConnectionRegistry: 5 种 IDebugConnection 实现的工厂。
 *  按 DebugConnectionPreferences.activeType 选择具体实现。
 *
 *  当前 sub-project 1 阶段: 5 个实现都是 stub (resolve/connect/attach
 *  抛 UnsupportedOperationException)。后续子项目 2~5 补齐。
 */

package com.itsaky.androidide.debugger.connection

import com.itsaky.androidide.debugger.connection.impl.InnetVmAdbConnection
import com.itsaky.androidide.debugger.connection.impl.InnetVmSocksConnection
import com.itsaky.androidide.debugger.connection.impl.RootConnection
import com.itsaky.androidide.debugger.connection.impl.ShizukuConnection
import com.itsaky.androidide.debugger.connection.impl.UsbLanConnection

object DebugConnectionRegistry {

    /**
     * 工厂方法: 根据 type 创建对应 IDebugConnection 实例。
     * 每次调用都返回新实例 (state=Idle),生命周期由调用方管。
     */
    fun create(
        type: ConnectionType,
        target: DebugTarget,
        settings: DebugConnectionSettings,
    ): IDebugConnection = when (type) {
        ConnectionType.AidlSocket -> UsbLanConnection(target, settings)
        ConnectionType.Shizuku -> ShizukuConnection(target, settings)
        ConnectionType.Root -> RootConnection(target, settings)
        ConnectionType.InnetVmSocks -> InnetVmSocksConnection(target, settings)
        ConnectionType.InnetVmAdb -> InnetVmAdbConnection(target, settings)
        ConnectionType.UsbLan -> UsbLanConnection(target, settings)
    }

    /**
     * 便捷方法: 用 settings.activeType 决定类型。
     */
    fun createForActive(
        target: DebugTarget,
        settings: DebugConnectionSettings,
    ): IDebugConnection = create(settings.activeType, target, settings)
}
