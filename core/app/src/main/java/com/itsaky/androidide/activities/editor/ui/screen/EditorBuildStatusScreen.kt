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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * `layout_editor_build_status.xml` 的 Jetpack Compose 一比一复刻。
 *
 * 结构映射 (XML → Compose):
 * - `ConstraintLayout` (match_parent × 14dp) → 固定 14dp 高的 [Box]
 * - `statusText` (0dp×match_parent, minHeight=14dp, paddingStart/End=16dp,
 *   gravity=center, maxLines=1, textAppearance=BodyMedium)
 *   → 居中 [Text] + bodyMedium
 *
 * XML 未设置静态文本 (tools:text="Configure project :app" 仅预览用); 宿主通过
 * [statusText] 注入运行时状态 (对应 EditorBottomSheet.setStatus())。
 */
@Composable
fun EditorBuildStatusScreen(
    modifier: Modifier = Modifier,
    statusText: String = "",
) {
  // ConstraintLayout: layout_height=14dp
  Box(
      modifier = modifier.fillMaxWidth().height(14.dp),
      contentAlignment = Alignment.Center) {
        // statusText: gravity=center, maxLines=1, BodyMedium
        Text(
            text = statusText,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1)
      }
}
