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

package com.itsaky.androidide.compose.preview.data.device

import androidx.compose.runtime.Immutable

/**
 * 屏幕切口 (cutout / notch / punch-hole / waterfall) 几何.
 *
 * 用于 [com.itsaky.androidide.compose.preview.ui.CutoutOverlay] 渲染真实设备屏幕
 * 上的非矩形显示区域.
 *
 * 三种形态:
 * - [Notch]: 顶部矩形 / 药丸形切口 (iPhone 13 ~ 14 / 华为 Mate 20)
 * - [PunchHole]: 居中 / 边角的小圆孔 (Pixel 6+ / Galaxy S24 / 小米 14)
 * - [WaterfallCurve]: 两侧大曲率瀑布边 (华为 Mate 30 Pro / 荣耀 Magic)
 *
 * 所有尺寸使用 **dp** 作为单位, 渲染时按 [densityDpi] 自动换算为 px.
 *
 * @property widthDp 切口宽度 (Notch / PunchHole 适用)
 * @property heightDp 切口高度 (Notch / PunchHole 适用)
 * @property anchor 在屏幕上的锚点位置
 * @property insetDp 距屏幕边缘的内缩 (用于调整位置)
 */
@Immutable
sealed class CutoutGeometry {

    abstract val anchor: Anchor

    /**
     * 顶部居中 / 角部锚点.
     *
     * 注意: 瀑布屏 ([WaterfallCurve]) 的几何不直接走 anchor, 而是
     * 通过 [WaterfallCurve.side] 字段指定 LEFT / RIGHT.
     */
    enum class Anchor {
        /** 顶部居中 (iPhone notch / Pixel punch-hole) */
        TOP_CENTER,

        /** 左上角 (OnePlus 12 / 部分三星) */
        TOP_LEFT,

        /** 右上角 (三星早期 / 部分小米) */
        TOP_RIGHT,

        /** 左侧居中 (瀑布屏左侧) */
        LEFT_CENTER,

        /** 右侧居中 (瀑布屏右侧) */
        RIGHT_CENTER,
    }

    /**
     * 传统刘海 (顶部矩形切口).
     *
     * 典型设备: iPhone 13 / 14, 华为 Mate 20, 早期 Pixel 3 XL.
     *
     * @param widthDp 切口宽度
     * @param heightDp 切口高度 (从屏幕顶端向下)
     * @param anchor 顶部锚点
     * @param cornerRadiusDp 切口圆角 (iPhone 风格用较大值, Mate 用较小)
     */
    data class Notch(
        val widthDp: Float,
        val heightDp: Float,
        override val anchor: Anchor = Anchor.TOP_CENTER,
        val cornerRadiusDp: Float = 8f,
    ) : CutoutGeometry()

    /**
     * iPhone 14 Pro+ 灵动岛 (Dynamic Island).
     *
     * 跟 [Notch] 形状类似 (顶部居中药丸), 但**有动画**: 屏幕闲置时
     * 收缩成药丸形, 显示通知 / 音乐 / 计时器时展开成更宽的矩形. 在
     * Compose preview 里我们只渲染静态形态, 不模拟动画.
     *
     * @param widthDp 切口宽度 (126 ~ 130 dp, 视设备而异)
     * @param heightDp 切口高度 (37 ~ 38 dp)
     * @param cornerRadiusDp 切口圆角 (药丸, ~18dp)
     * @param anchor 顶部锚点 (默认 TOP_CENTER, 横屏时由 [com.itsaky.androidide.compose.preview.ui.DeviceOrientation.rotateAnchor] 调整)
     */
    data class DynamicIsland(
        val widthDp: Float,
        val heightDp: Float,
        val cornerRadiusDp: Float = 18f,
        override val anchor: Anchor = Anchor.TOP_CENTER,
    ) : CutoutGeometry()

    /**
     * 挖孔屏 / 针孔屏 (小圆孔).
     *
     * 典型设备: Pixel 6/7/8 (居中), 三星 S24 (居中), 小米 14 (居中),
     * OnePlus 12 (左上).
     *
     * @param diameterDp 圆孔直径
     * @param anchor 锚点
     * @param insetDp 距屏幕边缘内缩 (例如左上角时光圈距左边 8dp)
     */
    data class PunchHole(
        val diameterDp: Float,
        override val anchor: Anchor = Anchor.TOP_CENTER,
        val insetDp: Float = 0f,
    ) : CutoutGeometry() {
        // 为保持与 Notch 兼容, 暴露 widthDp / heightDp = diameterDp
        val widthDp: Float get() = diameterDp
        val heightDp: Float get() = diameterDp
    }

    /**
     * 瀑布屏曲线 (屏幕两侧 88° 曲面, 几乎没有物理边框).
     *
     * 典型设备: 华为 Mate 30 Pro, 荣耀 Magic, vivo NEX 3.
     *
     * 与 Notch / PunchHole 不同, 瀑布屏切口不是"挖掉"一块, 而是用
     * Bezier 曲线把屏幕两端弯到背面, 因此渲染时需要走 [androidx.compose.ui.graphics.Path]
     * 裁切.
     *
     * @param side 曲线在屏幕哪一侧
     * @param angleDeg 曲面角度 (88° 表示几乎垂直)
     * @param edgeWidthDp 侧边显示区域宽度 (实际可见弯曲部分)
     */
    data class WaterfallCurve(
        val side: Anchor = Anchor.LEFT_CENTER,
        val angleDeg: Float = 88f,
        val edgeWidthDp: Float = 4f,
        override val anchor: Anchor = side,
    ) : CutoutGeometry() {
        // 为保持接口一致
        val widthDp: Float get() = edgeWidthDp
        val heightDp: Float get() = 0f
    }

    companion object {
        // 常用预设 -----

        /** iPhone 13/14 风格 notch (154 × 30 dp, 顶部居中) */
        val IPHONE_14_NOTCH: Notch = Notch(
            widthDp = 154f,
            heightDp = 30f,
            anchor = Anchor.TOP_CENTER,
            cornerRadiusDp = 15f
        )

        /** iPhone 14 Pro+ 灵动岛 (126 × 37 dp, 顶部居中) — v3.2 用独立类型 */
        val DYNAMIC_ISLAND: DynamicIsland = DynamicIsland(
            widthDp = 126f,
            heightDp = 37f,
            cornerRadiusDp = 18f
        )

        /** Pixel 6/7 风格 punch-hole (居中 4dp) */
        val PIXEL_PUNCHHOLE: PunchHole = PunchHole(
            diameterDp = 4f,
            anchor = Anchor.TOP_CENTER,
            insetDp = 6f
        )

        /** 华为 Mate 60 Pro 风格 punch-hole (居中 8dp) */
        val HUAWEI_PUNCHHOLE: PunchHole = PunchHole(
            diameterDp = 8f,
            anchor = Anchor.TOP_CENTER,
            insetDp = 8f
        )

        /** 华为 Mate 30 Pro 风格 88° 瀑布 */
        val HUAWEI_WATERFALL: WaterfallCurve = WaterfallCurve(
            side = Anchor.LEFT_CENTER,
            angleDeg = 88f,
            edgeWidthDp = 6f
        )

        /** 无切口 (大多数现代 Android 设备, 默认值) */
        val NONE: CutoutGeometry? = null
    }
}
