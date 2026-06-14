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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.runtime.MultiPreviewRegistry
import com.itsaky.androidide.compose.preview.runtime.PreviewDisplayMode
import com.itsaky.androidide.compose.preview.runtime.PreviewSlot

/**
 * Multi-Preview 容器 v2.2 (P2).
 *
 * 根据当前 [MultiPreviewRegistry.displayMode] 渲染 SINGLE 或 GALLERY.
 *
 * - **SINGLE**: 仅渲染 [MultiPreviewRegistry.selectedIndex] 槽位
 * - **GALLERY**: 渲染所有 visible=true 的槽位, 用 LazyColumn 垂直堆叠
 *
 * 每个 slot 用 [PreviewSlotCard] 包裹, 显示 label + 切换可见性按钮.
 *
 * ## 用法
 *
 * ```kotlin
 * // 顶层 preview 容器中:
 * Box(Modifier.fillMaxSize()) {
 *     MultiPreviewHost(
 *         renderSlot = { slot -> /* ComposeView 渲染 slot.composableClass / slot.functionName */ },
 *         modifier = Modifier.fillMaxSize(),
 *     )
 * }
 * ```
 *
 * @param renderSlot 渲染单个 slot 的 Composable (由调用方决定如何用 ComposeView 渲染)
 */
@Composable
fun MultiPreviewHost(
    renderSlot: @Composable (slot: PreviewSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    var slots by remember { mutableStateOf(MultiPreviewRegistry.slots()) }
    var mode by remember { mutableStateOf(MultiPreviewRegistry.displayMode()) }
    var selectedIndex by remember { mutableStateOf(MultiPreviewRegistry.selectedIndex()) }

    LaunchedEffect(Unit) {
        MultiPreviewRegistry.addListener {
            slots = MultiPreviewRegistry.slots()
            mode = MultiPreviewRegistry.displayMode()
            selectedIndex = MultiPreviewRegistry.selectedIndex()
        }
    }

    when (mode) {
        PreviewDisplayMode.SINGLE -> SingleMode(slots, selectedIndex, renderSlot, modifier)
        PreviewDisplayMode.GALLERY -> GalleryMode(slots, renderSlot, modifier)
    }
}

@Composable
private fun SingleMode(
    slots: List<PreviewSlot>,
    selectedIndex: Int,
    renderSlot: @Composable (PreviewSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slot = slots.getOrNull(selectedIndex)
    if (slot == null) {
        EmptyState(message = "无 preview 可用", modifier = modifier)
        return
    }
    Box(modifier = modifier) {
        renderSlot(slot)
    }
}

@Composable
private fun GalleryMode(
    slots: List<PreviewSlot>,
    renderSlot: @Composable (PreviewSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = slots.filter { it.visible }
    if (visible.isEmpty()) {
        EmptyState(message = "所有 preview 已隐藏", modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101015)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        items(visible, key = { it.functionName }) { slot ->
            PreviewSlotCard(slot = slot, renderSlot = renderSlot)
        }
    }
}

@Composable
private fun PreviewSlotCard(
    slot: PreviewSlot,
    renderSlot: @Composable (PreviewSlot) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E1E26))
            .border(1.dp, Color(0xFF2A2A35), RoundedCornerShape(6.dp))
            .padding(0.dp),
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252530))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = slot.label,
                color = Color(0xFF80CBC4),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    // 切换 visible — 通过新 bind 调用实现
                    val updated = MultiPreviewRegistry.slots().map {
                        if (it.functionName == slot.functionName)
                            it.copy(visible = !it.visible)
                        else it
                    }
                    MultiPreviewRegistry.bind(updated)
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = if (slot.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = "toggle",
                    tint = if (slot.visible) Color(0xFF80CBC4) else Color(0xFF555555),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // 内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFF101015)),
        ) {
            if (slot.hasError) {
                Text(
                    text = "渲染失败: ${slot.errorMessage}",
                    color = Color(0xFFE57373),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                renderSlot(slot)
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101015)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color(0xFF888888),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * Gallery 模式切换栏 v2.2 (P2).
 *
 * 显示当前模式 / slot 数 / SINGLE 模式下拉选择 / 模式切换按钮.
 */
@Composable
fun PreviewModeToggleBar(
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(MultiPreviewRegistry.displayMode()) }
    var slotCount by remember { mutableStateOf(MultiPreviewRegistry.slots().size) }

    LaunchedEffect(Unit) {
        MultiPreviewRegistry.addListener {
            mode = MultiPreviewRegistry.displayMode()
            slotCount = MultiPreviewRegistry.slots().size
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E26))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$slotCount previews · ${mode.displayName}",
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        // 模式切换按钮
        ModeButton(
            text = "Single",
            selected = mode == PreviewDisplayMode.SINGLE,
        ) { MultiPreviewRegistry.setDisplayMode(PreviewDisplayMode.SINGLE) }
        Spacer(Modifier.width(4.dp))
        ModeButton(
            text = "Gallery",
            selected = mode == PreviewDisplayMode.GALLERY,
        ) { MultiPreviewRegistry.setDisplayMode(PreviewDisplayMode.GALLERY) }
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) Color(0xFF3F51B5) else Color(0xFF2A2A35))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

