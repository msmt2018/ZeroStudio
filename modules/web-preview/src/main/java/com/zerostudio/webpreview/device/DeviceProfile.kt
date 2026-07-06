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
 * 设备分类。
 */
enum class DeviceCategory {
    /** 手机 (竖屏 / 小屏)。 */
    PHONE,

    /** 平板 (大屏)。 */
    TABLET,

    /** 桌面 (无触屏)。 */
    DESKTOP,
}

/**
 * 预置设备参数。
 *
 * 切换设备时, WebPreviewEngine 会把 [userAgent] 设到 WebSettings,
 * 把 [viewportWidth]/[viewportHeight] 作为 WebView 渲染视口,
 * 把 [devicePixelRatio] 作为 CSS DPR, 并注入 viewport meta。
 *
 * WebView 会真实重渲染并真实发起所有网络请求 (含 POST)。
 *
 * @property name 设备显示名 (如 "iPhone 14 Pro Max")
 * @property category 设备分类
 * @property userAgent 完整 User-Agent 字符串
 * @property viewportWidth CSS 视口宽度 (px)
 * @property viewportHeight CSS 视口高度 (px)
 * @property devicePixelRatio 设备像素比 (1.0 / 2.0 / 3.0)
 * @property isMobile 是否移动端 UA (影响某些站点的响应式分支)
 * @property hasTouch 是否支持触屏事件
 */
data class DeviceProfile(
    val name: String,
    val category: DeviceCategory,
    val userAgent: String,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val devicePixelRatio: Float,
    val isMobile: Boolean,
    val hasTouch: Boolean,
)
