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

package com.zerostudio.webpreview.device

/**
 * 预置设备参数库 (11 档)。
 *
 * UA 字符串取自各设备真实 Chrome UA (截至 2025 年):
 * - iPhone UA 用 iOS Safari + Chrome iOS
 * - Android UA 用 Chrome Android
 * - Desktop UA 用 Chrome Desktop
 *
 * 视口尺寸为 CSS 像素 (CSS px), 不是物理像素。
 * DPR 是设备像素比 (devicePixelRatio)。
 */
object DevicePresets {

    /** 默认设备 (当前 Android 设备实际参数, 不做模拟)。 */
    val DEFAULT: DeviceProfile = DeviceProfile(
        name = "Default (This Device)",
        category = DeviceCategory.PHONE,
        userAgent = "",  // 空字符串表示用 WebView 默认 UA
        viewportWidth = 0,  // 0 表示用 WebView 实际宽度
        viewportHeight = 0,
        devicePixelRatio = 0f,
        isMobile = true,
        hasTouch = true,
    )

    // ── iPhone 系列 ────────────────────────────────────────────

    val IPHONE_SE = DeviceProfile(
        name = "iPhone SE (3rd)",
        category = DeviceCategory.PHONE,
        userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1",
        viewportWidth = 375,
        viewportHeight = 667,
        devicePixelRatio = 2.0f,
        isMobile = true,
        hasTouch = true,
    )

    val IPHONE_14 = DeviceProfile(
        name = "iPhone 14",
        category = DeviceCategory.PHONE,
        userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        viewportWidth = 390,
        viewportHeight = 844,
        devicePixelRatio = 3.0f,
        isMobile = true,
        hasTouch = true,
    )

    val IPHONE_14_PRO_MAX = DeviceProfile(
        name = "iPhone 14 Pro Max",
        category = DeviceCategory.PHONE,
        userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        viewportWidth = 430,
        viewportHeight = 932,
        devicePixelRatio = 3.0f,
        isMobile = true,
        hasTouch = true,
    )

    // ── Android 系列 ──────────────────────────────────────────

    val PIXEL_7 = DeviceProfile(
        name = "Pixel 7",
        category = DeviceCategory.PHONE,
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36",
        viewportWidth = 412,
        viewportHeight = 915,
        devicePixelRatio = 2.625f,
        isMobile = true,
        hasTouch = true,
    )

    val PIXEL_7_PRO = DeviceProfile(
        name = "Pixel 7 Pro",
        category = DeviceCategory.PHONE,
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36",
        viewportWidth = 412,
        viewportHeight = 915,
        devicePixelRatio = 3.5f,
        isMobile = true,
        hasTouch = true,
    )

    val GALAXY_S23 = DeviceProfile(
        name = "Galaxy S23",
        category = DeviceCategory.PHONE,
        userAgent = "Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36",
        viewportWidth = 360,
        viewportHeight = 780,
        devicePixelRatio = 3.0f,
        isMobile = true,
        hasTouch = true,
    )

    // ── iPad 系列 ─────────────────────────────────────────────

    val IPAD_MINI = DeviceProfile(
        name = "iPad Mini (6th)",
        category = DeviceCategory.TABLET,
        userAgent = "Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1",
        viewportWidth = 744,
        viewportHeight = 1133,
        devicePixelRatio = 2.0f,
        isMobile = true,
        hasTouch = true,
    )

    val IPAD_PRO_11 = DeviceProfile(
        name = "iPad Pro 11 (4th)",
        category = DeviceCategory.TABLET,
        userAgent = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        viewportWidth = 834,
        viewportHeight = 1194,
        devicePixelRatio = 2.0f,
        isMobile = true,
        hasTouch = true,
    )

    // ── 桌面系列 ──────────────────────────────────────────────

    val DESKTOP_1080P = DeviceProfile(
        name = "Desktop 1080p",
        category = DeviceCategory.DESKTOP,
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36",
        viewportWidth = 1920,
        viewportHeight = 1080,
        devicePixelRatio = 1.0f,
        isMobile = false,
        hasTouch = false,
    )

    val DESKTOP_4K = DeviceProfile(
        name = "Desktop 4K",
        category = DeviceCategory.DESKTOP,
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36",
        viewportWidth = 3840,
        viewportHeight = 2160,
        devicePixelRatio = 1.0f,
        isMobile = false,
        hasTouch = false,
    )

    val MACBOOK_AIR = DeviceProfile(
        name = "MacBook Air",
        category = DeviceCategory.DESKTOP,
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36",
        viewportWidth = 1440,
        viewportHeight = 900,
        devicePixelRatio = 2.0f,
        isMobile = false,
        hasTouch = false,
    )

    /** 全部预置设备 (按分类排序: PHONE → TABLET → DESKTOP)。 */
    val ALL: List<DeviceProfile> = listOf(
        DEFAULT,
        IPHONE_SE,
        IPHONE_14,
        IPHONE_14_PRO_MAX,
        PIXEL_7,
        PIXEL_7_PRO,
        GALAXY_S23,
        IPAD_MINI,
        IPAD_PRO_11,
        MACBOOK_AIR,
        DESKTOP_1080P,
        DESKTOP_4K,
    )

    /** 按名称查找, 找不到返回 [DEFAULT]。 */
    fun byName(name: String): DeviceProfile = ALL.firstOrNull { it.name == name } ?: DEFAULT
}
