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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 主题切换器 (Light / Dark / Custom).
 *
 * 与 [DeviceProfileSheet] 不同, 这个组件是常驻在工具栏里的 chip 组, 切换立即生效.
 *
 * 注意: Custom 主题的具体配色由上层接 [colorSchemeOverride] 注入; 切换时上层应该
 * 重新计算 `MaterialTheme(colorScheme = ...)` 包住 [DeviceFrame] 的 content.
 */
enum class PreviewTheme { LIGHT, DARK, CUSTOM }

@Composable
fun ThemeSelector(
    selected: PreviewTheme,
    onSelect: (PreviewTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = selected == PreviewTheme.LIGHT,
            onClick = { onSelect(PreviewTheme.LIGHT) },
            label = { Text("Light") }
        )
        FilterChip(
            selected = selected == PreviewTheme.DARK,
            onClick = { onSelect(PreviewTheme.DARK) },
            label = { Text("Dark") }
        )
        FilterChip(
            selected = selected == PreviewTheme.CUSTOM,
            onClick = { onSelect(PreviewTheme.CUSTOM) },
            label = { Text("Custom") }
        )
    }
}
