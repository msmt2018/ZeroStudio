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
 *  along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.activities.editor.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `layout_diagnostic_info.xml` 的 Jetpack Compose 一比一复刻。
 *
 * 结构映射 (XML → Compose):
 * - `TextView` (id/msg, wrap_content, padding=8dp,
 *   textColor=?attr/colorOnPrimaryContainer, textIsSelectable=true, textSize=11sp)
 *   → [SelectionContainer] (textIsSelectable) 内的 [Text]
 *
 * 默认文案对应 XML tools:text="';' expected" — 诊断信息由宿主通过 [message] 注入。
 */
@Composable
fun DiagnosticInfoScreen(
    modifier: Modifier = Modifier,
    message: String = "",
) {
  // textIsSelectable=true
  SelectionContainer {
    // TextView: padding=8dp, colorOnPrimaryContainer, 11sp
    Text(
        text = message,
        modifier = modifier.padding(8.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontSize = 11.sp)
  }
}
