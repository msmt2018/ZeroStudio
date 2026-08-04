package com.itsaky.androidide.fragments.debugger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.itsaky.androidide.fragments.debugger.connection.DeviceConnectionSheetContent
import com.itsaky.androidide.fragments.debugger.console.AdbConsoleFragment
import com.itsaky.androidide.fragments.sheets.BaseBottomSheetFragment
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionTheme

/**
 * 设备连接管理 BottomSheet。
 *
 * JDWP-only 的历史实现已移除。当前为「设备连接页」入口：
 * - 状态通道总览条（Shizuku / Root / OTG / WiFi ADB 红黄绿点 + 刷新）
 * - 无线 ADB 卡片（指南 / 配对设备 / 启动）
 * - Root 权限卡片（申请权限 / 管理器选择 / ADB 设备）
 * - OTG 卡片（等待设备 / 管理设备）
 * - ADB 命令执行入口
 *
 * 调用方保持不变：
 * - [com.itsaky.androidide.actions.etc.OpenDeviceConnectionAction]
 * - [com.itsaky.androidide.preferences.debuggerPrefExts]
 */
class DeviceConnectionBottomSheet : BaseBottomSheetFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            DeviceConnectionTheme {
                DeviceConnectionSheetContent(
                    onDismiss = { dismiss() },
                    onNavigateToPairOwn = {
                        dismiss()
                        PairingOwnDeviceFragment().show(parentFragmentManager, "pairing_own")
                    },
                    onNavigateToPairOther = {
                        dismiss()
                        PairingOtherDeviceFragment().show(parentFragmentManager, "pairing_other")
                    },
                    onNavigateToAdbConsole = {
                        dismiss()
                        AdbConsoleFragment().show(parentFragmentManager, "adb_console")
                    },
                )
            }
        }
    }
}