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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 设备外壳 Composable: 模拟手机 / 平板 / 手表的圆角、刘海、状态栏.
 *
 * - 内部画布大小严格按 [DeviceProfile] 的 widthPx × heightPx 比例缩放
 * - 外壳尺寸通过 [outerWidth] / [outerHeight] 控制 (Dp), 默认 `fillMaxSize`
 * - 状态栏 / 刘海用 [Canvas] 画上去
 * - 内容放在 [content] 中
 *
 * 用法:
 * ```kotlin
 * DeviceFrame(profile = DeviceProfiles.PIXEL_6) {
 *     // 你的 Composable (会按设备尺寸缩放后展示)
 *     MyComposable()
 * }
 * ```
 */
@Composable
fun DeviceFrame(
    profile: DeviceProfile,
    modifier: Modifier = Modifier,
    outerWidth: Dp? = null,
    outerHeight: Dp? = null,
    showStatusBar: Boolean = true,
    content: @Composable () -> Unit
) {
    val ratio = profile.aspectRatio
    val width = outerWidth
    val height = outerHeight
    val finalModifier = if (width != null && height != null) {
        modifier.size(width, height)
    } else {
        modifier.fillMaxSize()
    }

    Box(
        modifier = finalModifier
            .clip(shapeForStyle(profile.frameStyle))
            .background(Color(0xFF202124)),
        contentAlignment = Alignment.Center
    ) {
        // 屏幕区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }
            if (showStatusBar && profile.frameStyle == DeviceProfile.FrameStyle.PHONE) {
                StatusBarOverlay(modifier = Modifier.fillMaxSize())
            }
            if (profile.frameStyle == DeviceProfile.FrameStyle.PHONE) {
                NotchOverlay(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** 形状: Phone -> 圆角, Tablet -> 小圆角, Watch -> 圆形, Foldable -> 几乎无圆角. */
private fun shapeForStyle(style: DeviceProfile.FrameStyle) = when (style) {
    DeviceProfile.FrameStyle.PHONE -> RoundedCornerShape(28.dp)
    DeviceProfile.FrameStyle.TABLET -> RoundedCornerShape(16.dp)
    DeviceProfile.FrameStyle.FOLDABLE -> RoundedCornerShape(4.dp)
    DeviceProfile.FrameStyle.WATCH -> RoundedCornerShape(50)
    DeviceProfile.FrameStyle.NONE -> RoundedCornerShape(0.dp)
}

@Composable
private fun StatusBarOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, 36.dp.toPx())
        )
    }
}

@Composable
private fun NotchOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val notchW = 96.dp.toPx()
        val notchH = 24.dp.toPx()
        val cx = size.width / 2
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(cx - notchW / 2, 8.dp.toPx()),
            size = Size(notchW, notchH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
        )
    }
}

/**
 * 计算在 [outerWidth] 约束下, 等比缩放到设备 [profile] 比例后的实际高度.
 * (若 outerHeight 已指定, 走 outerHeight, 此函数仅给出基于宽度推算的高度.)
 */
@Stable
fun computeScaledHeight(outerWidth: Dp, profile: DeviceProfile): Dp {
    val w = outerWidth.value
    val h = w / profile.aspectRatio
    return h.dp
}
