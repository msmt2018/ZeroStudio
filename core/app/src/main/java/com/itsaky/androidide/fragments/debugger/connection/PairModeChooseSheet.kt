package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.RemoteConfig
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.ui.theme.deviceconnection.DcDivider
import com.itsaky.androidide.ui.theme.deviceconnection.DcModalBottomSheet
import com.itsaky.androidide.ui.theme.deviceconnection.DcOptionRow
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 配对模式选择弹窗。落实 spec §8.4。
 *
 * 两个入口：配对此设备 / 配对其它设备。
 * 使用 [DcModalBottomSheet] 承载，毛玻璃背景。
 */
@Composable
fun PairModeChooseSheet(
    onDismiss: () -> Unit,
    onPairOwn: () -> Unit,
    onPairOther: () -> Unit,
) {
    val c = deviceConnectionColors
    DcModalBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "配对设备",
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            DcDivider()
            DcOptionRow(
                icon = Icons.Default.PhonelinkRing,
                title = "配对此设备",
                subtitle = "本机 Android 11+ 无线调试自配对",
                onClick = {
                    onDismiss()
                    onPairOwn()
                },
            )
            DcOptionRow(
                icon = Icons.Default.RemoteConfig,
                title = "配对其它设备",
                subtitle = "手动输入 host:port:code",
                onClick = {
                    onDismiss()
                    onPairOther()
                },
            )
        }
    }
}