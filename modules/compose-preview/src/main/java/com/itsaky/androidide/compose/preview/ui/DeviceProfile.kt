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
import androidx.compose.ui.graphics.Color
import com.itsaky.androidide.compose.preview.data.device.CutoutGeometry
import com.itsaky.androidide.compose.preview.data.device.PhysicalKey

/**
 * 设备 / 分辨率 配置 v2.1.
 *
 * 描述一台真实设备 (Pixel / Huawei / iPhone / Foldable / Tablet / Watch)
 * 在预览中如何被**视觉还原**:
 *
 * - 屏幕像素尺寸 + DPI → 计算 widthDp / heightDp
 * - 形状 (圆角矩形 / 圆表 / 瀑布 / 折叠) → 决定 [DeviceFrame] 的裁切
 * - 切口 (刘海 / 针孔 / 瀑布) → 叠加 [CutoutOverlay]
 * - 边框 (上 / 下 / 左 / 右 dp) → 决定机身外壳
 * - 物理按键 → 在屏幕外绘制矩形
 * - 状态栏 / 导航栏 高度 → 模拟系统栏占用空间
 *
 * **保留** v2 旧字段 (widthPx / heightPx / densityDpi / frameStyle / isCustom),
 * 这样 ComposePreviewFragment / ComposePreviewActivity 等旧入口不需要立刻全部迁移.
 *
 * @property id 唯一 ID, 用于 SharedPreferences / Catalog 查询
 * @property manufacturer 厂商 ("Google" / "Huawei" / "Samsung" / "Apple" / ...)
 * @property model 型号 ("Pixel 7 Pro" / "Galaxy Z Fold 5")
 * @property osVersion 系统版本字符串 ("Android 14" / "iOS 17")
 * @property formFactor 形态因子 (决定 [DeviceFrame] 渲染策略)
 * @property widthPx 屏幕宽 (px)
 * @property heightPx 屏幕高 (px)
 * @property densityDpi 屏幕 DPI
 * @property cornerRadiusDp 屏幕圆角
 * @property cutout 切口几何 (刘海 / 针孔 / 瀑布); null = 无切口
 * @property bezels 边框 (上下左右 dp)
 * @property chassisColor 机身颜色
 * @property physicalKeys 物理按键列表
 * @property statusBarHeightDp 状态栏高度
 * @property navigationBarHeightDp 导航栏高度
 * @property frameStyle 旧字段, 由 [formFactor] 推导 (兼容)
 * @property isCustom 是否为用户自定义
 */
@Immutable
data class DeviceProfile(
    val id: String,
    val displayName: String,
    val manufacturer: String = "Generic",
    val model: String = displayName,
    val osVersion: String = "Android 14",
    val formFactor: FormFactor = FormFactor.PHONE,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val cornerRadiusDp: Float = 28f,
    val cutout: CutoutGeometry? = null,
    val bezels: Bezels = Bezels(),
    val chassisColor: Color = Color(0xFF1A1A1F),
    val physicalKeys: List<PhysicalKey> = PhysicalKey.NO_KEYS,
    val statusBarHeightDp: Int = 24,
    val navigationBarHeightDp: Int = 48,
    val frameStyle: FrameStyle = formFactor.toFrameStyle(),
    val isCustom: Boolean = false,
) {

    /** width in dp. */
    val widthDp: Float get() = widthPx * 160f / densityDpi

    /** height in dp. */
    val heightDp: Float get() = heightPx * 160f / densityDpi

    val aspectRatio: Float get() = widthPx.toFloat() / heightPx

    /** 是否圆表 (Watch) */
    val isRound: Boolean get() = formFactor == FormFactor.WATCH

    /** 是否包含刘海 */
    val hasNotch: Boolean get() = cutout is CutoutGeometry.Notch

    /** 是否包含针孔 */
    val hasPunchHole: Boolean get() = cutout is CutoutGeometry.PunchHole

    /** 是否瀑布屏 */
    val hasWaterfall: Boolean get() = cutout is CutoutGeometry.WaterfallCurve

    /** 是否折叠屏 (内屏 / 外屏) */
    val isFoldable: Boolean
        get() = formFactor == FormFactor.FOLDABLE_INNER || formFactor == FormFactor.FOLDABLE_OUTER

    /** 是否为外屏 (foldable outer) */
    val isFoldableOuter: Boolean get() = formFactor == FormFactor.FOLDABLE_OUTER

    /** 屏幕对角线 (英寸, 用于 UI 显示) */
    val diagonalInches: Float
        get() {
            val widthIn = widthPx / densityDpi.toFloat()
            val heightIn = heightPx / densityDpi.toFloat()
            return kotlin.math.sqrt(widthIn * widthIn + heightIn * heightIn)
        }

    /** v2 兼容字段. 由 [formFactor] 推导. */
    enum class FrameStyle { PHONE, TABLET, FOLDABLE, WATCH, NONE }

    /** v2.1 设备形态. 决定 [DeviceFrame] 渲染策略. */
    enum class FormFactor {
        /** 传统 16:9 ~ 21:9 全面屏手机 */
        PHONE,

        /** 折叠屏内屏 (展开后) - 大屏, 无 cutout */
        FOLDABLE_INNER,

        /** 折叠屏外屏 (折叠后) - 小屏, 有 cutout */
        FOLDABLE_OUTER,

        /** 平板 (10"~13") */
        TABLET,

        /** Wear OS 圆形手表 */
        WATCH,

        /** 桌面 / 自由窗口模式 */
        DESKTOP,

        /** 不显示外壳 (透明背景) - 用于自定义测试 */
        NONE;

        internal fun toFrameStyle(): FrameStyle = when (this) {
            PHONE -> FrameStyle.PHONE
            FOLDABLE_INNER, FOLDABLE_OUTER -> FrameStyle.FOLDABLE
            TABLET -> FrameStyle.TABLET
            WATCH -> FrameStyle.WATCH
            DESKTOP -> FrameStyle.NONE
            NONE -> FrameStyle.NONE
        }
    }

    /**
     * 设备边框 (上 / 下 / 左 / 右 dp).
     *
     * 用于 [DeviceFrame] 渲染机身外壳的厚度. 现代全面屏手机
     * 上边框通常 4~6dp, 下边框 8~10dp (含 home indicator 区域).
     *
     * @property topDp 顶部边框
     * @property bottomDp 底部边框
     * @property leftDp 左侧边框
     * @property rightDp 右侧边框
     */
    @Immutable
    data class Bezels(
        val topDp: Float = 4f,
        val bottomDp: Float = 8f,
        val leftDp: Float = 4f,
        val rightDp: Float = 4f,
    ) {
        companion object {
            /** 现代全面屏手机 (Pixel 7) */
            val PHONE_MODERN: Bezels = Bezels(topDp = 4f, bottomDp = 8f, leftDp = 4f, rightDp = 4f)
            /** 传统 16:9 手机 (顶部摄像头 / 听筒) */
            val PHONE_LEGACY: Bezels = Bezels(topDp = 24f, bottomDp = 16f, leftDp = 4f, rightDp = 4f)
            /** 平板 */
            val TABLET: Bezels = Bezels(topDp = 16f, bottomDp = 16f, leftDp = 16f, rightDp = 16f)
            /** 折叠屏内屏 (极窄边框) */
            val FOLDABLE_INNER: Bezels = Bezels(topDp = 2f, bottomDp = 2f, leftDp = 2f, rightDp = 2f)
            /** 折叠屏外屏 (类似传统手机) */
            val FOLDABLE_OUTER: Bezels = Bezels(topDp = 4f, bottomDp = 8f, leftDp = 4f, rightDp = 4f)
            /** 手表 (无边框, 圆形) */
            val WATCH: Bezels = Bezels(0f, 0f, 0f, 0f)
        }
    }
}

/**
 * 内置常用设备 (v2 兼容) + v2.1 扩展.
 *
 * 详细 30+ 真实设备清单见 [com.itsaky.androidide.compose.preview.data.device.DeviceCatalog].
 */
object DeviceProfiles {

    // === v2 旧 Profile (保留, 旧入口仍可用) ===

    val PIXEL_4 = DeviceProfile(
        id = "pixel-4",
        displayName = "Pixel 4",
        widthPx = 1080, heightPx = 2280, densityDpi = 440,
        cornerRadiusDp = 24f,
        bezels = DeviceProfile.Bezels.PHONE_LEGACY,
        statusBarHeightDp = 28,
    )

    val PIXEL_5 = DeviceProfile(
        id = "pixel-5",
        displayName = "Pixel 5",
        widthPx = 1080, heightPx = 2340, densityDpi = 440,
        cornerRadiusDp = 28f,
        bezels = DeviceProfile.Bezels.PHONE_MODERN,
        cutout = CutoutGeometry.PIXEL_PUNCHHOLE,
    )

    val PIXEL_6 = DeviceProfile(
        id = "pixel-6",
        displayName = "Pixel 6",
        widthPx = 1080, heightPx = 2400, densityDpi = 420,
        cornerRadiusDp = 28f,
        bezels = DeviceProfile.Bezels.PHONE_MODERN,
        cutout = CutoutGeometry.PIXEL_PUNCHHOLE,
    )

    val PIXEL_7 = DeviceProfile(
        id = "pixel-7",
        displayName = "Pixel 7",
        widthPx = 1080, heightPx = 2400, densityDpi = 420,
        cornerRadiusDp = 28f,
        bezels = DeviceProfile.Bezels.PHONE_MODERN,
        cutout = CutoutGeometry.PIXEL_PUNCHHOLE,
    )

    val PIXEL_TABLET = DeviceProfile(
        id = "pixel-tablet",
        displayName = "Pixel Tablet",
        widthPx = 1600, heightPx = 2560, densityDpi = 320,
        cornerRadiusDp = 16f,
        formFactor = DeviceProfile.FormFactor.TABLET,
        bezels = DeviceProfile.Bezels.TABLET,
        statusBarHeightDp = 24,
    )

    val FOLDABLE_INNER = DeviceProfile(
        id = "foldable-inner",
        displayName = "Foldable (Inner)",
        widthPx = 2208, heightPx = 1840, densityDpi = 420,
        cornerRadiusDp = 4f,
        formFactor = DeviceProfile.FormFactor.FOLDABLE_INNER,
        bezels = DeviceProfile.Bezels.FOLDABLE_INNER,
        statusBarHeightDp = 24,
    )

    val WEAR_OS_SMALL = DeviceProfile(
        id = "wear-small",
        displayName = "Wear OS Small",
        widthPx = 384, heightPx = 384, densityDpi = 320,
        cornerRadiusDp = 192f,
        formFactor = DeviceProfile.FormFactor.WATCH,
        bezels = DeviceProfile.Bezels.WATCH,
        statusBarHeightDp = 0,
        navigationBarHeightDp = 0,
    )

    val WEAR_OS_LARGE = DeviceProfile(
        id = "wear-large",
        displayName = "Wear OS Large",
        widthPx = 454, heightPx = 454, densityDpi = 320,
        cornerRadiusDp = 227f,
        formFactor = DeviceProfile.FormFactor.WATCH,
        bezels = DeviceProfile.Bezels.WATCH,
        statusBarHeightDp = 0,
        navigationBarHeightDp = 0,
    )

    /** v2 旧内置 profile 列表. */
    val builtins: List<DeviceProfile> = listOf(
        PIXEL_4, PIXEL_5, PIXEL_6, PIXEL_7,
        PIXEL_TABLET, FOLDABLE_INNER,
        WEAR_OS_SMALL, WEAR_OS_LARGE
    )

    /**
     * 按 id 查找. 空 id 视为 Pixel 6 (默认).
     *
     * 注意: 仅在 v2 旧入口中调用. v2.1 新入口应改用
     * [com.itsaky.androidide.compose.preview.data.device.DeviceCatalog.byId].
     */
    fun findById(id: String): DeviceProfile? =
        if (id.isBlank()) PIXEL_6 else builtins.firstOrNull { it.id == id }
}
