package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * OTG 等待 USB 设备插入弹窗。落实 spec §4.4。
 *
 * 显示一个旋转加载圈 + 当前状态提示文字。状态由调用方传入（来自 [OtgConnection.state]）。
 *
 * @param message 当前 OTG 状态描述
 * @param onDismiss 关闭弹窗（同时会取消扫描）
 */
@Composable
fun OtgWaitingSheet(
    message: String,
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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "等待 OTG 设备",
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = c.textSecondary)
                    }
                }
                Icon(
                    Icons.Default.Usb,
                    contentDescription = null,
                    tint = c.channelOtg,
                    modifier = Modifier.size(48.dp),
                )
                CircularProgressIndicator(color = c.channelOtg, strokeWidth = 3.dp)
                Text(
                    message,
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "请插入 USB 设备，连接成功后此弹窗会自动关闭",
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}