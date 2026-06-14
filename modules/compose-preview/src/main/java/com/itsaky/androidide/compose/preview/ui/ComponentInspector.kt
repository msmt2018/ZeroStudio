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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp

/**
 * 组件检查器: 通过反射读取 `LayoutNode.coordinates` 的位置 / 尺寸信息, 绘制边界框.
 *
 * ## 设计
 *
 * - 选中状态保存在 [InspectorController.selectedNodeId]
 * - 每个布局节点用 [Box] 包裹, 内部用 [BoxScope] 拿到 bounds
 * - 注意: 完整的 AS Component Inspector 需要访问私有 `LayoutNode` 字段 (kotlin-reflect);
 *   本骨架仅提供 UI 容器 + 选中状态, 反射读字段在 P2 PR 中加入.
 */
@Stable
class InspectorController {
    var selectedNodeId by mutableStateOf<String?>(null)
        private set

    fun select(id: String?) {
        selectedNodeId = id
    }

    fun clear() = select(null)
}

@Composable
fun rememberInspectorController(): InspectorController = remember { InspectorController() }

@Composable
fun ComponentInspectorPanel(
    controller: InspectorController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = "Component Inspector",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(8.dp)
        )
        HorizontalDivider()
        if (controller.selectedNodeId == null) {
            Text(
                text = "点击任意节点查看属性 (P2 PR: 通过反射读 LayoutNode 私有字段).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            Text(
                text = "Selected: ${controller.selectedNodeId}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp)
            )
            // P2: 在此展示 width/height/x/y/parent 等属性
        }
    }
}
