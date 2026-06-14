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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/**
 * 可视化编辑工具枚举 v2.1.
 *
 * 用户通过 [EditorToolbar] 切换:
 * - [Select]    点击选中元素 (默认)
 * - [Pan]       拖动视口 (改变 viewport.offsetX/Y)
 * - [Drag]      拖动选中元素 (改变其 translationX/Y)
 * - [Eyedropper] 点击取色 (采样元素背景色)
 */
@Immutable
enum class EditorTool(val label: String, val description: String) {
    Select("Select", "点击选中元素"),
    Pan("Pan", "拖动视口"),
    Drag("Drag", "拖动选中元素"),
    Eyedropper("Eyedropper", "点击取色"),
}

/**
 * 选中元素的状态 v2.1.
 *
 * @property nodeId 选中的 LayoutNode id (对应 [NodeInfo.id])
 * @property composableName 元素名 (用于 UI 显示)
 * @property bounds 元素边界 (相对设备屏幕坐标系, 单位 px)
 * @property backgroundColor 元素背景色 (可选, 来自 Eyedropper 采样)
 * @property sampledColor 最近一次 Eyedropper 取到的颜色
 * @property translationX / translationY 当前累积位移 (Drag 工具写入)
 */
@Immutable
data class Selection(
    val nodeId: Int,
    val composableName: String,
    val bounds: Rect,
    val backgroundColor: Color? = null,
    val sampledColor: Color? = null,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
) {
    val width: Float get() = bounds.width
    val height: Float get() = bounds.height
    val isTranslated: Boolean get() = translationX != 0f || translationY != 0f
}

/**
 * 选中状态 v2.1.
 *
 * 包装当前 [Selection] + [EditorTool]. 整个编辑器的可观察 state.
 */
@Immutable
data class EditorState(
    val tool: EditorTool = EditorTool.Select,
    val selection: Selection? = null,
    val eyedropperActive: Boolean = false,
) {
    val isEmpty: Boolean get() = selection == null
    val isDragging: Boolean get() = tool == EditorTool.Drag && selection != null
}

/**
 * 拖动手柄的位置枚举.
 *
 * 用于定义选中框上的可拖动手柄 (Resize 工具的 8 个方向).
 */
enum class HandlePosition {
    TopLeft, TopCenter, TopRight,
    MiddleLeft, MiddleRight,
    BottomLeft, BottomCenter, BottomRight,
}

/**
 * 拖动手柄信息.
 *
 * @property position 8 个方向之一
 * @property center 手柄中心 (相对选中框)
 * @property size 手柄大小
 * @property cursor 鼠标光标类型 (供 IDE 桌面端参考)
 */
@Immutable
data class HandleInfo(
    val position: HandlePosition,
    val center: Offset,
    val size: Float = 12f,
)

/**
 * 8 个手柄 (中心 / 角) 的偏移量计算.
 *
 * @param bounds 选中框
 * @param handleSize 手柄大小 (px)
 */
internal fun buildHandles(
    bounds: Rect,
    handleSize: Float,
): List<HandleInfo> = buildList {
    val cx = bounds.left + bounds.width / 2f
    val cy = bounds.top + bounds.height / 2f
    val r = bounds.right
    val b = bounds.bottom
    val l = bounds.left
    val t = bounds.top
    val h = handleSize
    add(HandleInfo(HandlePosition.TopLeft, Offset(l, t), h))
    add(HandleInfo(HandlePosition.TopCenter, Offset(cx, t), h))
    add(HandleInfo(HandlePosition.TopRight, Offset(r, t), h))
    add(HandleInfo(HandlePosition.MiddleLeft, Offset(l, cy), h))
    add(HandleInfo(HandlePosition.MiddleRight, Offset(r, cy), h))
    add(HandleInfo(HandlePosition.BottomLeft, Offset(l, b), h))
    add(HandleInfo(HandlePosition.BottomCenter, Offset(cx, b), h))
    add(HandleInfo(HandlePosition.BottomRight, Offset(r, b), h))
}
