/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.data.device.DesktopApp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面 launcher Composable (PR-C).
 *
 * 在 [DeviceProfile.FormFactor.DESKTOP] 形态下, 代替纯 preview 内容显示一个"模拟桌面":
 * - 顶部 status bar (时间 + 系统信息)
 * - 居中 app 网格 (4 列 x 多行)
 * - 底部 dock 栏 (Home 按钮 + 收藏夹)
 *
 * 用户要求 #1.1: "从 AGP 资源拿 app icon + 模拟不可点击系统应用 + 物理键返回桌面 + 后台模拟".
 * - "系统应用" (Settings / Phone / Camera / Messages / Clock) 用 [DesktopApp.isClickable]=false,
 *   [DesktopLauncher] 点击它们时调 [onLaunchApp] 但 ViewModel 端会拒绝 (PR-C 已在 ViewModel 加
 *   `launchDesktopApp` 跳过 isClickable=false 的 app).
 * - "物理键返回桌面" 用 dock 栏的 Home 按钮触发 [onGoHome] — 模拟"按 home 键后台 app".
 * - "后台模拟" 由 [foregroundApp] 决定: 非 null 时桌面显示已锁屏的"应用运行中"状态.
 *
 * 渲染:
 * - 壁纸: 渐变 (蓝紫), 让桌面有视觉识别度
 * - 应用图标: 圆角矩形 + 简单系统图标 (因为桌面预览里没有真实 AGP 资源加载)
 * - 网格: 自适应, 容器宽 / 80dp = 列数
 *
 * @param apps 当前桌面上的应用列表 (系统 + 用户)
 * @param foregroundApp 当前"前台" app. null=显示桌面
 * @param onLaunchApp 点击应用回调
 * @param onGoHome Home 键回调
 * @param modifier 外部 modifier
 */
@Composable
fun DesktopLauncher(
    apps: List<DesktopApp>,
    foregroundApp: DesktopApp?,
    onLaunchApp: (DesktopApp) -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 顶部时钟
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000) // 桌面时钟 30s 刷新
        }
    }
    val timeText = remember(now) {
        SimpleDateFormat("EEE, MMM d  HH:mm", Locale.getDefault()).format(now)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A), // 深蓝
                        Color(0xFF6D28D9), // 紫色
                        Color(0xFFDB2777), // 粉
                    )
                )
            ),
    ) {
        val widthDp = maxWidth
        val heightDp = maxHeight

        Column(modifier = Modifier.fillMaxSize()) {
            // === 1) 顶部 status bar ===
            DesktopStatusBar(time = timeText, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(24.dp))

            // === 2) 已启动 app 提示条 (foregroundApp != null 时显示) ===
            if (foregroundApp != null) {
                ForegroundAppBanner(
                    app = foregroundApp,
                    onGoHome = onGoHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                // 桌面提示文字
                Text(
                    text = "Compose Preview — Desktop Launcher",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    textAlign = TextAlign.Start,
                )
            }

            Spacer(Modifier.height(16.dp))

            // === 3) App 网格 (foregroundApp == null 时显示) ===
            if (foregroundApp == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    DesktopAppGrid(
                        apps = apps,
                        onLaunch = onLaunchApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            } else {
                // 后台模拟: 桌面只显示 wallpaper + banner + dock
                Spacer(modifier = Modifier.weight(1f))
            }

            // === 4) 底部 dock 栏 ===
            DesktopDock(
                onHomeClick = onGoHome,
                onAppClick = onLaunchApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }
}

/**
 * 桌面顶部状态栏 — 时间 + 系统信息.
 */
@Composable
private fun DesktopStatusBar(
    time: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color(0x40000000))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = time,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "●",
                color = Color(0xFF22C55E),
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Connected",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * 桌面 App 网格 (4 列).
 */
@Composable
private fun DesktopAppGrid(
    apps: List<DesktopApp>,
    onLaunch: (DesktopApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 自适应列数: 容器宽 / 80dp = 列数
    BoxWithConstraints(modifier = modifier) {
        val columnWidth = 80.dp
        val columns = (maxWidth / columnWidth).toInt().coerceIn(2, 8)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(apps, key = { it.id }) { app ->
                DesktopAppTile(
                    app = app,
                    onClick = { onLaunch(app) },
                )
            }
        }
    }
}

/**
 * 单个 App 图块 (图标 + 名称).
 */
@Composable
private fun DesktopAppTile(
    app: DesktopApp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (app.isClickable) Color(0xFFFFFFFF).copy(alpha = 0.18f)
                    else Color(0xFF000000).copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconForApp(app),
                contentDescription = app.label,
                tint = if (app.isClickable) Color.White else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = if (app.isClickable) Color.White else Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 桌面 dock 栏 — Home 键 + 收藏夹.
 *
 * Home 键调用 [onHomeClick], 模拟"物理键返回桌面".
 */
@Composable
private fun DesktopDock(
    onHomeClick: () -> Unit,
    onAppClick: (DesktopApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0x40000000))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Home 按钮 (圆形, 模拟"物理键")
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB))
                    .clickable(onClick = onHomeClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(4.dp))

            // 收藏夹: 展示 DesktopApp.DEFAULT_SYSTEM_APPS 中的前 4 个 + 一个 "apps" 占位
            val favorites = remember {
                listOf(
                    DesktopApp.SETTINGS,
                    DesktopApp.PHONE,
                    DesktopApp.CAMERA,
                    DesktopApp.MESSAGES,
                )
            }
            favorites.forEach { app ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { onAppClick(app) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconForApp(app),
                        contentDescription = app.label,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * 前台 app 提示条 (用户启动了 app 后, 桌面显示 "app running in background").
 */
@Composable
private fun ForegroundAppBanner(
    app: DesktopApp,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x80000000))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFFFFF).copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconForApp(app),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Running · click Home to return",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
        // 一个小的 "Home" 文字按钮, 提示用户怎么退出
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2563EB))
                .clickable(onClick = onGoHome)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Home",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * 桌面 app → Material icon 映射.
 *
 * 因为 [DesktopLauncher] 是个**纯 Compose 模拟**, 没有真实 AGP 资源, 我们用 Material
 * icon 凑数. 真实工程里可以扩展 [DesktopApp] 加 [iconResId] 用 [androidx.compose.ui.res.painterResource].
 */
private fun iconForApp(app: DesktopApp): androidx.compose.ui.graphics.vector.ImageVector = when (app.id) {
    "system.settings" -> Icons.Filled.Settings
    "system.phone" -> Icons.Filled.Phone
    "system.camera" -> Icons.Filled.PhotoCamera
    "system.messages" -> Icons.Filled.Email
    "system.clock" -> Icons.Filled.AccessTime
    else -> Icons.Filled.Apps
}
