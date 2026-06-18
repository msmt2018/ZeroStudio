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

/**
 * 桌面应用图标数据 (PR-C 桌面 launcher 模拟).
 *
 * - [id] 唯一标识 (用 packageName 或自定义)
 * - [label] 显示名
 * - [packageName] Android 包名 (系统应用为 null)
 * - [iconResName] icon drawable 资源名 (来自 res/drawable 或 mipmap).
 *                null = 用默认占位 icon (Android 机器人)
 * - [iconResId] 已解析的 resource id (运行时从 AndroidModule 拿)
 * - [isClickable] true=点击进入 app (在桌面模拟里启动 preview),
 *                false=占位不可点击 (模拟系统 app 不响应, 用户要求 1.1)
 */
data class DesktopApp(
    val id: String,
    val label: String,
    val packageName: String? = null,
    val iconResName: String? = null,
    val iconResId: Int = 0,
    val isClickable: Boolean = true,
) {
    /** 系统应用 (不可点击) 用预定义枚举代替. */
    companion object {
        // 系统应用占位 — PR-C 第一批, 后续可扩展.
        val SETTINGS = DesktopApp(
            id = "system.settings",
            label = "Settings",
            packageName = "com.android.settings",
            isClickable = false,
        )
        val PHONE = DesktopApp(
            id = "system.phone",
            label = "Phone",
            packageName = "com.android.dialer",
            isClickable = false,
        )
        val CAMERA = DesktopApp(
            id = "system.camera",
            label = "Camera",
            packageName = "com.android.camera",
            isClickable = false,
        )
        val MESSAGES = DesktopApp(
            id = "system.messages",
            label = "Messages",
            packageName = "com.android.mms",
            isClickable = false,
        )
        val CLOCK = DesktopApp(
            id = "system.clock",
            label = "Clock",
            packageName = "com.android.deskclock",
            isClickable = false,
        )

        /** 默认桌面应用列表 (系统应用占位). 真实项目 app 由 ViewModel.addUserApp() 加入. */
        val DEFAULT_SYSTEM_APPS: List<DesktopApp> = listOf(
            SETTINGS, PHONE, CAMERA, MESSAGES, CLOCK,
        )
    }
}
