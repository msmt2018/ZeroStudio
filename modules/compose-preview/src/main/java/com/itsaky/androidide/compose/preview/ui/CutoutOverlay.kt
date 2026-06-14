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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.compose.preview.data.device.CutoutGeometry

/**
 * 屏幕切口叠加层.
 *
 * 在 [DeviceFrame] 的屏幕内容**之上**绘制, 模拟刘海 / 针孔 / 瀑布屏.
 *
 * 渲染方式:
 * - [CutoutGeometry.Notch] → 顶部居中 / 角部 圆角矩形
 * - [CutoutGeometry.PunchHole] → 顶部居中 / 角部 圆形
 * - [CutoutGeometry.WaterfallCurve] → 两侧 Bezier 路径
 *
 * 注意: 切口是**纯视觉**的 (不实际 "挖掉" 屏幕内容), 这是因为
 * 预览中的 Compose UI 是已知 API 28+ 的 WindowInsets 处理, 不应
 * 被额外的物理切口遮挡. 真正应用会按 [CutoutGeometry.Anchor]
 * 配置 WindowInsets.
 *
 * @param cutout 切口几何. 传入 null 时不渲染.
 * @param modifier 外部 modifier (用于定位和尺寸)
 * @param screenSize 屏幕尺寸 (px), 瀑布屏需要它来计算曲线
 * @param color 切口颜色 (默认近黑, 与设备一体感)
 */
@Composable
fun CutoutOverlay(
    cutout: CutoutGeometry?,
    modifier: Modifier = Modifier,
    screenSize: Size = Size.Unspecified,
    color: Color = Color(0xF0000000),
) {
    if (cutout == null) return

    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        when (cutout) {
            is CutoutGeometry.Notch -> drawNotch(cutout, color)
            is CutoutGeometry.PunchHole -> drawPunchHole(cutout, color)
            is CutoutGeometry.WaterfallCurve -> drawWaterfall(cutout, color, screenSize)
        }
    }
}

/**
 * 绘制刘海: 顶部矩形 (圆角).
 */
private fun DrawScope.drawNotch(notch: CutoutGeometry.Notch, color: Color) {
    val widthPx = withHpx(notch.widthDp)
    val heightPx = withHpx(notch.heightDp)
    val cornerRadiusPx = withHpx(notch.cornerRadiusDp)

    val x = when (notch.anchor) {
        CutoutGeometry.Anchor.TOP_CENTER -> (size.width - widthPx) / 2f
        CutoutGeometry.Anchor.TOP_LEFT -> withHpx(notch.cornerRadiusDp) * 2f
        CutoutGeometry.Anchor.TOP_RIGHT -> size.width - widthPx - withHpx(notch.cornerRadiusDp) * 2f
        else -> (size.width - widthPx) / 2f
    }
    val y = 0f

    // 阴影外圈 (深色, 模拟刘海在屏幕上的"立体感")
    val shadowPath = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = x, top = y,
                right = x + widthPx, bottom = y + heightPx,
                radiusX = cornerRadiusPx, radiusY = cornerRadiusPx
            )
        )
    }
    drawPath(path = shadowPath, color = color)

    // 摄像头 / 听筒 - 暗色实心点
    val sensorSize = cornerRadiusPx * 0.7f
    drawCircle(
        color = Color(0xFF0A0A0A),
        radius = sensorSize,
        center = Offset(x + widthPx / 2f, y + heightPx / 2f),
    )
    // 听筒条
    drawRoundRect(
        color = Color(0xFF0A0A0A),
        topLeft = Offset(x + widthPx / 2f - sensorSize * 2.5f, y + heightPx / 2f - sensorSize * 0.3f),
        size = Size(sensorSize * 5f, sensorSize * 0.6f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(sensorSize * 0.3f, sensorSize * 0.3f),
    )
}

/**
 * 绘制针孔: 顶部 / 角部小圆.
 */
private fun DrawScope.drawPunchHole(punch: CutoutGeometry.PunchHole, color: Color) {
    val radiusPx = withHpx(punch.diameterDp) / 2f
    val insetPx = withHpx(punch.insetDp)

    val centerX = when (punch.anchor) {
        CutoutGeometry.Anchor.TOP_CENTER -> size.width / 2f
        CutoutGeometry.Anchor.TOP_LEFT -> insetPx + radiusPx
        CutoutGeometry.Anchor.TOP_RIGHT -> size.width - insetPx - radiusPx
        else -> size.width / 2f
    }
    val centerY = insetPx + radiusPx

    // 摄像头 (深色实心)
    drawCircle(
        color = color,
        radius = radiusPx,
        center = Offset(centerX, centerY),
    )
    // 镜头内圈 (更深)
    drawCircle(
        color = Color(0xFF000000),
        radius = radiusPx * 0.7f,
        center = Offset(centerX, centerY),
    )
    // 镜头反光 (灰色)
    drawCircle(
        color = Color(0x30000000),
        radius = radiusPx * 0.35f,
        center = Offset(centerX - radiusPx * 0.2f, centerY - radiusPx * 0.2f),
    )
}

/**
 * 绘制瀑布屏: 两侧 Bezier 曲线 (模拟 88° 弯折).
 *
 * 这里用深色 Path 沿屏幕两侧画一条 "阴影带", 给视觉上
 * 暗示屏幕从平面弯到侧面.
 */
private fun DrawScope.drawWaterfall(
    waterfall: CutoutGeometry.WaterfallCurve,
    color: Color,
    screenSize: Size,
) {
    if (screenSize == Size.Unspecified) return

    val edgePx = withHpx(waterfall.edgeWidthDp)
    val path = Path()

    when (waterfall.side) {
        CutoutGeometry.Anchor.LEFT_CENTER, CutoutGeometry.Anchor.TOP_LEFT -> {
            // 左侧瀑布
            path.moveTo(0f, 0f)
            path.cubicTo(
                edgePx * 0.5f, screenSize.height * 0.25f,
                edgePx * 0.5f, screenSize.height * 0.75f,
                0f, screenSize.height
            )
            path.lineTo(edgePx, screenSize.height)
            path.cubicTo(
                edgePx * 1.5f, screenSize.height * 0.75f,
                edgePx * 1.5f, screenSize.height * 0.25f,
                edgePx, 0f
            )
            path.close()
        }
        CutoutGeometry.Anchor.RIGHT_CENTER, CutoutGeometry.Anchor.TOP_RIGHT -> {
            // 右侧瀑布
            path.moveTo(screenSize.width, 0f)
            path.cubicTo(
                screenSize.width - edgePx * 0.5f, screenSize.height * 0.25f,
                screenSize.width - edgePx * 0.5f, screenSize.height * 0.75f,
                screenSize.width, screenSize.height
            )
            path.lineTo(screenSize.width - edgePx, screenSize.height)
            path.cubicTo(
                screenSize.width - edgePx * 1.5f, screenSize.height * 0.75f,
                screenSize.width - edgePx * 1.5f, screenSize.height * 0.25f,
                screenSize.width - edgePx, 0f
            )
            path.close()
        }
        else -> {}
    }

    drawPath(path = path, color = color)
    // 在边缘再画一条 0.5dp 浅色线, 模拟反射
    drawPath(
        path = path,
        color = Color(0x40FFFFFF),
        style = Stroke(width = withHpx(0.5f)),
    )
}

/**
 * dp → px 转换 (在 DrawScope 内部).
 */
private fun DrawScope.withHpx(dp: Float): Float = dp * density
