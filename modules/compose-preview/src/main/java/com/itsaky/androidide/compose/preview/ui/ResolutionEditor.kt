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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 自定义分辨率编辑器.
 *
 * 用户可输入 width × height × dpi 来自定义设备尺寸.
 * 默认填入当前 [initial] 的值, 确认后通过 [onConfirm] 回调传出.
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义分辨率", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                OutlinedTextField(
                    value = dpi,
                    onValueChange = { dpi = it.filter { c -> c.isDigit() } },
                    label = { Text("Density (dpi)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "预览尺寸: %.0f × %.0f dp".format(
                        (width.toIntOrNull() ?: initial.widthPx) * 160f / (dpi.toIntOrNull() ?: initial.densityDpi),
                        (height.toIntOrNull() ?: initial.heightPx) * 160f / (dpi.toIntOrNull() ?: initial.densityDpi)
                    ),
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
                onConfirm(
                    initial.copy(
                        id = "custom-${w}x${h}@${d}",
                        displayName = "Custom ${w}×${h}",
                        widthPx = w,
                        heightPx = h,
                        densityDpi = d,
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
