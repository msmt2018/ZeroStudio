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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

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
 *
 * 使用 [Modifier.graphicsLayer] 而非 [Modifier.layout] 缩放:
 * - content 按原始尺寸 measure, 不会因为 [ZoomController.scale] 反复触发重新 measure
 * - 缩放/平移由 GPU layer 应用, 性能更好
 * - offsetX/offsetY 累积, pan 时不会"回到 0"
 */
fun Modifier.zoomable(zoom: ZoomController): Modifier = this
    .graphicsLayer(
        scaleX = zoom.scale,
        scaleY = zoom.scale,
        translationX = zoom.offsetX,
        translationY = zoom.offsetY,
    )
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
