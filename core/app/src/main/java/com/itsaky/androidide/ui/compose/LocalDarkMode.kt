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

package com.itsaky.androidide.ui.compose

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 全局暗色模式状态 (CompositionLocal)。
 *
 * core/app 没有像 chatai 模块那样的 `RikkahubTheme` + `LocalDarkMode` 包装,
 * 但 `AppCompatDelegate.setDefaultNightMode` 设置的夜间模式 Compose 无法直接感知。
 * 本 [CompositionLocal] 解析当前夜间模式状态, 供磨砂玻璃控件 / 图标 tint 等使用。
 *
 * 使用方式:
 * ```
 * ProvideDarkMode {
 *     // 内部 LocalDarkMode.current 可用
 *     FrostedControlBar(...) { ... }
 * }
 * ```
 */
val LocalDarkMode = compositionLocalOf<Boolean> { error("LocalDarkMode not provided, wrap with ProvideDarkMode {}") }

/**
 * 提供 [LocalDarkMode] 的 Composable 包装。
 *
 * 监听 [LocalConfiguration] 的 uiMode 变化, 主题切换时自动更新暗色状态。
 */
@Composable
fun ProvideDarkMode(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val isDark = remember(configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        val mode = AppCompatDelegate.getDefaultNightMode()
        when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }
    CompositionLocalProvider(LocalDarkMode provides isDark, content = content)
}
