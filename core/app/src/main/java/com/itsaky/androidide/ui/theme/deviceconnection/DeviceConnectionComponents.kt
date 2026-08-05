package com.itsaky.androidide.ui.theme.deviceconnection

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 通道标识。用于卡片色条 / 状态聚合。 */
enum class DcChannel(val color: Color, val displayName: String) {
    SHIZUKU(DeviceConnectionDarkColors.channelShizuku, "Shizuku"),
    ROOT(DeviceConnectionDarkColors.channelRoot, "Root"),
    ROOT_ADB(DeviceConnectionDarkColors.channelRoot, "Root ADB"),
    OTG(DeviceConnectionDarkColors.channelOtg, "OTG"),
    WIFI_ADB(DeviceConnectionDarkColors.channelWifiAdb, "无线 ADB"),
}

/** 状态点颜色层级。 */
enum class DcStatusLevel(val color: Color) {
    GREEN(DeviceConnectionDarkColors.statusGreen),
    YELLOW(DeviceConnectionDarkColors.statusYellow),
    RED(DeviceConnectionDarkColors.statusRed),
}

/**
 * 设备连接页卡片。
 *
 * - 圆角 20dp + 1dp 描边
 * - 左侧 4dp 通道色条
 * - 表层 [DeviceConnectionColors.surfacePanel]
 */
@Composable
fun DcCard(
    channel: DcChannel,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = deviceConnectionColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (highlight) c.surfaceHighlight else c.surfacePanel,
        border = BorderStroke(1.dp, c.border),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .background(channel.color)
                    .fillMaxWidth(),
            )
            Box(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

/** 主操作按钮：蓝→紫渐变填色 + 圆角 14dp。 */
@Composable
fun DcPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val c = deviceConnectionColors
    val brush = Brush.horizontalGradient(listOf(c.primaryGradientStart, c.primaryGradientEnd))
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) Color.Transparent else c.surfaceHighlight,
        enabled = enabled,
        onClick = onClick,
    ) {
        if (enabled) {
            Box(modifier = Modifier.background(brush))
        }
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(16.dp), tint = c.textPrimary)
            }
            Text(text, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 次操作按钮：描边 + 半透明背景。 */
@Composable
fun DcSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val c = deviceConnectionColors
    OutlinedButton(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = c.surfaceHighlight.copy(alpha = 0.4f),
            contentColor = c.textPrimary,
        ),
        border = BorderStroke(1.dp, if (enabled) c.border else c.border.copy(alpha = 0.4f)),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Medium)
    }
}

/**
 * 状态点。YELLOW 时脉冲呼吸 + 外圈光晕。
 *
 * @param level 颜色层级
 * @param sizeDp 圆点直径，默认 12dp
 */
@Composable
fun DcStatusDot(
    level: DcStatusLevel,
    modifier: Modifier = Modifier,
    sizeDp: Int = 12,
) {
    val pulse = if (level == DcStatusLevel.YELLOW) {
        val transition = rememberInfiniteTransition(label = "dc_status_pulse")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                tween(900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dc_status_alpha",
        ).value
    } else {
        1f
    }

    Box(
        modifier = modifier.size((sizeDp + 6).dp),
        contentAlignment = Alignment.Center,
    ) {
        // 外圈光晕
        Box(
            modifier = Modifier
                .size((sizeDp + 6).dp)
                .alpha(pulse * 0.4f)
                .background(level.color.copy(alpha = 0.5f), CircleShape),
        )
        // 主圆点
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .background(level.color, CircleShape),
        )
    }
}

/**
 * 通用选项行（用于 OptionSheet 模板的每一项）。
 */
@Composable
fun DcOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = deviceConnectionColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(c.surfaceHighlight, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = c.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(subtitle, color = c.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 简易分隔线。 */
@Composable
fun DcDivider(modifier: Modifier = Modifier) {
    val c = deviceConnectionColors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.border.copy(alpha = 0.5f)),
    )
}

// ── ModalBottomSheet 包装器 ─────────────────────────

/**
 * 设备连接页统一的 ModalBottomSheet 包装器。落实 spec §8.4。
 *
 * 视觉：顶部 28dp 圆角 + 主题色纵向渐变 + 顶部高光（模拟毛玻璃受光）。
 * scrim 用主题深色，保证层次感。
 *
 * 用法：
 * ```
 * DcModalBottomSheet(onDismiss = { ... }) {
 *     // 内容
 * }
 * ```
 *
 * @param onDismiss 关闭回调（点击 scrim / 滑动隐藏 / 按返回键均触发）
 * @param content Sheet 内容
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DcModalBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = deviceConnectionColors
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = c.background.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(c.surfacePanel, c.background),
                    ),
                )
                .drawWithContent {
                    drawContent()
                    // 顶部高光，模拟毛玻璃受光
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = 24f,
                        ),
                    )
                },
        ) {
            content()
        }
    }
}