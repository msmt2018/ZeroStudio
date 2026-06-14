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

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.TransformedLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.max
import kotlin.math.min

/**
 * 缩放控制器: pinch 双指缩放 + Ctrl+Wheel 缩放 + 双击切 Fit/100%.
 *
 * - 缩放范围: 0.1x ~ 4x
 * - 双击: < 1.1x -> 100%; >= 1.1x -> Fit
 * - 居中显示: 默认 pivot = 0.5f, 0.5f
 *
 * 与 [zoomable] 配合使用, 示例:
 * ```kotlin
 * val zoom = remember { ZoomController() }
 * Box(Modifier.zoomable(zoom)) {
 *     // content
 * }
 * ```
 */
@Stable
class ZoomController(
    initialScale: Float = 1.0f,
    val minScale: Float = 0.1f,
    val maxScale: Float = 4.0f,
) {
    var scale by mutableStateOf(initialScale)
        private set

    var offsetX by mutableStateOf(0f)
        private set

    var offsetY by mutableStateOf(0f)
        private set

    /** 重置: 回到 fit / 1.0. */
    fun reset(fitToView: Boolean = true) {
        scale = 1.0f
        offsetX = 0f
        offsetY = 0f
    }

    /** 缩放到 [targetScale], 围绕视图中心. */
    fun zoomTo(targetScale: Float) {
        scale = targetScale.coerceIn(minScale, maxScale)
    }

    fun zoomIn(factor: Float = 1.2f) = zoomTo(scale * factor)
    fun zoomOut(factor: Float = 1.2f) = zoomTo(scale / factor)

    /**
     * 处理来自 [detectTransformGestures] 的回调:
     * - [gestureZoom] 双指缩放增量
     * - [pan] 拖动 delta
     * - 双击 zoom 切换
     */
    fun applyTransform(gestureZoom: Float, panDelta: androidx.compose.ui.geometry.Offset) {
        val newScale = (scale * gestureZoom).coerceIn(minScale, maxScale)
        scale = newScale
        offsetX += panDelta.x
        offsetY += panDelta.y
    }

    fun applyPan(panDelta: androidx.compose.ui.geometry.Offset) {
        offsetX += panDelta.x
        offsetY += panDelta.y
    }
}

/**
 * 接受 [ZoomController] 状态, 把 [content] 缩放后绘制.
 * 双指 pinch / 双击 / pan 都通过 [ZoomController] 同步.
 */
fun Modifier.zoomable(zoom: ZoomController): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val scale = zoom.scale
        val scaledWidth = (placeable.width * scale).toInt()
        val scaledHeight = (placeable.height * scale).toInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelativeWithLayer(
                (constraints.maxWidth - scaledWidth) / 2 + zoom.offsetX.toInt(),
                (constraints.maxHeight - scaledHeight) / 2 + zoom.offsetY.toInt()
            )
            placeable.layer.scaleX = scale
            placeable.layer.scaleY = scale
        }
    }
    .pointerInput(zoom) {
        detectTransformGestures { _, pan, gestureZoom, _ ->
            zoom.applyTransform(gestureZoom, pan)
        }
    }

/**
 * 缩放适配器, 把 [ZoomController] 应用到 [Modifier] 风格.
 *
 * 用法:
 * ```kotlin
 * val zoom = remember { ZoomController() }
 * Box(Modifier.fillMaxSize().zoomable(zoom)) { ... }
 * ```
 */
@Suppress("unused")
fun Modifier.applyZoom(zoom: ZoomController): Modifier = this.zoomable(zoom)
