package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.ui.theme.deviceconnection.DcModalBottomSheet
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 无线调试开启指引弹窗。落实 spec §8.4。
 *
 * 图文步骤（当前为纯文本步骤，可后续扩展）。
 * 使用 [DcModalBottomSheet] 承载，毛玻璃背景。
 */
@Composable
fun GuideSheet(onDismiss: () -> Unit) {
    val c = deviceConnectionColors
    DcModalBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "无线调试开启指引",
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = c.textSecondary)
                }
            }
            val steps = listOf(
                "1. 设置 → 系统 → 开发者选项",
                "2. 打开「无线调试」开关（Android 11+）",
                "3. 点击「无线调试」进入详情，记录 IP 与端口",
                "4. 点击「使用配对码配对设备」获取 6 位配对码",
                "5. 回到本页选择「配对此设备」或「配对其它设备」",
                "6. 配对成功后点击「启动」建立 ADB 连接",
            )
            steps.forEach { step ->
                Text(
                    step,
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}