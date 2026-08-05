package com.itsaky.androidide.fragments.debugger.console

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import com.itsaky.androidide.fragments.debugger.DeviceConnectionBottomSheet
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionTheme

/**
 * adb 命令执行页（全屏）。
 *
 * 由 [DeviceConnectionBottomSheet] 的「ADB 命令执行」入口弹出。
 * 内部承载 [AdbConsoleScreen]，使用 [DeviceConnectionTheme] 主题。
 *
 * 复用 connection 模块的 ShellCommandExecutor / CommandDatabase 等连接层，
 * UI 全新设计，不引入 connection 的 BaseShellScreen / ShellViewModel。
 */
class AdbConsoleFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            DeviceConnectionTheme {
                AdbConsoleScreen(
                    onBack = { dismiss() },
                    onNavigateToConnection = { dismiss() },
                )
            }
        }
    }
}