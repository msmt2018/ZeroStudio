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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.R

/**
 * 搜索模块勾选项。
 *
 * 对应 XML 中 [com.itsaky.androidide.activities.editor.ProjectHandlerActivity]
 * 运行时往 `modules_container` 里动态 addView 的 CheckBox (text=模块名, 默认勾选)。
 */
data class SearchModule(val name: String, val checked: Boolean = true)

/**
 * `layout_search_project.xml` 的 Jetpack Compose 一比一复刻。
 *
 * 结构映射 (XML → Compose):
 * - `NestedScrollView` (match/match, background=?attr/colorSurface, fillViewport=true)
 *   → 带 `verticalScroll` 的 `Column` + `background(colorScheme.surface)`
 * - `LinearLayout` (vertical, paddingTop/Bottom=12dp) → 同一 Column 的 padding
 * - `TextView` @string/msg_search_modules (marginStart/End=24dp, marginBottom=8dp)
 * - `modules_container` (LinearLayout vertical, marginStart/End=24dp)
 *   → [modules] 列表渲染出的复选框行 (CheckBox bottomMargin=4dp)
 * - `input` (TextInputLayout OutlinedBox.Dense, hint=@string/text_to_search,
 *   startIconDrawable=@drawable/ic_search, marginStart/End=8dp)
 *   → [OutlinedTextField] + label + leadingIcon
 * - `filter` (TextInputLayout OutlinedBox.Dense, hint=@string/hint_find_project_filter,
 *   helperText=@string/msg_find_project_filter, startIconDrawable=@drawable/ic_filter)
 *   → [OutlinedTextField] + label + supportingText + leadingIcon
 */
@Composable
fun SearchProjectScreen(
    modifier: Modifier = Modifier,
    modules: List<SearchModule> = emptyList(),
    onModuleCheckedChange: (SearchModule, Boolean) -> Unit = { _, _ -> },
    searchText: String = "",
    onSearchTextChange: (String) -> Unit = {},
    filterText: String = "",
    onFilterTextChange: (String) -> Unit = {},
) {
  // NestedScrollView + LinearLayout(vertical, paddingTop/Bottom=12dp)
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surface)
              .verticalScroll(rememberScrollState())
              .padding(top = 12.dp, bottom = 12.dp)) {
        // TextView @string/msg_search_modules
        Text(
            text = stringResource(R.string.msg_search_modules),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))

        // modules_container: 运行时动态填充的模块 CheckBox 列表
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp)) {
              modules.forEach { module ->
                // CheckBox(text=module.name, isChecked=true) + bottomMargin=4dp
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .toggleable(
                                value = module.checked,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                  onModuleCheckedChange(module, checked)
                                }),
                    verticalAlignment = Alignment.CenterVertically) {
                      Checkbox(checked = module.checked, onCheckedChange = null)
                      Text(text = module.name)
                    }
              }
            }

        // input: TextInputLayout(hint=text_to_search, startIcon=ic_search)
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
            label = { Text(stringResource(R.string.text_to_search)) },
            leadingIcon = {
              Icon(
                  painter = painterResource(R.drawable.ic_search),
                  contentDescription = null)
            })

        // filter: TextInputLayout(hint=hint_find_project_filter,
        //                         helperText=msg_find_project_filter, startIcon=ic_filter)
        OutlinedTextField(
            value = filterText,
            onValueChange = onFilterTextChange,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
            label = { Text(stringResource(R.string.hint_find_project_filter)) },
            supportingText = { Text(stringResource(R.string.msg_find_project_filter)) },
            leadingIcon = {
              Icon(
                  painter = painterResource(R.drawable.ic_filter),
                  contentDescription = null)
            })
      }
}
