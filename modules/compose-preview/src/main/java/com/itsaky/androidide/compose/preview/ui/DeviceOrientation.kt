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

/**
 * 设备方向 v3.5.
 *
 * 用于驱动 [DeviceFrame] / [CutoutOverlay] / [FoldableHingeOverlay] /
 * [SystemBarsOverlay] / 物理按键 在横屏 / 竖屏下做适配.
 *
 * 四种方向:
 * - [PORTRAIT] 竖屏 (0°), 默认. 物理设备的"自然方向"
 * - [LANDSCAPE] 横屏 (顺时针 90°)
 * - [REVERSE_PORTRAIT] 倒置竖屏 (180°), 部分平板/折叠屏支持
 * - [REVERSE_LANDSCAPE] 反向横屏 (顺时针 270°)
 *
 * 旋转规则 (顺时针 90° LANDSCAPE 举例):
 * - 屏幕 widthPx ↔ heightPx (对调)
 * - bezel.topDp ↔ bezel.leftDp, bezel.bottomDp ↔ bezel.rightDp
 * - 物理键 positionXdp ↔ positionYdp, widthDp ↔ heightDp
 *   (坐标对调: 横屏时电源键在顶部, positionXdp=0, positionYdp=originalScreenWidth-6)
 * - statusBar / navigationBar 高度对调 (横屏时 status bar 仍是状态栏,
 *   但 status bar 高度是 heightDp, 而 heightDp = 原 widthDp)
 * - Cutout 锚点: 旋转 90° 后, TOP_CENTER → LEFT_CENTER, LEFT_CENTER → BOTTOM_CENTER,
 *   等等. 详情见 [anchorAfterRotate].
 * - FoldableHinge: 横向铰链 (horizontal = true) 旋转 90° 变竖向 (horizontal = false)
 *
 * @see DeviceProfile.effectiveWidthDp
 * @see DeviceProfile.effectiveHeightDp
 */
@Immutable
enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE,
    REVERSE_PORTRAIT,
    REVERSE_LANDSCAPE;

    /**
     * 是否为"横屏" (短边朝上).
     *
     * 旋转 90° 或 270° 都算横屏 (屏幕长边水平). 设备上长边对应原
     * [DeviceProfile.widthPx].
     */
    val isLandscape: Boolean get() = this == LANDSCAPE || this == REVERSE_LANDSCAPE

    /**
     * 是否为"反向" (180° / 270° 旋转).
     *
     * 反向时某些设备 UI 元素 (如 "Power" 按键) 会位置对调 (虽然物理键
     * 还在同一位置, 但相对屏幕的"上"变了). 我们的物理键定位按
     * "用户面对屏幕" 计算, 所以反向时物理键坐标需要 180° 翻转.
     */
    val isReverse: Boolean get() = this == REVERSE_PORTRAIT || this == REVERSE_LANDSCAPE

    /**
     * 旋转的"次数" (0/1/2/3, 每次 90° 顺时针). 用于精确计算旋转后
     * 几何. REVERSE_PORTRAIT = 2, REVERSE_LANDSCAPE = 3.
     */
    val quarterTurns: Int get() = when (this) {
        PORTRAIT -> 0
        LANDSCAPE -> 1
        REVERSE_PORTRAIT -> 2
        REVERSE_LANDSCAPE -> 3
    }

    /**
     * 把 [CutoutGeometry.Anchor] 按当前方向旋转对应的角度.
     *
     * 旋转规则: 顺时针 90° = `TOP_CENTER → LEFT_CENTER, LEFT_CENTER → BOTTOM_CENTER,
     * TOP_LEFT → BOTTOM_LEFT, TOP_RIGHT → TOP_LEFT` (屏幕坐标系, y 向下). 详情见函数体注释.
     *
     * 注: [CutoutGeometry.Anchor] 只有 5 个值 (TOP_CENTER / TOP_LEFT / TOP_RIGHT /
     * LEFT_CENTER / RIGHT_CENTER), 没有 BOTTOM_* 锚点. 旋转 BOTTOM 等价于
     * 旋转 TOP (因为旋转 180° 互换).
     */
    fun rotateAnchor(
        anchor: com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor
    ): com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor {
        return when (anchor) {
            com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_CENTER ->
                topCenterAfterRotate()
            com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.LEFT_CENTER ->
                leftCenterAfterRotate()
            com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.RIGHT_CENTER ->
                rightCenterAfterRotate()
            com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_LEFT ->
                topLeftAfterRotate()
            com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_RIGHT ->
                topRightAfterRotate()
        }
    }

    private fun topCenterAfterRotate() = when (this) {
        PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_CENTER
        LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.LEFT_CENTER
        REVERSE_PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_CENTER
        REVERSE_LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.RIGHT_CENTER
    }

    private fun leftCenterAfterRotate() = when (this) {
        PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.LEFT_CENTER
        LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_CENTER
        REVERSE_PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.LEFT_CENTER
        REVERSE_LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_CENTER
    }

    private fun rightCenterAfterRotate() = when (this) {
        PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.RIGHT_CENTER
        LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_CENTER
        REVERSE_PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.RIGHT_CENTER
        REVERSE_LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_CENTER
    }

    private fun topLeftAfterRotate() = when (this) {
        PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_LEFT
        LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_LEFT
        REVERSE_PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_RIGHT
        REVERSE_LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_RIGHT
    }

    private fun topRightAfterRotate() = when (this) {
        PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_RIGHT
        LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_RIGHT
        REVERSE_PORTRAIT -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_LEFT
        REVERSE_LANDSCAPE -> com.itsaky.androidide.compose.preview.data.device.CutoutGeometry.Anchor.TOP_LEFT
    }

    /**
     * 把 (x, y) 坐标按当前方向旋转 (坐标原点 = 屏幕左上角, 单位无关).
     *
     * 用于物理按键定位 + 切口中心点定位. 输入输出坐标都是相对"旋转前"屏幕
     * 坐标系. 旋转 90° 后, 屏幕的"宽"变成原"高", "高"变成原"宽".
     *
     * 调用方需要在传入坐标时明确是在"原屏幕坐标系"还是"新屏幕坐标系" ——
     * 这个方法是把"原坐标系下的 (x, y)" 转为"新坐标系下的 (x', y')".
     *
     * 算法 (顺时针 90°): (x, y) → (newWidth - y, x)
     * 其中 newWidth = 原 height
     */
    fun rotateCoord(
        x: Float,
        y: Float,
        originalWidth: Float,
        originalHeight: Float,
    ): Pair<Float, Float> {
        return when (this) {
            PORTRAIT -> x to y
            LANDSCAPE -> (originalHeight - y) to x
            REVERSE_PORTRAIT -> (originalWidth - x) to (originalHeight - y)
            REVERSE_LANDSCAPE -> y to (originalWidth - x)
        }
    }

    /**
     * 把 (width, height) 尺寸按当前方向旋转.
     *
     * 旋转 90° / 270° 时, width ↔ height 对调; 旋转 180° 时不变.
     */
    fun rotateSize(width: Float, height: Float): Pair<Float, Float> {
        return if (isLandscape) height to width else width to height
    }

    /**
     * 把 bezel (top/bottom/left/right) 按当前方向旋转.
     *
     * 顺时针 90°: top → left, left → bottom, bottom → right, right → top
     * 180°: top ↔ bottom, left ↔ right (对调)
     * 270°: top → right, right → bottom, bottom → left, left → top
     */
    fun rotateBezels(
        top: Float,
        bottom: Float,
        left: Float,
        right: Float,
    ): DeviceProfile.Bezels {
        return when (this) {
            PORTRAIT -> DeviceProfile.Bezels(top, bottom, left, right)
            LANDSCAPE -> DeviceProfile.Bezels(left, right, top, bottom)
            REVERSE_PORTRAIT -> DeviceProfile.Bezels(bottom, top, right, left)
            REVERSE_LANDSCAPE -> DeviceProfile.Bezels(right, top, left, bottom)
        }
    }

    /**
     * 把 (statusBarHeightDp, navigationBarHeightDp) 按当前方向"旋转".
     *
     * 注意: 旋转后 statusBar 仍在"新屏幕的顶", 不会变成横屏的"侧". 所以
     * statusBarHeight 是"原 statusBar 高度在新方向上的厚度", 旋转 90° 后
     * 实际占用空间 = 原 statusBar 高度 (值不变, 但水平方向看是 width 的一部分).
     *
     * 状态栏 / 导航栏高度数值本身不旋转 — 但渲染时它们贴在"新屏幕的顶 / 底",
     * 占用的是"新屏幕的高度方向" = "原屏幕的宽度方向". 所以视觉上看, 旋转 90°
     * 时 statusBar 变窄了.
     *
     * 结论: statusBarHeight / navigationBarHeight 值不变. (旋转不会改数值)
     * 此函数作为 placeholder 留给后续可能的"按方向调整高度"需求.
     */
    fun rotateBarHeights(statusBarHeightDp: Int, navigationBarHeightDp: Int): Pair<Int, Int> {
        return statusBarHeightDp to navigationBarHeightDp
    }
}
