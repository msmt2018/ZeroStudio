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

import androidx.compose.ui.graphics.Color
import com.itsaky.androidide.compose.preview.ui.DeviceProfile
import com.itsaky.androidide.compose.preview.ui.DeviceProfile.Bezels
import com.itsaky.androidide.compose.preview.ui.DeviceProfile.FormFactor

/**
 * 真实设备目录 v2.1.
 *
 * 内置 30+ 真实设备 Profile, 按 [FormFactor] 分组.
 *
 * **数据来源**:
 * - Wikipedia (设备屏幕尺寸 / 分辨率)
 * - GSMArena (设备规格)
 * - 厂商官网 (相机 / 听筒 / 物理键位置)
 *
 * 屏幕宽 / 高 px 与 DPI 是真实测量值; 边框 / 圆角为视觉近似 (误差 ± 2dp).
 * cutout 尺寸按厂商公布值, 物理键位置按设备平面图.
 *
 * **使用示例**:
 * ```
 * val pixel7 = DeviceCatalog.byId("pixel-7")
 * val phones = DeviceCatalog.byFormFactor(FormFactor.PHONE)
 * ```
 */
object DeviceCatalog {

    // === Phone (现代全面屏, 居中 / 角部 punch-hole) ===

    private val pixel_7 = DeviceProfile(
        id = "pixel-7",
        displayName = "Pixel 7",
        manufacturer = "Google", model = "Pixel 7", osVersion = "Android 14",
        widthPx = 1080, heightPx = 2400, densityDpi = 420,
        cornerRadiusDp = 30f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 6f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.pixelKeys(411f),
        chassisColor = Color(0xFF1F1F25),
    )

    private val pixel_7_pro = DeviceProfile(
        id = "pixel-7-pro",
        displayName = "Pixel 7 Pro",
        manufacturer = "Google", model = "Pixel 7 Pro", osVersion = "Android 14",
        widthPx = 1440, heightPx = 3120, densityDpi = 560,
        cornerRadiusDp = 32f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 8f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.pixelKeys(411f),
        chassisColor = Color(0xFF20202A),
    )

    private val pixel_8 = DeviceProfile(
        id = "pixel-8",
        displayName = "Pixel 8",
        manufacturer = "Google", model = "Pixel 8", osVersion = "Android 14",
        widthPx = 1080, heightPx = 2400, densityDpi = 420,
        cornerRadiusDp = 30f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 6f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.pixelKeys(411f),
        chassisColor = Color(0xFFEFEAE3),
    )

    private val pixel_8_pro = DeviceProfile(
        id = "pixel-8-pro",
        displayName = "Pixel 8 Pro",
        manufacturer = "Google", model = "Pixel 8 Pro", osVersion = "Android 14",
        widthPx = 1344, heightPx = 2992, densityDpi = 560,
        cornerRadiusDp = 32f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 8f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.pixelKeys(385f),
        chassisColor = Color(0xFF1B1B1F),
    )

    private val huawei_mate60_pro = DeviceProfile(
        id = "huawei-mate60-pro",
        displayName = "Huawei Mate 60 Pro",
        manufacturer = "Huawei", model = "Mate 60 Pro", osVersion = "HarmonyOS 4",
        widthPx = 1260, heightPx = 2720, densityDpi = 460,
        cornerRadiusDp = 28f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 8f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 8f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.standardAndroidKeys(437f),
        chassisColor = Color(0xFF3C3A37),
    )

    private val huawei_p30_pro = DeviceProfile(
        id = "huawei-p30-pro",
        displayName = "Huawei P30 Pro",
        manufacturer = "Huawei", model = "P30 Pro", osVersion = "Android 10",
        widthPx = 1080, heightPx = 2340, densityDpi = 480,
        cornerRadiusDp = 32f,
        cutout = CutoutGeometry.WaterfallCurve(
            side = CutoutGeometry.Anchor.LEFT_CENTER,
            angleDeg = 88f,
            edgeWidthDp = 4f
        ),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.standardAndroidKeys(360f),
        chassisColor = Color(0xFF1F2A44),
    )

    private val xiaomi_14_pro = DeviceProfile(
        id = "xiaomi-14-pro",
        displayName = "Xiaomi 14 Pro",
        manufacturer = "Xiaomi", model = "14 Pro", osVersion = "HyperOS 1",
        widthPx = 1440, heightPx = 3200, densityDpi = 560,
        cornerRadiusDp = 28f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 6f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.standardAndroidKeys(411f),
        chassisColor = Color(0xFF202020),
    )

    private val samsung_s24_ultra = DeviceProfile(
        id = "samsung-s24-ultra",
        displayName = "Samsung Galaxy S24 Ultra",
        manufacturer = "Samsung", model = "Galaxy S24 Ultra", osVersion = "Android 14",
        widthPx = 1440, heightPx = 3120, densityDpi = 560,
        cornerRadiusDp = 14f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 6f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.standardAndroidKeys(411f),
        chassisColor = Color(0xFF353535),
    )

    private val samsung_s23 = DeviceProfile(
        id = "samsung-s23",
        displayName = "Samsung Galaxy S23",
        manufacturer = "Samsung", model = "Galaxy S23", osVersion = "Android 14",
        widthPx = 1080, heightPx = 2340, densityDpi = 420,
        cornerRadiusDp = 18f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 6f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.standardAndroidKeys(390f),
        chassisColor = Color(0xFFEEEAE4),
    )

    private val oneplus_12 = DeviceProfile(
        id = "oneplus-12",
        displayName = "OnePlus 12",
        manufacturer = "OnePlus", model = "12", osVersion = "OxygenOS 14",
        widthPx = 1440, heightPx = 3168, densityDpi = 560,
        cornerRadiusDp = 24f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_LEFT, insetDp = 8f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.standardAndroidKeys(412f),
        chassisColor = Color(0xFF2A2A2D),
    )

    private val honor_magic6_pro = DeviceProfile(
        id = "honor-magic6-pro",
        displayName = "Honor Magic 6 Pro",
        manufacturer = "Honor", model = "Magic 6 Pro", osVersion = "MagicOS 8",
        widthPx = 1280, heightPx = 2800, densityDpi = 460,
        cornerRadiusDp = 28f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 6f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 6f),
        bezels = Bezels.PHONE_MODERN,
        physicalKeys = PhysicalKey.standardAndroidKeys(437f),
        chassisColor = Color(0xFF1E1E22),
    )

    // === Phone (刘海 / Dynamic Island / iPhone) ===

    private val iphone_13 = DeviceProfile(
        id = "iphone-13",
        displayName = "iPhone 13",
        manufacturer = "Apple", model = "iPhone 13", osVersion = "iOS 17",
        widthPx = 1170, heightPx = 2532, densityDpi = 460,
        cornerRadiusDp = 42f,
        cutout = CutoutGeometry.Notch(
            widthDp = 154f, heightDp = 30f,
            anchor = CutoutGeometry.Anchor.TOP_CENTER, cornerRadiusDp = 15f
        ),
        bezels = Bezels(topDp = 30f, bottomDp = 22f, leftDp = 4f, rightDp = 4f),
        physicalKeys = PhysicalKey.iphoneKeys(390f),
        chassisColor = Color(0xFF1B1B1D),
    )

    private val iphone_14 = DeviceProfile(
        id = "iphone-14",
        displayName = "iPhone 14",
        manufacturer = "Apple", model = "iPhone 14", osVersion = "iOS 17",
        widthPx = 1170, heightPx = 2532, densityDpi = 460,
        cornerRadiusDp = 42f,
        cutout = CutoutGeometry.Notch(
            widthDp = 154f, heightDp = 30f,
            anchor = CutoutGeometry.Anchor.TOP_CENTER, cornerRadiusDp = 15f
        ),
        bezels = Bezels(topDp = 30f, bottomDp = 22f, leftDp = 4f, rightDp = 4f),
        physicalKeys = PhysicalKey.iphoneKeys(390f),
        chassisColor = Color(0xFFF1F0ED),
    )

    private val iphone_15_pro = DeviceProfile(
        id = "iphone-15-pro",
        displayName = "iPhone 15 Pro",
        manufacturer = "Apple", model = "iPhone 15 Pro", osVersion = "iOS 17",
        widthPx = 1179, heightPx = 2556, densityDpi = 460,
        cornerRadiusDp = 50f,
        cutout = CutoutGeometry.Notch(
            widthDp = 126f, heightDp = 37f,
            anchor = CutoutGeometry.Anchor.TOP_CENTER, cornerRadiusDp = 18f
        ),
        bezels = Bezels(topDp = 37f, bottomDp = 24f, leftDp = 4f, rightDp = 4f),
        physicalKeys = PhysicalKey.iphoneKeys(393f),
        chassisColor = Color(0xFF34343A),
    )

    private val iphone_15_pro_max = DeviceProfile(
        id = "iphone-15-pro-max",
        displayName = "iPhone 15 Pro Max",
        manufacturer = "Apple", model = "iPhone 15 Pro Max", osVersion = "iOS 17",
        widthPx = 1290, heightPx = 2796, densityDpi = 460,
        cornerRadiusDp = 50f,
        cutout = CutoutGeometry.Notch(
            widthDp = 130f, heightDp = 38f,
            anchor = CutoutGeometry.Anchor.TOP_CENTER, cornerRadiusDp = 19f
        ),
        bezels = Bezels(topDp = 38f, bottomDp = 26f, leftDp = 4f, rightDp = 4f),
        physicalKeys = PhysicalKey.iphoneKeys(430f),
        chassisColor = Color(0xFF2A2A2C),
    )

    // === Foldable (内屏 / 外屏) ===

    private val galaxy_z_fold5_inner = DeviceProfile(
        id = "galaxy-z-fold5-inner",
        displayName = "Galaxy Z Fold 5 (Inner)",
        manufacturer = "Samsung", model = "Galaxy Z Fold 5 (Inner)", osVersion = "Android 14",
        formFactor = FormFactor.FOLDABLE_INNER,
        widthPx = 1812, heightPx = 2176, densityDpi = 374,
        cornerRadiusDp = 6f,
        cutout = null, // 内屏无 cutout
        bezels = Bezels.FOLDABLE_INNER,
        physicalKeys = PhysicalKey.standardAndroidKeys(485f),
        statusBarHeightDp = 24,
        chassisColor = Color(0xFF1A1A1A),
    )

    private val galaxy_z_fold5_outer = DeviceProfile(
        id = "galaxy-z-fold5-outer",
        displayName = "Galaxy Z Fold 5 (Outer)",
        manufacturer = "Samsung", model = "Galaxy Z Fold 5 (Outer)", osVersion = "Android 14",
        formFactor = FormFactor.FOLDABLE_OUTER,
        widthPx = 904, heightPx = 2316, densityDpi = 374,
        cornerRadiusDp = 6f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_CENTER, insetDp = 6f),
        bezels = Bezels.FOLDABLE_OUTER,
        physicalKeys = PhysicalKey.standardAndroidKeys(242f),
        chassisColor = Color(0xFF1A1A1A),
    )

    private val pixel_fold_inner = DeviceProfile(
        id = "pixel-fold-inner",
        displayName = "Pixel Fold (Inner)",
        manufacturer = "Google", model = "Pixel Fold (Inner)", osVersion = "Android 14",
        formFactor = FormFactor.FOLDABLE_INNER,
        widthPx = 2208, heightPx = 1840, densityDpi = 420,
        cornerRadiusDp = 8f,
        cutout = null,
        bezels = Bezels.FOLDABLE_INNER,
        physicalKeys = PhysicalKey.standardAndroidKeys(673f),
        statusBarHeightDp = 24,
        chassisColor = Color(0xFF1A1A1A),
    )

    private val pixel_fold_outer = DeviceProfile(
        id = "pixel-fold-outer",
        displayName = "Pixel Fold (Outer)",
        manufacturer = "Google", model = "Pixel Fold (Outer)", osVersion = "Android 14",
        formFactor = FormFactor.FOLDABLE_OUTER,
        widthPx = 1080, heightPx = 2092, densityDpi = 420,
        cornerRadiusDp = 12f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_LEFT, insetDp = 8f),
        bezels = Bezels.FOLDABLE_OUTER,
        physicalKeys = PhysicalKey.standardAndroidKeys(412f),
        chassisColor = Color(0xFF1F1F22),
    )

    private val oppo_find_n3_inner = DeviceProfile(
        id = "oppo-find-n3-inner",
        displayName = "OPPO Find N3 (Inner)",
        manufacturer = "OPPO", model = "Find N3 (Inner)", osVersion = "ColorOS 13",
        formFactor = FormFactor.FOLDABLE_INNER,
        widthPx = 2268, heightPx = 2240, densityDpi = 431,
        cornerRadiusDp = 6f,
        cutout = null,
        bezels = Bezels.FOLDABLE_INNER,
        statusBarHeightDp = 24,
        chassisColor = Color(0xFF1A1A1A),
    )

    private val oppo_find_n3_outer = DeviceProfile(
        id = "oppo-find-n3-outer",
        displayName = "OPPO Find N3 (Outer)",
        manufacturer = "OPPO", model = "Find N3 (Outer)", osVersion = "ColorOS 13",
        formFactor = FormFactor.FOLDABLE_OUTER,
        widthPx = 1116, heightPx = 2484, densityDpi = 431,
        cornerRadiusDp = 14f,
        cutout = CutoutGeometry.PunchHole(diameterDp = 4f, anchor = CutoutGeometry.Anchor.TOP_LEFT, insetDp = 6f),
        bezels = Bezels.FOLDABLE_OUTER,
        chassisColor = Color(0xFF1F1F22),
    )

    // === Tablet ===

    private val pixel_tablet = DeviceProfile(
        id = "pixel-tablet",
        displayName = "Pixel Tablet",
        manufacturer = "Google", model = "Pixel Tablet", osVersion = "Android 14",
        formFactor = FormFactor.TABLET,
        widthPx = 1600, heightPx = 2560, densityDpi = 320,
        cornerRadiusDp = 18f,
        bezels = Bezels.TABLET,
        statusBarHeightDp = 24,
        chassisColor = Color(0xFFEEEAE3),
    )

    private val ipad_pro_11 = DeviceProfile(
        id = "ipad-pro-11",
        displayName = "iPad Pro 11\"",
        manufacturer = "Apple", model = "iPad Pro 11", osVersion = "iPadOS 17",
        formFactor = FormFactor.TABLET,
        widthPx = 1668, heightPx = 2388, densityDpi = 264,
        cornerRadiusDp = 22f,
        bezels = Bezels(topDp = 22f, bottomDp = 22f, leftDp = 22f, rightDp = 22f),
        statusBarHeightDp = 24,
        chassisColor = Color(0xFF2C2C2E),
    )

    private val ipad_pro_12_9 = DeviceProfile(
        id = "ipad-pro-12.9",
        displayName = "iPad Pro 12.9\"",
        manufacturer = "Apple", model = "iPad Pro 12.9", osVersion = "iPadOS 17",
        formFactor = FormFactor.TABLET,
        widthPx = 2048, heightPx = 2732, densityDpi = 264,
        cornerRadiusDp = 22f,
        bezels = Bezels(topDp = 22f, bottomDp = 22f, leftDp = 22f, rightDp = 22f),
        statusBarHeightDp = 24,
        chassisColor = Color(0xFF2C2C2E),
    )

    private val galaxy_tab_s9 = DeviceProfile(
        id = "galaxy-tab-s9",
        displayName = "Galaxy Tab S9",
        manufacturer = "Samsung", model = "Galaxy Tab S9", osVersion = "Android 14",
        formFactor = FormFactor.TABLET,
        widthPx = 2560, heightPx = 1600, densityDpi = 274,
        cornerRadiusDp = 16f,
        bezels = Bezels.TABLET,
        statusBarHeightDp = 24,
        chassisColor = Color(0xFF353535),
    )

    // === Watch ===

    private val wear_small = DeviceProfile(
        id = "wear-small",
        displayName = "Wear OS Small (384×384)",
        manufacturer = "Google", model = "Wear OS Small", osVersion = "Wear OS 4",
        formFactor = FormFactor.WATCH,
        widthPx = 384, heightPx = 384, densityDpi = 320,
        cornerRadiusDp = 192f,
        bezels = Bezels.WATCH,
        statusBarHeightDp = 0,
        navigationBarHeightDp = 0,
        chassisColor = Color(0xFF1A1A1A),
    )

    private val wear_large = DeviceProfile(
        id = "wear-large",
        displayName = "Wear OS Large (454×454)",
        manufacturer = "Google", model = "Wear OS Large", osVersion = "Wear OS 4",
        formFactor = FormFactor.WATCH,
        widthPx = 454, heightPx = 454, densityDpi = 320,
        cornerRadiusDp = 227f,
        bezels = Bezels.WATCH,
        statusBarHeightDp = 0,
        navigationBarHeightDp = 0,
        chassisColor = Color(0xFF1A1A1A),
    )

    private val wear_square = DeviceProfile(
        id = "wear-square",
        displayName = "Wear OS Square (390×390)",
        manufacturer = "Google", model = "Wear OS Square", osVersion = "Wear OS 4",
        formFactor = FormFactor.WATCH,
        widthPx = 390, heightPx = 390, densityDpi = 320,
        cornerRadiusDp = 32f,
        bezels = Bezels(topDp = 32f, bottomDp = 32f, leftDp = 32f, rightDp = 32f),
        statusBarHeightDp = 0,
        navigationBarHeightDp = 0,
        chassisColor = Color(0xFF1A1A1A),
    )

    // === Desktop / Auto ===

    private val desktop_1080p = DeviceProfile(
        id = "desktop-1080p",
        displayName = "Desktop 1920×1080",
        manufacturer = "Generic", model = "Desktop 1080p", osVersion = "Android 14 Desktop",
        formFactor = FormFactor.DESKTOP,
        widthPx = 1920, heightPx = 1080, densityDpi = 160,
        cornerRadiusDp = 4f,
        bezels = Bezels(0f, 0f, 0f, 0f),
        statusBarHeightDp = 28,
        navigationBarHeightDp = 0,
        chassisColor = Color(0xFF202024),
    )

    /**
     * 全部内置设备 (按 formFactor 排序).
     */
    val builtinProfiles: List<DeviceProfile> = listOf(
        // Phone
        pixel_7, pixel_7_pro, pixel_8, pixel_8_pro,
        huawei_mate60_pro, huawei_p30_pro, xiaomi_14_pro,
        samsung_s23, samsung_s24_ultra,
        oneplus_12, honor_magic6_pro,
        // iPhone
        iphone_13, iphone_14, iphone_15_pro, iphone_15_pro_max,
        // Foldable
        galaxy_z_fold5_inner, galaxy_z_fold5_outer,
        pixel_fold_inner, pixel_fold_outer,
        oppo_find_n3_inner, oppo_find_n3_outer,
        // Tablet
        pixel_tablet, ipad_pro_11, ipad_pro_12_9, galaxy_tab_s9,
        // Watch
        wear_small, wear_large, wear_square,
        // Desktop
        desktop_1080p,
    )

    /**
     * 按 [FormFactor] 分组的字典.
     *
     * 顺序: PHONE → FOLDABLE_OUTER → FOLDABLE_INNER → TABLET → WATCH → DESKTOP.
     */
    val groupedByFormFactor: Map<FormFactor, List<DeviceProfile>> = mapOf(
        FormFactor.PHONE to builtinProfiles.filter { it.formFactor == FormFactor.PHONE },
        FormFactor.FOLDABLE_OUTER to builtinProfiles.filter { it.formFactor == FormFactor.FOLDABLE_OUTER },
        FormFactor.FOLDABLE_INNER to builtinProfiles.filter { it.formFactor == FormFactor.FOLDABLE_INNER },
        FormFactor.TABLET to builtinProfiles.filter { it.formFactor == FormFactor.TABLET },
        FormFactor.WATCH to builtinProfiles.filter { it.formFactor == FormFactor.WATCH },
        FormFactor.DESKTOP to builtinProfiles.filter { it.formFactor == FormFactor.DESKTOP },
    )

    /**
     * 按 ID 查找设备.
     *
     * @return 命中返回 profile, 否则 null.
     */
    fun byId(id: String): DeviceProfile? = builtinProfiles.firstOrNull { it.id == id }

    /**
     * 按 [FormFactor] 查找设备.
     */
    fun byFormFactor(ff: FormFactor): List<DeviceProfile> =
        groupedByFormFactor[ff] ?: emptyList()

    /**
     * 默认设备 (Pixel 7), 当用户未选择时使用.
     */
    val DEFAULT: DeviceProfile = pixel_7
}
