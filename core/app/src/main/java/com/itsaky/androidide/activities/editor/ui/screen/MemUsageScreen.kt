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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.itsaky.androidide.R

/**
 * `layout_mem_usage.xml` 的 Jetpack Compose 一比一复刻。
 *
 * 结构映射 (XML → Compose):
 * - `ConstraintLayout` (match_parent × @dimen/editor_mem_usage_view_height[200dp])
 *   → 固定高度的 [Box]
 * - `LineChart` (id/chart, 0dp×0dp 四边约束到 parent) → [AndroidView] 互操作包装的
 *   MPAndroidChart [LineChart], 通过 [onChartCreated] 暴露给宿主配置
 *   (对应 BaseEditorActivity.setupMemUsageChart() 的初始化入口)。
 */
@Composable
fun MemUsageScreen(
    modifier: Modifier = Modifier,
    onChartCreated: (LineChart) -> Unit = {},
) {
  // ConstraintLayout: layout_height=@dimen/editor_mem_usage_view_height
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .height(dimensionResource(R.dimen.editor_mem_usage_view_height))) {
        // LineChart: 四边约束到 parent → fillMaxSize
        AndroidView(
            factory = { context -> LineChart(context).also(onChartCreated) },
            modifier = Modifier.fillMaxSize())
      }
}
