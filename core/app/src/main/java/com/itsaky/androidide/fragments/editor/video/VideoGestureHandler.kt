/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.editor.video

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

/**
 * 手势区域: 屏幕被分为左/中/右三块, 不同区域响应不同手势。
 */
enum class GestureZone {
    /** 左半屏: 上下滑动调亮度。 */
    BRIGHTNESS,

    /** 中间: 左右滑动 seek, 双击播放/暂停。 */
    SEEK,

    /** 右半屏: 上下滑动调音量。 */
    VOLUME,
}

/**
 * 手势事件回调。
 *
 * 由 [VideoPreviewFragment] 实现, 内部转发给 [VideoPlaybackController] / Window / AudioManager。
 */
interface VideoGestureCallbacks {

    /** 亮度变化 (0..1, -1 表示跟随系统)。 */
    fun onBrightnessChange(brightness: Float)

    /** 音量变化 (0..1)。 */
    fun onVolumeChange(volume: Float)

    /**
     * Seek 偏移 (相对当前位置的毫秒数, 可为负)。
     * 调用方负责实际 seekTo(currentPosition + deltaMs)。
     */
    fun onSeekDelta(deltaMs: Long)

    /** 双击: 切换播放/暂停。 */
    fun onDoubleTap()

    /** 单击: 切换控制栏可见性。 */
    fun onSingleTap()

    /** 长按: 临时 2x 倍速 (按住时)。 */
    fun onLongPressStart()

    /** 长按结束: 恢复 1x 倍速。 */
    fun onLongPressEnd()

    /** 获取当前亮度 (供 onDrag 计算 delta)。 -1 = 跟随系统, 默认 0.5。 */
    fun getInitialBrightness(): Float

    /** 获取当前音量 (0..1)。 */
    fun getInitialVolume(): Float

    /** 获取当前播放位置 (毫秒)。 */
    fun getInitialPositionMs(): Long
}

/**
 * 视频手势处理器: 检测拖拽 / 单击 / 双击 / 长按, 转发给 [callbacks]。
 *
 * ## 手势分配 (参照主流播放器)
 * - 左半屏上下拖 → 亮度
 * - 右半屏上下拖 → 音量
 * - 中间左右拖 → Seek (10ms/px)
 * - 单击 → 切换控件可见性
 * - 双击 → 播放/暂停
 * - 长按 → 临时 2x 倍速 (松手恢复)
 *
 * 用法: 在 PlayerView 之上叠一层透明的 Box, 用 [gestureModifier] 处理手势。
 *
 * ```
 * Box(modifier = Modifier.fillMaxSize().then(handler.gestureModifier(size, callbacks))) {
 *     // PlayerView 在下层
 * }
 * ```
 *
 * @author ZeroStudio
 */
class VideoGestureHandler {

    /**
     * 创建一个 Modifier, 处理视频手势。
     *
     * @param screenSize 屏幕尺寸 (用于计算左/中/右区域)
     * @param callbacks 手势回调
     */
    @Composable
    fun gestureModifier(
        screenSize: IntSize,
        callbacks: VideoGestureCallbacks,
    ): Modifier {
        var dragStartY by remember { mutableFloatStateOf(0f) }
        var dragStartX by remember { mutableFloatStateOf(0f) }
        var lastDragX by remember { mutableFloatStateOf(0f) }
        var activeZone by remember { mutableStateOf(GestureZone.SEEK) }
        var initialBrightness by remember { mutableFloatStateOf(-1f) }
        var initialVolume by remember { mutableFloatStateOf(1f) }

        return Modifier.pointerInput(screenSize) {
            detectTapGestures(
                onTap = { callbacks.onSingleTap() },
                onDoubleTap = { callbacks.onDoubleTap() },
                onLongPress = { callbacks.onLongPressStart() },
            )
        }.pointerInput(screenSize) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragStartY = offset.y
                    dragStartX = offset.x
                    lastDragX = offset.x
                    // 根据起始 X 位置确定手势区域
                    val width = screenSize.width.coerceAtLeast(1)
                    activeZone = when {
                        offset.x < width / 3f -> GestureZone.BRIGHTNESS
                        offset.x > width * 2f / 3f -> GestureZone.VOLUME
                        else -> GestureZone.SEEK
                    }
                    initialBrightness = callbacks.getInitialBrightness()
                    initialVolume = callbacks.getInitialVolume()
                },
                onDrag = { change, _ ->
                    val totalDy = dragStartY - change.position.y // 上滑为正
                    val dx = change.position.x - lastDragX
                    lastDragX = change.position.x

                    when (activeZone) {
                        GestureZone.BRIGHTNESS -> {
                            val delta = totalDy / screenSize.height.coerceAtLeast(1)
                            val newBrightness = (initialBrightness.coerceAtLeast(0f) + delta).coerceIn(0f, 1f)
                            callbacks.onBrightnessChange(newBrightness)
                        }
                        GestureZone.VOLUME -> {
                            val delta = totalDy / screenSize.height.coerceAtLeast(1)
                            val newVolume = (initialVolume + delta).coerceIn(0f, 1f)
                            callbacks.onVolumeChange(newVolume)
                        }
                        GestureZone.SEEK -> {
                            // 左右拖动 → 10ms/px
                            val seekDeltaMs = (dx * 10L).toLong()
                            if (kotlin.math.abs(seekDeltaMs) > 50) {
                                callbacks.onSeekDelta(seekDeltaMs)
                                dragStartX = change.position.x
                            }
                        }
                    }
                    change.consume()
                },
                onDragEnd = {
                    callbacks.onLongPressEnd()
                },
                onDragCancel = {
                    callbacks.onLongPressEnd()
                },
            )
        }
    }
}
