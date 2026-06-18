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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SmartphoneOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 预览顶栏工具 v2.1.
 *
 * 显示在 [ComposePreviewActivity] 顶部, 提供:
 * - 设备切换 (chip 文本 = 当前设备名; 点击弹 [DeviceProfileSheet])
 * - 主题切换 (Light / Dark / Custom 循环)
 * - 缩放控制 (zoom out / zoom in / fit)
 * - 系统栏显示开关
 * - 调试开关 (P1 占位, 留口)
 * - 关闭按钮
 *
 * 状态:
 * - [PreviewToolbarState] 描述所有可观察状态
 * - [PreviewToolbarActions] 描述所有用户行为
 *
 * @param state 当前状态
 * @param actions 行为回调
 * @param modifier modifier
 */
@Composable
fun PreviewToolbar(
    state: PreviewToolbarState,
    actions: PreviewToolbarActions,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 【v3.2】真实设备模拟 toggle — 默认无设备模式 (chip 文字 "Device" 表示可切到套壳,
        // selected 时 chip 高亮 = 当前已启用真实设备模拟).
        FilterChip(
            selected = state.deviceSimEnabled,
            onClick = actions.onToggleDeviceSim,
            label = { Text(if (state.deviceSimEnabled) "Device" else "Raw") },
            leadingIcon = {
                Icon(
                    imageVector = if (state.deviceSimEnabled) Icons.Filled.Smartphone else Icons.Filled.SmartphoneOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // 设备 chip (只切到设备模拟时显示, 否则灰显)
        AssistChip(
            onClick = actions.onOpenDeviceSheet,
            enabled = state.deviceSimEnabled,
            label = { Text(state.deviceName) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Smartphone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // 主题 chip
        AssistChip(
            onClick = actions.onCycleTheme,
            label = { Text(state.themeLabel) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Brightness6,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // 缩放信息
        AssistChip(
            onClick = actions.onFitZoom,
            label = { Text("%.0f%%".format(state.zoom * 100)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.AspectRatio,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // Zoom out
        IconButton(onClick = { actions.onSetZoom(state.zoom - 0.1f) }) {
            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
        }

        // Zoom in
        IconButton(onClick = { actions.onSetZoom(state.zoom + 0.1f) }) {
            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
        }

        // 系统栏可见性 toggle
        FilterChip(
            selected = state.showSystemBars,
            onClick = actions.onToggleSystemBars,
            label = { Text(if (state.showSystemBars) "Bars" else "Hidden") },
            leadingIcon = {
                Icon(
                    imageVector = if (state.showSystemBars) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // 调试开关
        FilterChip(
            selected = state.debugEnabled,
            onClick = actions.onToggleDebug,
            label = { Text("Debug") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        // 【PR-B 占位】全屏切换 (v3.2 先放按钮, 行为 PR-B 实现)
        IconButton(onClick = actions.onToggleFullscreen) {
            Icon(
                Icons.Filled.Fullscreen,
                contentDescription = "Fullscreen",
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 关闭
        IconButton(onClick = actions.onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }
    }
}

/**
 * 顶栏状态 (Snapshot).
 */
data class PreviewToolbarState(
    val deviceName: String = "Pixel 7",
    val themeLabel: String = "Light",
    val zoom: Float = 1.0f,
    val showSystemBars: Boolean = true,
    val debugEnabled: Boolean = false,
    /** v3.2: 是否启用真实设备模拟. 默认 false. */
    val deviceSimEnabled: Boolean = false,
    /** v3.2 / PR-B: 全屏模式. 默认 false. */
    val fullscreen: Boolean = false,
)

/**
 * 顶栏行为回调.
 */
data class PreviewToolbarActions(
    val onOpenDeviceSheet: () -> Unit,
    val onCycleTheme: () -> Unit,
    val onSetZoom: (Float) -> Unit,
    val onFitZoom: () -> Unit,
    val onToggleSystemBars: () -> Unit,
    val onToggleDebug: () -> Unit,
    val onClose: () -> Unit,
    /** v3.2: 切换设备模拟开关. */
    val onToggleDeviceSim: () -> Unit = {},
    /** v3.2 / PR-B: 切换全屏. */
    val onToggleFullscreen: () -> Unit = {},
)
