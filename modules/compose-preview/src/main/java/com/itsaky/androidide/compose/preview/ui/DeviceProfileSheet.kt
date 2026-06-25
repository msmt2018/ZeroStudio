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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.compose.preview.data.device.CutoutGeometry
import com.itsaky.androidide.compose.preview.data.device.DeviceCatalog
import com.itsaky.androidide.compose.preview.ui.DeviceProfile.FormFactor

/**
 * 设备选择底部 Sheet v2.1.
 *
 * 按 [FormFactor] 分组显示内置 30+ 真实设备, 每行带:
 * - 设备缩略图 (含 cutout 标识)
 * - 设备名 / 厂商 / 型号
 * - 分辨率 (px × px @ dpi)
 * - 屏幕宽 (dp)
 *
 * 分组顺序: Phone → Foldable → Tablet → Watch → Desktop.
 *
 * @param onSelect 用户选中设备, 回调; selectedId 用于高亮当前选中.
 * @param onCustom 跳转到 ResolutionEditor
 * @param onDismiss 关闭 sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceProfileSheet(
    sheetState: SheetState,
    selectedId: String,
    onSelect: (DeviceProfile) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "选择设备",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Text(
                text = "${DeviceCatalog.builtinProfiles.size} 个真实设备 · 按形态分组",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                // 按 formFactor 顺序
                val order = listOf(
                    FormFactor.PHONE,
                    FormFactor.FOLDABLE_OUTER,
                    FormFactor.FOLDABLE_INNER,
                    FormFactor.TABLET,
                    FormFactor.WATCH,
                    FormFactor.DESKTOP,
                )
                order.forEach { ff ->
                    val list = DeviceCatalog.byFormFactor(ff)
                    if (list.isNotEmpty()) {
                        item(key = "header_${ff.name}") {
                            FormFactorHeader(ff = ff, count = list.size)
                        }
                        items(list, key = { it.id }) { profile ->
                            DeviceProfileRow(
                                profile = profile,
                                selected = profile.id == selectedId,
                                onClick = {
                                    onSelect(profile)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
                item(key = "custom_btn") {
                    TextButton(
                        onClick = {
                            onCustom()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("自定义分辨率…")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FormFactorHeader(ff: FormFactor, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconForFormFactor(ff),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = labelForFormFactor(ff) + " · $count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DeviceProfileRow(
    profile: DeviceProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else androidx.compose.ui.graphics.Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 缩略图
        DeviceThumbnail(profile = profile, maxHeight = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${profile.manufacturer} · ${profile.widthPx}×${profile.heightPx} @ ${profile.densityDpi}dpi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val cutoutText = when (profile.cutout) {
                is CutoutGeometry.Notch -> "Notch"
                is CutoutGeometry.PunchHole -> "Punch-hole"
                is CutoutGeometry.WaterfallCurve -> "Waterfall"
                is CutoutGeometry.DynamicIsland -> "Dynamic Island"
                null -> "No cutout"
            }
            Text(
                text = "%.0f dp · %s · %s".format(profile.widthDp, profile.osVersion, cutoutText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun iconForFormFactor(ff: FormFactor): ImageVector = when (ff) {
    FormFactor.PHONE -> Icons.Filled.Smartphone
    FormFactor.FOLDABLE_INNER, FormFactor.FOLDABLE_OUTER -> Icons.Filled.Phone
    FormFactor.TABLET -> Icons.Filled.Tablet
    FormFactor.WATCH -> Icons.Filled.Watch
    FormFactor.DESKTOP -> Icons.Filled.Phone
    FormFactor.NONE -> Icons.Filled.Smartphone
}

private fun labelForFormFactor(ff: FormFactor): String = when (ff) {
    FormFactor.PHONE -> "Phone"
    FormFactor.FOLDABLE_INNER -> "Foldable (Inner)"
    FormFactor.FOLDABLE_OUTER -> "Foldable (Outer)"
    FormFactor.TABLET -> "Tablet"
    FormFactor.WATCH -> "Watch"
    FormFactor.DESKTOP -> "Desktop"
    FormFactor.NONE -> "Custom"
}
