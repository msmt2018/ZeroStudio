/*
 * ZeroStudio IDE - 设备连接管理 BottomSheet
 *
 * DeviceConnectionBottomSheet: 以 Shizuku 和 Root 两种 ADB 连接方式为核心的
 * 设备连接管理底部弹窗。
 *
 * 功能参考 debugger/android-adb-shell 的 LocalAdbScreen + ShizukuPermissionHandler
 * + ShellRepositoryImpl,但 UI 界面和源码完全自主编写,采用与参考代码不同的视觉设计:
 *   - 卡片式布局,每张卡片代表一个连接通道
 *   - 状态指示灯 (彩色圆点) 直观展示通道状态
 *   - 当前活跃通道用边框高亮标记
 *   - 操作按钮内联在卡片底部
 *
 * 复用项目已有的 ConnectionType / DebugConnectionPreferences / DeviceConnectionManager。
 */

package com.itsaky.androidide.fragments.debugger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DeviceConnectionManager
import com.itsaky.androidide.debugger.connection.RootProbeState
import com.itsaky.androidide.debugger.connection.ShizukuServiceState
import com.itsaky.androidide.fragments.sheets.BaseBottomSheetFragment
import kotlinx.coroutines.launch

/**
 * 设备连接管理底部弹窗。
 *
 * 使用方式:
 *   DeviceConnectionBottomSheet().show(supportFragmentManager, "device_connection")
 */
class DeviceConnectionBottomSheet : BaseBottomSheetFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    DeviceConnectionScreen(onDismiss = { dismiss() })
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 进入时立即刷新 Shizuku 状态
        DeviceConnectionManager.refreshShizukuState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceConnectionScreen(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val shizukuState by DeviceConnectionManager.shizukuState.collectAsState()
    val rootState by DeviceConnectionManager.rootState.collectAsState()
    var activeType by remember { mutableStateOf(DeviceConnectionManager.activeType) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 标题栏 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "设备连接管理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Shizuku / Root ADB 连接通道",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- 当前活跃连接 ----
            ActiveConnectionCard(activeType = activeType)

            // ---- Shizuku 通道卡片 ----
            ConnectionChannelCard(
                title = "Shizuku 桥接",
                subtitle = "通过 Shizuku 服务以 ADB/Shell 权限执行",
                statusColor = shizukuStatusColor(shizukuState),
                statusText = shizukuStatusText(shizukuState),
                detailText = shizukuDetailText(shizukuState),
                isActive = activeType == ConnectionType.Shizuku,
                available = shizukuState == ShizukuServiceState.RunningAuthorized,
                actions = {
                    OutlinedButton(
                        onClick = { DeviceConnectionManager.refreshShizukuState() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("刷新")
                    }
                    if (shizukuState == ShizukuServiceState.RunningUnauthorized) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { DeviceConnectionManager.requestShizukuPermission() }) {
                            Text("请求授权")
                        }
                    }
                    if (shizukuState == ShizukuServiceState.RunningAuthorized) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                DeviceConnectionManager.activeType = ConnectionType.Shizuku
                                activeType = ConnectionType.Shizuku
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("设为活跃")
                        }
                    }
                },
            )

            // ---- Root 通道卡片 ----
            ConnectionChannelCard(
                title = "Root 直连",
                subtitle = "通过 su 以 Superuser 权限直接执行",
                statusColor = rootStatusColor(rootState),
                statusText = rootStatusText(rootState),
                detailText = rootDetailText(rootState),
                isActive = activeType == ConnectionType.Root,
                available = rootState == RootProbeState.Available,
                actions = {
                    OutlinedButton(
                        onClick = {
                            scope.launch { DeviceConnectionManager.probeRoot() }
                        },
                        enabled = rootState != RootProbeState.Probing,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (rootState == RootProbeState.Probing) "检测中..." else "检测")
                    }
                    if (rootState == RootProbeState.Available) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                DeviceConnectionManager.activeType = ConnectionType.Root
                                activeType = ConnectionType.Root
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("设为活跃")
                        }
                    }
                },
            )

            // ---- 底部提示 ----
            Text(
                text = "提示: Shizuku 需要 Shizuku app 已安装并启动服务;Root 需要设备已 root 且授权 su。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 当前活跃连接卡片。
 */
@Composable
private fun ActiveConnectionCard(activeType: ConnectionType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "当前活跃连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = activeType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * 通用连接通道卡片。
 */
@Composable
private fun ConnectionChannelCard(
    title: String,
    subtitle: String,
    statusColor: Color,
    statusText: String,
    detailText: String,
    isActive: Boolean,
    available: Boolean,
    actions: @Composable () -> Unit,
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 状态指示灯 + 文字
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color = statusColor, shape = CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                    )
                }
            }

            // 详情文字
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

// ---- Shizuku 状态映射 ----

private fun shizukuStatusColor(state: ShizukuServiceState): Color = when (state) {
    ShizukuServiceState.NotRunning -> Color(0xFF9E9E9E) // 灰色
    ShizukuServiceState.RunningUnauthorized -> Color(0xFFFFA726) // 橙色
    ShizukuServiceState.RunningAuthorized -> Color(0xFF66BB6A) // 绿色
}

private fun shizukuStatusText(state: ShizukuServiceState): String = when (state) {
    ShizukuServiceState.NotRunning -> "未运行"
    ShizukuServiceState.RunningUnauthorized -> "未授权"
    ShizukuServiceState.RunningAuthorized -> "就绪"
}

private fun shizukuDetailText(state: ShizukuServiceState): String = when (state) {
    ShizukuServiceState.NotRunning -> "Shizuku 服务未启动,请先打开 Shizuku app 并启动服务"
    ShizukuServiceState.RunningUnauthorized -> "Shizuku 服务已运行,但本应用尚未获得授权"
    ShizukuServiceState.RunningAuthorized -> "Shizuku 服务已运行且已授权,可以使用"
}

// ---- Root 状态映射 ----

private fun rootStatusColor(state: RootProbeState): Color = when (state) {
    RootProbeState.NotProbed -> Color(0xFF9E9E9E) // 灰色
    RootProbeState.Probing -> Color(0xFFFFA726) // 橙色
    RootProbeState.Available -> Color(0xFF66BB6A) // 绿色
    RootProbeState.Unavailable -> Color(0xFFEF5350) // 红色
}

private fun rootStatusText(state: RootProbeState): String = when (state) {
    RootProbeState.NotProbed -> "未检测"
    RootProbeState.Probing -> "检测中"
    RootProbeState.Available -> "就绪"
    RootProbeState.Unavailable -> "不可用"
}

private fun rootDetailText(state: RootProbeState): String = when (state) {
    RootProbeState.NotProbed -> "尚未检测 Root 状态,点击「检测」按钮"
    RootProbeState.Probing -> "正在尝试执行 su..."
    RootProbeState.Available -> "设备已 Root,su 可用"
    RootProbeState.Unavailable -> "设备未 Root 或 su 被拒绝"
}
