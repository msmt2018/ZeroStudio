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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * - DESKTOP / NONE: 无外壳
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
    content: @Composable () -> Unit,
) {
    when (profile.formFactor) {
        DeviceProfile.FormFactor.WATCH -> WatchFrame(
            profile = profile,
            modifier = modifier,
            systemBarsTheme = systemBarsTheme,
            content = content,
        )
        DeviceProfile.FormFactor.DESKTOP,
        DeviceProfile.FormFactor.NONE -> Box(
            modifier = modifier
                .clip(RoundedCornerShape(profile.cornerRadiusDp.dp))
        ) {
            content()
        }
        else -> PhoneOrFoldableFrame(
            profile = profile,
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
    val bezelTopPx = with(density) { profile.bezels.topDp.dp.toPx() }
    val bezelBottomPx = with(density) { profile.bezels.bottomDp.dp.toPx() }
    val bezelLeftPx = with(density) { profile.bezels.leftDp.dp.toPx() }
    val bezelRightPx = with(density) { profile.bezels.rightDp.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val parentWidth = maxWidth
        val parentHeight = maxHeight

        // 计算屏幕尺寸 (按设备纵横比 + 父容器约束)
        val screenWidth = parentWidth
        val aspect = profile.aspectRatio
        val screenHeight = screenWidth / aspect

        Box(
            modifier = Modifier
                .width(screenWidth)
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

            // 2) 屏幕 (圆角矩形)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = with(density) { profile.bezels.topDp.dp },
                        bottom = with(density) { profile.bezels.bottomDp.dp },
                        start = with(density) { profile.bezels.leftDp.dp },
                        end = with(density) { profile.bezels.rightDp.dp },
                    )
                    .clip(RoundedCornerShape(profile.cornerRadiusDp.dp))
            ) {
                // 3) 内容
                content()

                // 4) 切口叠加 (在内容之上)
                if (showCutout && profile.cutout != null) {
                    CutoutOverlay(
                        cutout = profile.cutout,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // 5) 折叠铰链 (仅内屏)
                if (profile.formFactor == DeviceProfile.FormFactor.FOLDABLE_INNER) {
                    FoldableHingeOverlay(
                        modifier = Modifier.fillMaxSize(),
                        horizontal = true,
                    )
                }

                // 6) 状态栏 + 导航栏
                SystemBarsOverlay(
                    profile = profile,
                    systemBarsTheme = systemBarsTheme,
                    showStatusBar = showStatusBar,
                    showNavigationBar = showNavigationBar,
                    useGestureNav = useGestureNav,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 7) 物理按键 (屏幕外侧)
            if (showPhysicalKeys) {
                profile.physicalKeys.forEach { key ->
                    PhysicalKeyIndicator(
                        key = key,
                        screenHeightDp = with(density) { (parentHeight.value).dp },
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
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val size = if (maxWidth < maxHeight) maxWidth else maxHeight
        Box(
            modifier = Modifier
                .size(size)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(profile.chassisColor)
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.94f)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black)
            ) {
                content()
            }
        }
    }
}

/**
 * 物理按键指示器 (在屏幕外侧画一个矩形 + 文字).
 */
@Composable
private fun PhysicalKeyIndicator(
    key: PhysicalKey,
    screenHeightDp: androidx.compose.ui.unit.Dp,
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
    maxHeight: androidx.compose.ui.unit.Dp = 80.dp,
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
                                .background(cutoutColor, androidx.compose.foundation.shape.CircleShape)
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
