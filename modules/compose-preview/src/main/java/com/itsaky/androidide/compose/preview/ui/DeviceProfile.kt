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

/**
 * 设备 / 分辨率 配置.
 *
 * 参考 Android Studio Compose Preview 模板, 提供常用 Pixel / Tablet / Foldable / Watch 尺寸.
 * 自定义场景下, 用户可填入任意 width × height × dpi.
 */
data class DeviceProfile(
    val id: String,
    val displayName: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val frameStyle: FrameStyle = FrameStyle.PHONE,
    val isCustom: Boolean = false,
) {
    /** width in dp. */
    val widthDp: Float get() = widthPx * 160f / densityDpi

    /** height in dp. */
    val heightDp: Float get() = heightPx * 160f / densityDpi

    val aspectRatio: Float get() = widthPx.toFloat() / heightPx

    enum class FrameStyle { PHONE, TABLET, FOLDABLE, WATCH, NONE }
}

object DeviceProfiles {

    val PIXEL_4 = DeviceProfile(
        id = "pixel-4",
        displayName = "Pixel 4",
        widthPx = 1080, heightPx = 2280, densityDpi = 440
    )

    val PIXEL_5 = DeviceProfile(
        id = "pixel-5",
        displayName = "Pixel 5",
        widthPx = 1080, heightPx = 2340, densityDpi = 440
    )

    val PIXEL_6 = DeviceProfile(
        id = "pixel-6",
        displayName = "Pixel 6",
        widthPx = 1080, heightPx = 2400, densityDpi = 420
    )

    val PIXEL_7 = DeviceProfile(
        id = "pixel-7",
        displayName = "Pixel 7",
        widthPx = 1080, heightPx = 2400, densityDpi = 420
    )

    val PIXEL_TABLET = DeviceProfile(
        id = "pixel-tablet",
        displayName = "Pixel Tablet",
        widthPx = 1600, heightPx = 2560, densityDpi = 320,
        frameStyle = DeviceProfile.FrameStyle.TABLET
    )

    val FOLDABLE_INNER = DeviceProfile(
        id = "foldable-inner",
        displayName = "Foldable (Inner)",
        widthPx = 2208, heightPx = 1840, densityDpi = 420,
        frameStyle = DeviceProfile.FrameStyle.FOLDABLE
    )

    val WEAR_OS_SMALL = DeviceProfile(
        id = "wear-small",
        displayName = "Wear OS Small",
        widthPx = 384, heightPx = 384, densityDpi = 320,
        frameStyle = DeviceProfile.FrameStyle.WATCH
    )

    val WEAR_OS_LARGE = DeviceProfile(
        id = "wear-large",
        displayName = "Wear OS Large",
        widthPx = 454, heightPx = 454, densityDpi = 320,
        frameStyle = DeviceProfile.FrameStyle.WATCH
    )

    /** 全部内置 profile. */
    val builtins: List<DeviceProfile> = listOf(
        PIXEL_4, PIXEL_5, PIXEL_6, PIXEL_7,
        PIXEL_TABLET, FOLDABLE_INNER,
        WEAR_OS_SMALL, WEAR_OS_LARGE
    )

    fun findById(id: String): DeviceProfile? =
        if (id.isBlank()) PIXEL_6 else builtins.firstOrNull { it.id == id }
}
