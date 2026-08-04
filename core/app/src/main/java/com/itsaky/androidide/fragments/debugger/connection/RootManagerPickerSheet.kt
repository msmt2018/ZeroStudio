package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.itsaky.androidide.debugger.root.RootManagerType
import com.itsaky.androidide.ui.theme.deviceconnection.DcDivider
import com.itsaky.androidide.ui.theme.deviceconnection.DcOptionRow
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * Root 管理器选择弹窗。列出 4 种管理器，附带各自可用性。
 *
 * 当前实现仅展示选项；点击后调用 [onPick]，由调用方触发对应授权流程。
 */
@Composable
fun RootManagerPickerSheet(
    availableManagers: Set<RootManagerType>,
    onPick: (RootManagerType) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = deviceConnectionColors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = c.surfacePanel,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Root 管理器",
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                DcDivider()
                RootManagerType.values().forEach { type ->
                    val available = type in availableManagers
                    val subtitle = if (available) "已检测到 · 点击授权" else "未检测到"
                    val icon = when (type) {
                        RootManagerType.STANDARD_SU -> Icons.Default.Terminal
                        RootManagerType.KERNEL_SU -> Icons.Default.Security
                        RootManagerType.MAGISK -> Icons.Default.VerifiedUser
                        RootManagerType.APATCH -> Icons.Default.VpnKey
                    }
                    DcOptionRow(
                        icon = icon,
                        title = type.displayName,
                        subtitle = subtitle,
                        onClick = {
                            onDismiss()
                            onPick(type)
                        },
                    )
                }
            }
        }
    }
}