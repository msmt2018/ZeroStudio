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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.runtime.DeviceProfileMatrix

/**
 * v2.3 P2 设备 profile 矩阵面板.
 *
 * 设计师友好: `LazyVerticalGrid` 自动按屏幕宽度分列, 每个 cell 显示
 * 一个 device profile 的: 名称 / 厂商 / 实际屏幕尺寸 (宽 × 高 dp) / 缩略图 (按 dp 比例).
 *
 * 单击 cell → 触发 [onProfileClick], 供上层切换为 SINGLE 模式显示该 profile.
 *
 * @param matrix 要显示的 profile 矩阵. 建议 20+ 个.
 * @param selectedId 当前选中的 profile id (用于高亮).
 * @param onProfileIdClick 单击回调. 传入 profile id.
 */
@Composable
fun ProfileMatrixPanel(
    matrix: DeviceProfileMatrix,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    onProfileIdClick: (String) -> Unit = {},
) {
    Column(modifier = modifier) {
        // 顶部: 标题 + 总数
        Text(
            text = "DeviceProfileMatrix (v2.3 P2)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = "${matrix.size} profiles (click to switch to single mode)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(matrix.profiles, key = { it.id }) { profile ->
                ProfileCell(
                    profile = profile,
                    isSelected = profile.id == selectedId,
                    onClick = { onProfileIdClick(profile.id) },
                )
            }
        }
    }
}

/**
 * 单个 device profile 单元格.
 *
 * 内部按 (widthDp, heightDp) 比例画一个缩略矩形, 让用户视觉对比尺寸.
 */
@Composable
private fun ProfileCell(
    profile: DeviceProfile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // 把 dp 缩到合适大小 (最大 80dp 宽, 按比例缩)
    val maxThumbDp = 80f
    val wDp = profile.widthDp.coerceAtLeast(240f)
    val hDp = profile.heightDp.coerceAtLeast(320f)
    val scale = if (wDp > maxThumbDp) maxThumbDp / wDp else 1f
    val thumbW = (wDp * scale).coerceIn(40f, maxThumbDp)
    val thumbH = (hDp * scale).coerceIn(40f, maxThumbDp * 2f)

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val chassisColor = profile.chassisColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 设备缩略图 (按比例)
        Box(
            modifier = Modifier
                .size(width = thumbW.dp, height = thumbH.dp)
                .clip(RoundedCornerShape(profile.cornerRadiusDp.dp / 2))
                .background(chassisColor.copy(alpha = 0.85f))
                .border(
                    width = 0.5.dp,
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(profile.cornerRadiusDp.dp / 2),
                ),
        ) {
            // 中间放尺寸标签
            Text(
                text = "${wDp.toInt()}×${hDp.toInt()}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        // 设备名
        Text(
            text = profile.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
        // 厂商
        Text(
            text = profile.manufacturer,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
        )
    }
}
