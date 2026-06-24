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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.compose.preview.data.device.CutoutGeometry

/**
 * 自定义分辨率 / 形态 / 切口 编辑器 v2.1.
 *
 * 用户可输入:
 * - width × height × dpi
 * - 形态因子 (Phone / Foldable / Tablet / Watch / Desktop)
 * - 切口 (None / Notch / PunchHole / Waterfall)
 *
 * 实时显示预览尺寸 (dp).
 *
 * @param initial 初始 profile
 * @param onConfirm 确认时传出新 profile
 * @param onDismiss 取消
 */
@Composable
fun ResolutionEditor(
    initial: DeviceProfile,
    onConfirm: (DeviceProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var width by remember { mutableStateOf(initial.widthPx.toString()) }
    var height by remember { mutableStateOf(initial.heightPx.toString()) }
    var dpi by remember { mutableStateOf(initial.densityDpi.toString()) }
    var formFactor by remember { mutableStateOf(initial.formFactor) }
    var cutoutKind by remember { mutableStateOf(initial.cutout?.kind() ?: CutoutKind.NONE) }
    var cornerRadius by remember { mutableStateOf(initial.cornerRadiusDp.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义设备", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                // 形态因子
                Text(
                    text = "形态",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(DeviceProfile.FormFactor.values().toList()) { ff ->
                        FilterChip(
                            selected = formFactor == ff,
                            onClick = { formFactor = ff },
                            label = { Text(ff.name) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 切口
                Text(
                    text = "切口 (Cutout)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(CutoutKind.values().toList()) { kind ->
                        FilterChip(
                            selected = cutoutKind == kind,
                            onClick = { cutoutKind = kind },
                            label = { Text(kind.displayName) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 分辨率
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = width,
                        onValueChange = { width = it.filter { c -> c.isDigit() } },
                        label = { Text("Width (px)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it.filter { c -> c.isDigit() } },
                        label = { Text("Height (px)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dpi,
                        onValueChange = { dpi = it.filter { c -> c.isDigit() } },
                        label = { Text("Density (dpi)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = cornerRadius,
                        onValueChange = { cornerRadius = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Corner (dp)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val w = width.toIntOrNull() ?: initial.widthPx
                val h = height.toIntOrNull() ?: initial.heightPx
                val d = dpi.toIntOrNull() ?: initial.densityDpi
                Text(
                    text = "预览尺寸: %.0f × %.0f dp".format(w * 160f / d, h * 160f / d),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = width.toIntOrNull() ?: initial.widthPx
                val h = height.toIntOrNull() ?: initial.heightPx
                val d = dpi.toIntOrNull()?.coerceIn(120, 800) ?: initial.densityDpi
                val r = cornerRadius.toFloatOrNull()?.coerceIn(0f, 200f) ?: initial.cornerRadiusDp
                onConfirm(
                    initial.copy(
                        id = "custom-${w}x${h}@${d}-${formFactor.name}",
                        displayName = "Custom ${w}×${h} ${formFactor.name.lowercase()}",
                        widthPx = w,
                        heightPx = h,
                        densityDpi = d,
                        formFactor = formFactor,
                        cornerRadiusDp = r,
                        cutout = cutoutKind.toCutoutGeometry(),
                        isCustom = true,
                    )
                )
            }) { Text("应用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 切口类型 (在 ResolutionEditor 中作为 chip 显示).
 */
internal enum class CutoutKind(val displayName: String) {
    NONE("None"),
    NOTCH("Notch"),
    PUNCH_HOLE("Punch"),
    WATERFALL("Waterfall"),
    DYNAMIC_ISLAND("Dynamic Island");

    fun toCutoutGeometry(): CutoutGeometry? = when (this) {
        NONE -> null
        NOTCH -> CutoutGeometry.IPHONE_14_NOTCH
        PUNCH_HOLE -> CutoutGeometry.PIXEL_PUNCHHOLE
        WATERFALL -> CutoutGeometry.HUAWEI_WATERFALL
        DYNAMIC_ISLAND -> CutoutGeometry.IPHONE_14_PRO_DYNAMIC_ISLAND
    }
}

private fun CutoutGeometry.kind(): CutoutKind = when (this) {
    is CutoutGeometry.Notch -> CutoutKind.NOTCH
    is CutoutGeometry.PunchHole -> CutoutKind.PUNCH_HOLE
    is CutoutGeometry.WaterfallCurve -> CutoutKind.WATERFALL
    is CutoutGeometry.DynamicIsland -> CutoutKind.DYNAMIC_ISLAND
}
