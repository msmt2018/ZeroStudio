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
 * 设备物理按键模型.
 *
 * 模拟真实手机 / 平板 / 折叠屏机身上的物理按钮 (电源 / 音量 / 相机).
 * 用于 [com.itsaky.androidide.compose.preview.ui.DeviceFrame] 在屏幕
 * 外壳上绘制按钮位置.
 *
 * v2.1 P0 只渲染矩形 + 文字 (不做 3D 仿真 / 凹凸效果). P1 增强时
 * 可以叠加渐变 / 阴影 / 颜色等.
 *
 * v3.5 增 [rotated] 方法: 按设备方向旋转按键坐标 + 尺寸. 横屏时物理按键
 * 位置自然变化 (电源键从右移到顶, 音量键从左移到上).
 *
 * 坐标定义:
 * - 屏幕 (Screen) 坐标系: 原点 = 屏幕左上角, 单位 dp
 * - [positionXdp] / [positionYdp] 是按键**中心点**相对屏幕左上角的偏移
 * - [widthDp] / [heightDp] 是按键自身尺寸
 *
 * @property positionXdp 中心 X (相对屏幕左上角, 单位 dp)
 * @property positionYdp 中心 Y (相对屏幕左上角, 单位 dp)
 * @property widthDp 按键宽度
 * @property heightDp 按键高度
 */
@Immutable
sealed class PhysicalKey {

    abstract val positionXdp: Float
    abstract val positionYdp: Float
    abstract val widthDp: Float
    abstract val heightDp: Float

    /** 按键显示名 (Compose 渲染时用, 例如 "Power" / "Vol+" / "Cam") */
    abstract val displayName: String

    /**
     * 【v3.5】按 [orientation] 旋转返回新按键. 旋转 90° 时:
     * - positionXdp, positionYdp 互换并翻转 (用 [DeviceOrientation.rotateCoord])
     * - widthDp, heightDp 互换
     *
     * @param orientation 目标方向
     * @param originalScreenWidthDp 物理设备"自然方向" (竖屏) 屏幕宽
     * @param originalScreenHeightDp 物理设备"自然方向" (竖屏) 屏幕高
     *
     * 注意: 输入的 (originalScreenWidthDp, originalScreenHeightDp) 必须用
     * 物理设备的"竖屏"尺寸, 不是 [DeviceProfile.effectiveWidthDp]. 因为
     * [positionXdp] / [positionYdp] 在 catalog 里是按"竖屏"定义的.
     */
    fun rotated(
        orientation: com.itsaky.androidide.compose.preview.ui.DeviceOrientation,
        originalScreenWidthDp: Float,
        originalScreenHeightDp: Float,
    ): PhysicalKey {
        if (orientation == com.itsaky.androidide.compose.preview.ui.DeviceOrientation.PORTRAIT) {
            return this
        }
        val (nx, ny) = orientation.rotateCoord(
            x = positionXdp,
            y = positionYdp,
            originalWidth = originalScreenWidthDp,
            originalHeight = originalScreenHeightDp,
        )
        val (nw, nh) = orientation.rotateSize(widthDp, heightDp)
        return when (this) {
            is Power -> copy(positionXdp = nx, positionYdp = ny, widthDp = nw, heightDp = nh)
            is VolumeUp -> copy(positionXdp = nx, positionYdp = ny, widthDp = nw, heightDp = nh)
            is VolumeDown -> copy(positionXdp = nx, positionYdp = ny, widthDp = nw, heightDp = nh)
            is Camera -> copy(positionXdp = nx, positionYdp = ny, widthDp = nw, heightDp = nh)
            is Assistant -> copy(positionXdp = nx, positionYdp = ny, widthDp = nw, heightDp = nh)
        }
    }

    /**
     * 电源键 (绝大多数手机在右侧).
     *
     * 典型位置: 屏幕右上往下 1/3 处.
     */
    data class Power(
        override val positionXdp: Float,
        override val positionYdp: Float,
        override val widthDp: Float = 4f,
        override val heightDp: Float = 36f,
    ) : PhysicalKey() {
        override val displayName: String = "Power"
    }

    /**
     * 音量上键 (通常在电源键上方).
     */
    data class VolumeUp(
        override val positionXdp: Float,
        override val positionYdp: Float,
        override val widthDp: Float = 4f,
        override val heightDp: Float = 28f,
    ) : PhysicalKey() {
        override val displayName: String = "Vol+"
    }

    /**
     * 音量下键 (通常紧贴 Vol+ 下方).
     */
    data class VolumeDown(
        override val positionXdp: Float,
        override val positionYdp: Float,
        override val widthDp: Float = 4f,
        override val heightDp: Float = 28f,
    ) : PhysicalKey() {
        override val displayName: String = "Vol-"
    }

    /**
     * 相机键 (部分设备: Pixel 3 ~ 5, Sony Xperia, iPhone 16 等).
     */
    data class Camera(
        override val positionXdp: Float,
        override val positionYdp: Float,
        override val widthDp: Float = 4f,
        override val heightDp: Float = 22f,
    ) : PhysicalKey() {
        override val displayName: String = "Cam"
    }

    /**
     * Bixby / Google Assistant 键 (三星 / 早期 Pixel).
     */
    data class Assistant(
        override val positionXdp: Float,
        override val positionYdp: Float,
        override val widthDp: Float = 4f,
        override val heightDp: Float = 22f,
    ) : PhysicalKey() {
        override val displayName: String = "AI"
    }

    companion object {
        // 常用预设 -----

        /**
         * Pixel 系列 (音量键在左侧, 电源键在右侧).
         *
         * 屏幕宽 1080px @ 420dpi ≈ 411dp, 因此:
         * - 左侧 Vol+/Vol- x = -6dp (即屏幕左外 6dp)
         * - 右侧 Power x = 411dp + 6dp = 417dp (即屏幕右外 6dp)
         */
        fun pixelKeys(screenWidthDp: Float): List<PhysicalKey> = listOf(
            VolumeUp(positionXdp = -6f, positionYdp = 320f),
            VolumeDown(positionXdp = -6f, positionYdp = 360f),
            Power(positionXdp = screenWidthDp + 6f, positionYdp = 320f)
        )

        /**
         * 华为 / 小米 / 三星标准 (音量键在左侧, 电源键在右侧).
         */
        fun standardAndroidKeys(screenWidthDp: Float): List<PhysicalKey> = listOf(
            VolumeUp(positionXdp = -6f, positionYdp = 300f),
            VolumeDown(positionXdp = -6f, positionYdp = 340f),
            Power(positionXdp = screenWidthDp + 6f, positionYdp = 300f)
        )

        /**
         * iPhone (左侧音量 + Action Button, 右侧电源). 静音键被
         * iPhone 15 Pro+ 替换为 Action Button.
         *
         * 按真实 iPhone 15 Pro Max 设备图物理键位置 (相对设备原始 dp):
         * - 左侧 Action Button y ≈ screenHeight × 14%
         * - 左侧 Vol+ y ≈ screenHeight × 18%
         * - 左侧 Vol- y ≈ screenHeight × 22%
         * - 右侧 Power y ≈ screenHeight × 18%
         *
         * 屏幕宽 / 高比例固定为 9:19.5, 因此 screenHeight ≈ screenWidthDp × 2.167.
         * 实际 y 坐标用 screenWidthDp × 0.305 ~ 0.480 系数计算.
         */
        fun iphoneKeys(screenWidthDp: Float): List<PhysicalKey> {
            val h = screenWidthDp * 2.167f
            return listOf(
                // 左侧 Action Button (iPhone 15 Pro+) — 替代静音键
                Assistant(positionXdp = -6f, positionYdp = h * 0.14f),
                VolumeUp(positionXdp = -6f, positionYdp = h * 0.18f),
                VolumeDown(positionXdp = -6f, positionYdp = h * 0.22f),
                // 右侧 Power (Side button) — 屏幕中上部
                Power(positionXdp = screenWidthDp + 6f, positionYdp = h * 0.18f)
            )
        }

        /** 无按键 (平板 / 折叠屏内屏 / 桌面模式 / 自定义) */
        val NO_KEYS: List<PhysicalKey> = emptyList()
    }
}
