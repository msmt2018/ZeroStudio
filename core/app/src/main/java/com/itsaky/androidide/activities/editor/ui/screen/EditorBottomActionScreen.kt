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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.R

/**
 * `layout_editor_bottom_action.xml` 的 Jetpack Compose 一比一复刻。
 *
 * 结构映射 (XML → Compose):
 * - `ConstraintLayout` (match_parent × 14dp, tools:layout_height=56dp — 运行时动态调高)
 *   → 固定 14dp 高的 [Column], 宿主可通过 modifier 覆盖高度
 * - `progress` (LinearProgressIndicator, 0dp 宽贴满, indeterminate=false, max=100)
 *   → [LinearProgressIndicator] + progress=actionProgress/100f
 * - `action_text` (0dp 宽, marginStart/End=16dp, marginV=0.2dp,
 *   textAppearance=BodyLarge, 位于 progress 下方)
 *   → [Text] + bodyLarge
 *
 * 宿主通过 [actionText]/[actionProgress] 注入运行时状态
 * (对应 EditorBottomSheet.setActionText()/setActionProgress())。
 */
@Composable
fun EditorBottomActionScreen(
    modifier: Modifier = Modifier,
    actionText: String = stringResource(R.string.msg_installing_apk),
    actionProgress: Int = 0,
) {
  // ConstraintLayout: layout_height=14dp (运行时动态调整, tools 预览为 56dp)
  Column(modifier = modifier.fillMaxWidth().height(14.dp)) {
    // progress: LinearProgressIndicator(indeterminate=false, max=100)
    LinearProgressIndicator(
        progress = { actionProgress.coerceIn(0, 100) / 100f },
        modifier = Modifier.fillMaxWidth())

    // action_text: BodyLarge, marginStart/End=16dp, marginV=0.2dp
    Text(
        text = actionText,
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = 16.dp, end = 16.dp, top = 0.2.dp, bottom = 0.2.dp),
        style = MaterialTheme.typography.bodyLarge)
  }
}
