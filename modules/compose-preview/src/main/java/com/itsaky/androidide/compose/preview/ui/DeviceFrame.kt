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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.data.device.CutoutGeometry
import com.itsaky.androidide.compose.preview.data.device.PhysicalKey
import kotlin.math.roundToInt

/**
 * 设备外壳 + 屏幕容器 v2.1.
 *
 * 把任意 Composable (通常是预览目标) 套在真实设备外壳中:
 *
 * ```
 * DeviceFrame(profile = Pixel_7) {
 *   RenderTarget()  // 实际预览内容
 * }
 * ```
 *
 * 渲染层次 (外到内):
 * 1. 机身 (chassis) - [profile.chassisColor] + 圆角
 * 2. 上/下/左/右 边框 ([profile.bezels])
 * 3. 物理按键 ([profile.physicalKeys]) - 屏幕外侧矩形
 * 4. 屏幕矩形 - [profile.cornerRadiusDp] 圆角
 * 5. 内容 ([content])
 * 6. 切口叠加 ([profile.cutout]) - 刘海 / 针孔 / 瀑布
 * 7. 折叠铰链 (FOLDABLE_INNER 形态)
 * 8. 状态栏 + 导航栏 ([SystemBarsOverlay])
 *
 * 形态因子 (formFactor) 决定渲染策略:
 * - PHONE: 标准圆角矩形 + 切口
 * - FOLDABLE_INNER: 极窄边框 + 中央铰链
 * - FOLDABLE_OUTER: 类似 PHONE + 切口
 * - TABLET: 较厚边框
 * - WATCH: 圆形 (radius = width/2)
 * - DESKTOP (PR-C): 桌面 launcher + 物理 home 键. 屏幕内容 (preview) 作为
 *                   "前台 app" 显示, 桌面在背后, 按 home 键返回桌面.
 * - NONE: 无外壳
 *
 * @param profile 设备 profile
 * @param modifier 外部 modifier
 * @param systemBarsTheme 系统栏主题
 * @param showStatusBar / showNavigationBar / showCutout / showChassis 显示开关
 * @param useGestureNav 手势导航
 * @param content 实际预览内容
 */
@Composable
fun DeviceFrame(
    profile: DeviceProfile,
    modifier: Modifier = Modifier,
    systemBarsTheme: SystemBarsTheme = SystemBarsTheme.AUTO,
    showStatusBar: Boolean = true,
    showNavigationBar: Boolean = true,
    showCutout: Boolean = true,
    showChassis: Boolean = true,
    showPhysicalKeys: Boolean = true,
    useGestureNav: Boolean = false,
    // === PR-C 桌面 launcher 集成参数 ===
    desktopApps: List<com.itsaky.androidide.compose.preview.data.device.DesktopApp> = emptyList(),
    foregroundApp: com.itsaky.androidide.compose.preview.data.device.DesktopApp? = null,
    modulePath: String? = null,
    onLaunchApp: (com.itsaky.androidide.compose.preview.data.device.DesktopApp) -> Unit = {},
    onGoHome: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // 【v3.5】从 profile.orientation 派生一个 effective profile, 让
    // 后续渲染逻辑 (cutout / bezels / 物理键 / 铰链) 一律用
    // [DeviceProfile.effectiveCutout] / [effectiveBezels] / [effectivePhysicalKeys] / [effectiveAspectRatio].
    // 这样同一个 profile 在横屏 / 竖屏下自然得到正确的几何, 无需在
    // 调用方手动 swap.
    val effectiveProfile = profile.copy(
        orientation = profile.orientation,
    )
    when (effectiveProfile.formFactor) {
        DeviceProfile.FormFactor.WATCH -> WatchFrame(
            profile = effectiveProfile,
            modifier = modifier,
            systemBarsTheme = systemBarsTheme,
            content = content,
        )
        DeviceProfile.FormFactor.DESKTOP -> DesktopFrame(
            profile = effectiveProfile,
            modifier = modifier,
            desktopApps = desktopApps,
            foregroundApp = foregroundApp,
            modulePath = modulePath,
            onLaunchApp = onLaunchApp,
            onGoHome = onGoHome,
            content = content,
        )
        DeviceProfile.FormFactor.NONE -> {
            // 【v3.2 修复 #1】NONE 必须 fillMaxSize, 否则 Box 没有
            // 尺寸约束, Compose 跳过绘制 (用户看到全透明 + 紫底透出).
            // 之前版本只 .clip(RoundedCornerShape(...)) 没用 fillMaxSize, 在
            // ComposePreviewScreen 内会被父容器测量为 0.
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(effectiveProfile.cornerRadiusDp.dp))
                    .background(Color.Transparent)
            ) {
                content()
            }
        }
        else -> PhoneOrFoldableFrame(
            profile = effectiveProfile,
            modifier = modifier,
            systemBarsTheme = systemBarsTheme,
            showStatusBar = showStatusBar,
            showNavigationBar = showNavigationBar,
            showCutout = showCutout,
            showChassis = showChassis,
            showPhysicalKeys = showPhysicalKeys,
            useGestureNav = useGestureNav,
            content = content,
        )
    }
}

/**
 * 桌面形态设备外壳 (PR-C).
 *
 * 渲染策略:
 * - **桌面** ([foregroundApp] == null): 屏幕显示 [DesktopLauncher] (壁纸 + 应用网格 + Dock 栏).
 * - **前台 app** ([foregroundApp] != null): 屏幕显示 [content] (preview), 桌面状态栏仍显示
 *   "app running in background" 提示, 用户可点 Home 键返回桌面.
 *
 * 这样 PR-C "桌面 launcher 模拟" + "物理键返回桌面" + "后台模拟" 三件事在屏幕内的不同
 * 状态自然过渡, 用户能直接感受到"app 在桌面里被启动 → 按 home 键回到桌面"流程.
 */
@Composable
private fun DesktopFrame(
    profile: DeviceProfile,
    modifier: Modifier,
    desktopApps: List<com.itsaky.androidide.compose.preview.data.device.DesktopApp>,
    foregroundApp: com.itsaky.androidide.compose.preview.data.device.DesktopApp?,
    modulePath: String?,
    onLaunchApp: (com.itsaky.androidide.compose.preview.data.device.DesktopApp) -> Unit,
    onGoHome: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(profile.cornerRadiusDp.dp))
            .background(Color.Black)
    ) {
        if (foregroundApp == null) {
            // === 桌面态: 显示 DesktopLauncher, content() 不渲染 ===
            DesktopLauncher(
                apps = desktopApps,
                foregroundApp = foregroundApp,
                onLaunchApp = onLaunchApp,
                onGoHome = onGoHome,
            )
        } else {
            // === 前台 app 态: 显示 preview 内容, 上层叠 DesktopLauncher dock
            //     + foreground app 提示 (用于按 home 返回) ===
            content()
            DesktopForegroundOverlay(
                foregroundApp = foregroundApp,
                modulePath = modulePath,
                onGoHome = onGoHome,
            )
        }
    }
}

/**
 * 前台 app 状态下的桌面浮层 — 仅显示 dock + foreground app 提示条, 让用户能按
 * "home" 键返回桌面. preview 内容透传显示在底层.
 */
@Composable
private fun DesktopForegroundOverlay(
    foregroundApp: com.itsaky.androidide.compose.preview.data.device.DesktopApp,
    modulePath: String?,
    onGoHome: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部小提示条 — 不抢 preview 视觉
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x80000000))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${foregroundApp.label} · running",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Home",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2563EB))
                    .clickable(onClick = onGoHome)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // 底部 dock — Home 物理键, 模拟"按 home 键返回桌面" (PR-C 用户要求 #1.1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB))
                    .clickable(onClick = onGoHome),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun PhoneOrFoldableFrame(
    profile: DeviceProfile,
    modifier: Modifier,
    systemBarsTheme: SystemBarsTheme,
    showStatusBar: Boolean,
    showNavigationBar: Boolean,
    showCutout: Boolean,
    showChassis: Boolean,
    showPhysicalKeys: Boolean,
    useGestureNav: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // 【v3.5】用 effectiveBezels (按 orientation 旋转后的边框). 横屏时
    // 原 bezel.topDp 变成 effectiveBezels.leftDp, 用于屏幕内 padding.
    val effectiveBezels = profile.effectiveBezels
    val bezelTopPx = with(density) { effectiveBezels.topDp.dp.toPx() }
    val bezelBottomPx = with(density) { effectiveBezels.bottomDp.dp.toPx() }
    val bezelLeftPx = with(density) { effectiveBezels.leftDp.dp.toPx() }
    val bezelRightPx = with(density) { effectiveBezels.rightDp.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val parentWidth = maxWidth
        val parentHeight = maxHeight
        // 【v3.5】用 effectiveAspectRatio. 横屏时宽高比翻转, 屏幕 Box 的
        // width/height 对调.
        val aspect = profile.effectiveAspectRatio

        // 【v3.2 修复 #3】按 maxWidth 和 maxHeight 双向约束, 选 min.
        //
        // 之前版本 val screenHeight = screenWidth / aspect 没用 maxHeight 限制,
        // 当 device 纵横比很窄 (iPhone 15 Pro Max 0.461) 且父容器较扁时, 实际渲染
        // 高度会超出 BoxWithConstraints 默认 clip 范围, 物理键 (y=480) 跟着被裁切
        // 显示不出来. 现在按 maxHeight 双向约束, 物理键按实际渲染高度计算.
        val maxScreenWidth = parentWidth
        val maxScreenHeight = (parentHeight * 0.96f)  // 留 4% 余量防裁切
        val screenWidth = maxScreenWidth
        val screenHeight = (screenWidth / aspect).coerceAtMost(maxScreenHeight)
        val finalScreenWidth = if (screenHeight == maxScreenHeight) {
            screenHeight * aspect
        } else {
            screenWidth
        }

        Box(
            modifier = Modifier
                .width(finalScreenWidth)
                .height(screenHeight)
        ) {
            // 1) 机身外壳
            if (showChassis) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(profile.chassisColor, RoundedCornerShape(profile.cornerRadiusDp.dp + 4.dp))
                )
            }

            // 2) 屏幕 (圆角矩形) — padding 按 effectiveBezels (已旋转)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = with(density) { effectiveBezels.topDp.dp },
                        bottom = with(density) { effectiveBezels.bottomDp.dp },
                        start = with(density) { effectiveBezels.leftDp.dp },
                        end = with(density) { effectiveBezels.rightDp.dp },
                    )
                    .clip(RoundedCornerShape(profile.cornerRadiusDp.dp))
            ) {
                // 3) 内容
                content()

                // 4) 切口叠加 (在内容之上) — 用 effectiveCutout (已旋转锚点)
                if (showCutout && profile.effectiveCutout != null) {
                    CutoutOverlay(
                        cutout = profile.effectiveCutout,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // 5) 折叠铰链 (仅内屏) — 【PR-C】可拖拽改变 foldAngle
                // 【v3.5】横屏时铰链方向翻转: 原 horizontal = true (Galaxy Z Fold)
                // 在 LANDSCAPE 模式下变 horizontal = false (竖向铰链).
                if (profile.formFactor == DeviceProfile.FormFactor.FOLDABLE_INNER) {
                    var foldAngle by remember { mutableStateOf(0f) }
                    FoldableHingeOverlay(
                        foldAngle = foldAngle,
                        onFoldAngleChange = { foldAngle = it },
                        modifier = Modifier.fillMaxSize(),
                        // 横屏时铰链方向对调: 屏幕从竖向 (长边垂直) 变成
                        // 横向 (长边水平), 所以铰链从"水平"变"垂直".
                        horizontal = !profile.orientation.isLandscape,
                    )
                }

                // 6) 状态栏 + 导航栏 — 横屏时 status bar / nav bar 仍在
                // "新屏幕的顶/底" (因为 SystemBarsOverlay 内部已是 Spacer.weight
                // 布局, 不需要再旋转). 数值不变 (statusBar 高度 = 原 statusBar 高度).
                SystemBarsOverlay(
                    profile = profile,
                    systemBarsTheme = systemBarsTheme,
                    showStatusBar = showStatusBar,
                    showNavigationBar = showNavigationBar,
                    useGestureNav = useGestureNav,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 7) 物理按键 (屏幕外侧) — 用 effectivePhysicalKeys (已旋转).
            // 实际渲染高度按"旋转后"的 finalScreenWidth / screenHeight,
            // 因为 effectivePhysicalKeys 内的 positionXdp / positionYdp
            // 是按"旋转后"屏幕坐标算的.
            if (showPhysicalKeys) {
                profile.effectivePhysicalKeys.forEach { key ->
                    PhysicalKeyIndicator(
                        key = key,
                        screenWidthDp = with(density) { finalScreenWidth },
                        screenHeightDp = with(density) { screenHeight },
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchFrame(
    profile: DeviceProfile,
    modifier: Modifier,
    systemBarsTheme: SystemBarsTheme,
    content: @Composable () -> Unit,
) {
    // 【v3.2 修复 #2】撑开 BoxWithConstraints, 否则 maxWidth/maxHeight 都是 0,
    // size = 0, 内层 Box size = 0, content() 渲染不到任何像素 = 全黑.
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val size = if (maxWidth < maxHeight) maxWidth else maxHeight
        // 渲染层次: 表壳 (圆形) → 屏幕 (内嵌圆形) → content → 数字表冠 + 侧键
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(profile.chassisColor)
        ) {
            // 【PR-C 修复】内层屏幕用 matchParentSize(), 让任意 content 都被圆形
            // 裁切 (不论 content 是 LazyColumn / 滚动列表 / 大尺寸 view 都会被
            // CircleShape 切掉, 而不溢出). 之前版本 size(size * 0.94f) 在父容器
            // 用 .clip(CircleShape) 时已经能切, 但当 content 想撑满圆形区域时
            // (例如预览了一个全屏大背景) 会留 6% 黑边. matchParentSize + 0.94f
            // 的 padding 用 inset 模拟, 真实表盘的"屏幕"区域是内嵌的小圆.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(size * 0.03f)
                    .clip(CircleShape)
                    .background(Color.Black)
            ) {
                content()
            }

            // === 数字表冠 (digital crown) — 表盘右侧 ===
            // 真实手表 (Wear OS / Apple Watch) 右侧有一个小圆柱形旋钮, 用矩形 + 渐变模拟.
            WatchCrown(modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

/**
 * 数字表冠 (Wear OS / Apple Watch 右侧的旋钮). PR-C 加, 让手表模拟更真实.
 */
@Composable
private fun WatchCrown(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val crownW = with(density) { 6.dp.toPx() }
    val crownH = with(density) { 32.dp.toPx() }
    val crownColor = Color(0xFF6B6B70)
    val crownRidgeColor = Color(0xFF3A3A3E)

    Canvas(
        modifier = modifier
            .width(8.dp)
            .height(40.dp)
    ) {
        // 外壳
        drawRoundRect(
            color = crownColor,
            topLeft = Offset(
                x = (size.width - crownW) / 2f,
                y = (size.height - crownH) / 2f,
            ),
            size = Size(crownW, crownH),
            cornerRadius = CornerRadius(crownW / 2f, crownW / 2f),
        )
        // 凹槽纹理 (4 道横线模拟螺纹)
        for (i in 0 until 4) {
            val y = (size.height - crownH) / 2f + (i + 1) * (crownH / 5f)
            drawLine(
                color = crownRidgeColor,
                start = Offset(
                    x = (size.width - crownW) / 2f,
                    y = y,
                ),
                end = Offset(
                    x = (size.width + crownW) / 2f,
                    y = y,
                ),
                strokeWidth = 1.2f,
            )
        }
    }
}

/**
 * 物理按键指示器 (在屏幕外侧画一个矩形 + 文字).
 *
 * 【v3.2 修复 #3】坐标基准改成"实际渲染屏幕尺寸" (而非父 BoxWithConstraints),
 * 这样在窄高比 device (iPhone 15 Pro Max 0.461) 实际渲染高度被 maxHeight 限制
 * 时, 物理键按"屏幕实际尺寸"百分比定位, 不会被父容器裁切.
 */
@Composable
private fun PhysicalKeyIndicator(
    key: PhysicalKey,
    screenWidthDp: Dp,
    screenHeightDp: Dp,
) {
    val density = LocalDensity.current
    val xPx = with(density) { key.positionXdp.dp.toPx() }
    val yPx = with(density) { key.positionYdp.dp.toPx() }
    val wPx = with(density) { key.widthDp.dp.toPx() }
    val hPx = with(density) { key.heightDp.dp.toPx() }

    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = Modifier
            .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .background(Color(0xFF1A1A1E), shape)
    ) {
        Text(
            text = key.displayName,
            color = Color(0xFF707075),
            fontSize = 4.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * 设备缩略图 (小尺寸) - 用于 DeviceProfileSheet 列表行.
 *
 * 用极简 Canvas 画一个设备缩略图 (含 cutout 标识),
 * 比例与真实 device 一致.
 */
@Composable
fun DeviceThumbnail(
    profile: DeviceProfile,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 80.dp,
) {
    val density = LocalDensity.current
    val aspect = profile.aspectRatio
    val maxW = maxHeight * aspect
    val cornerPx = with(density) { profile.cornerRadiusDp.dp.toPx() }
    val cutoutColor = Color(0xFF000000)

    BoxWithConstraints(
        modifier = modifier
            .height(maxHeight)
            .width(maxW),
        contentAlignment = Alignment.Center,
    ) {
        val width = maxWidth
        val height = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(profile.chassisColor, RoundedCornerShape(profile.cornerRadiusDp.dp + 2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(profile.cornerRadiusDp.dp))
                    .background(Color(0xFF101015)),
            ) {
                // 屏幕内容用空 Box
                Spacer(modifier = Modifier.fillMaxSize())

                // Cutout 缩略图
                when (val c = profile.cutout) {
                    is CutoutGeometry.Notch -> {
                        val w = with(density) { c.widthDp.dp.toPx() } * 0.05f
                        val h = with(density) { c.heightDp.dp.toPx() } * 0.05f
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        ((width.toPx() - w) / 2f).roundToInt(),
                                        0
                                    )
                                }
                                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                                .background(cutoutColor, RoundedCornerShape(2.dp))
                        )
                    }
                    is CutoutGeometry.DynamicIsland -> {
                        // 灵动岛形状与 Notch 相似, 沿用 Notch 渲染路径, 仅放大圆角
                        val w = with(density) { c.widthDp.dp.toPx() } * 0.05f
                        val h = with(density) { c.heightDp.dp.toPx() } * 0.05f
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        ((width.toPx() - w) / 2f).roundToInt(),
                                        0
                                    )
                                }
                                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                                .background(
                                    cutoutColor,
                                    RoundedCornerShape((c.cornerRadiusDp * 0.05f).dp)
                                )
                        )
                    }
                    is CutoutGeometry.PunchHole -> {
                        val d = with(density) { c.diameterDp.dp.toPx() } * 0.05f
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (width.toPx() / 2f - d / 2f).roundToInt(),
                                        (with(density) { 4.dp.toPx() }).roundToInt()
                                    )
                                }
                                .size(with(density) { d.toDp() })
                                .background(cutoutColor, CircleShape)
                        )
                    }
                    is CutoutGeometry.WaterfallCurve -> {
                        // 瀑布屏只在两侧画一条暗带
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val w = size.width * 0.04f
                                    drawRect(
                                        color = cutoutColor,
                                        topLeft = Offset(0f, 0f),
                                        size = Size(w, size.height),
                                    )
                                    drawRect(
                                        color = cutoutColor,
                                        topLeft = Offset(size.width - w, 0f),
                                        size = Size(w, size.height),
                                    )
                                }
                        )
                    }
                    null -> {}
                }
            }
        }
    }
}
