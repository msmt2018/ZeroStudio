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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.R

/**
 * `layout_editor_file_tree.xml` 的 Jetpack Compose 一比一复刻。
 *
 * 结构映射 (XML → Compose):
 * - 根 `LinearLayout` (vertical, paddingStart/End=16dp, paddingBottom=16dp)
 * - `git_status_bar` (横向, marginTop=4dp, marginBottom=6dp, gravity=center_vertical,
 *   background=selectableItemBackground, paddingV=4dp):
 *   - `ImageView` (@drawable/ic_git, 20dp, contentDescription=@string/git_action_branch_switch)
 *   - `git_status_branch` (weight=1, marginStart=8dp, 13sp bold, maxLines=1, ellipsize=end)
 *   - `git_status_pull` (ImageButton 36dp, @drawable/ic_git_pull, borderless 涟漪)
 *   - `git_status_push` (ImageButton 36dp, @drawable/ic_git_push, borderless 涟漪)
 * - `RelativeLayout`:
 *   - `horizontal_croll` (HorizontalScrollView match/match, fillViewport=true)
 *     → [fileTree] 内容槽 + horizontalScroll
 *   - `loading` (ProgressBar, centerInParent) → [isLoading] 时居中的
 *     [CircularProgressIndicator]
 */
@Composable
fun EditorFileTreeScreen(
    modifier: Modifier = Modifier,
    branchStatus: String = "",
    onBranchClick: () -> Unit = {},
    onPullClick: () -> Unit = {},
    onPushClick: () -> Unit = {},
    isLoading: Boolean = false,
    fileTree: @Composable () -> Unit = {},
) {
  // 根 LinearLayout(vertical, paddingStart/End=16dp, paddingBottom=16dp)
  Column(
      modifier =
          modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        // git_status_bar: 顶部 git 状态栏 (分支 + ahead/behind + 改动数 + Pull/Push 快捷按钮)
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp)
                    .clickable(onClick = onBranchClick) // background=selectableItemBackground
                    .padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
              // ImageView: @drawable/ic_git, 20dp
              Image(
                  painter = painterResource(R.drawable.ic_git),
                  contentDescription = stringResource(R.string.git_action_branch_switch),
                  modifier = Modifier.size(20.dp))

              // git_status_branch: 分支状态文本 (weight=1, 13sp bold, 单行省略)
              Text(
                  text = branchStatus,
                  modifier = Modifier.weight(1f).padding(start = 8.dp),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis)

              // git_status_pull: ImageButton 36dp (selectableItemBackgroundBorderless)
              IconButton(onClick = onPullClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_git_pull),
                    contentDescription = stringResource(R.string.git_action_pull))
              }

              // git_status_push: ImageButton 36dp (selectableItemBackgroundBorderless)
              IconButton(onClick = onPushClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_git_push),
                    contentDescription = stringResource(R.string.git_action_push))
              }
            }

        // RelativeLayout: 文件树横向滚动区 + 居中 loading
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          // horizontal_croll: HorizontalScrollView(match/match, fillViewport=true)
          Box(
              modifier =
                  Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                fileTree()
              }

          // loading: ProgressBar(centerInParent)
          if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
        }
      }
}
