package com.zerostudio.idea.editor.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.core.config.LineSeparatorMode
import com.zerostudio.idea.editor.ui.settings.dialogs.CustomLineSpacingDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorPreferencesScreen(
    prefs: EditorPreferencesState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLineSpacingDialog by remember { mutableStateOf(false) }

    if (showLineSpacingDialog) {
        CustomLineSpacingDialog(
            initialValue = prefs.lineSpacingMultiplier,
            onConfirm = { prefs.lineSpacingMultiplier = it },
            onDismiss = { showLineSpacingDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑器设置") }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Section 1: 超大文本与流畅度
            SettingGroupHeader("大文本性能与虚拟分页")
            
            OutlinedTextField(
                value = prefs.autoPaginationThresholdChars.toString(),
                onValueChange = { input ->
                    input.toLongOrNull()?.let {
                        prefs.autoPaginationThresholdChars = it.coerceIn(100_000L, 1_000_000_000L)
                    }
                },
                label = { Text("自动分页大小 (字符数)") },
                supportingText = { Text("范围：100,000 ~ 1,000,000,000 字符") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = prefs.autoFluencyModeThresholdChars.toString(),
                onValueChange = { input ->
                    input.toLongOrNull()?.let {
                        prefs.autoFluencyModeThresholdChars = it.coerceAtLeast(10_000L)
                    }
                },
                label = { Text("自动开启流畅模式阈值 (字符数)") },
                supportingText = { Text("超过此阈值将自动卸载重度正则语法树，保全 120Hz 触控") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            SettingSwitchRow(
                title = "强制启用流畅模式",
                description = "极致降级代码修饰，最大化提升键盘键入响应速度与滑屏平滑度",
                checked = prefs.forceFluencyMode,
                onCheckedChange = { prefs.forceFluencyMode = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Section 2: 文件变更与安全性
            SettingGroupHeader("文件变更检测")
            SettingSwitchRow(
                title = "检测文件是否被外部修改",
                description = "检测到底层文件变动后弹出重载提示",
                checked = prefs.notifyFileModifiedExternal,
                onCheckedChange = { prefs.notifyFileModifiedExternal = it }
            )
            SettingSwitchRow(
                title = "检测文件是否被删除",
                description = "外部被删除后提示是否从活动窗口中移出",
                checked = prefs.notifyFileDeletedExternal,
                onCheckedChange = { prefs.notifyFileDeletedExternal = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Section 3: 排版与视觉控制
            SettingGroupHeader("字体与排版")
            SettingClickableRow(
                title = "自定义行距",
                valueText = "${String.format("%.2f", prefs.lineSpacingMultiplier)} 倍",
                onClick = { showLineSpacingDialog = true }
            )
            SettingSwitchRow(
                title = "自动换行不断词",
                description = "防止长单词在屏幕边缘被硬切断到下一行",
                checked = prefs.antiWordBreaking,
                onCheckedChange = { prefs.antiWordBreaking = it }
            )
            SettingSwitchRow(
                title = "双指手势缩放",
                description = "支持在编辑区域捏合缩放字体大小",
                checked = prefs.pinchToZoomEnabled,
                onCheckedChange = { prefs.pinchToZoomEnabled = it }
            )
            SettingSwitchRow(
                title = "启用放大镜",
                description = "拖拽选择手柄时光标上方显示局部放大镜",
                checked = prefs.magnifierEnabled,
                onCheckedChange = { prefs.magnifierEnabled = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Section 4: 辅助符号显示
            SettingGroupHeader("辅助显示")
            SettingSwitchRow(
                title = "显示行号信息",
                checked = prefs.showLineNumbers,
                onCheckedChange = { prefs.showLineNumbers = it }
            )
            SettingSwitchRow(
                title = "固定行号信息",
                description = "横向滚动代码时行号始终固定在左侧",
                checked = prefs.pinLineNumbers,
                onCheckedChange = { prefs.pinLineNumbers = it }
            )
            SettingSwitchRow(
                title = "显示自动换行箭头",
                description = "直观区分硬换行与折行效果",
                checked = prefs.showSoftWrapArrows,
                onCheckedChange = { prefs.showSoftWrapArrows = it }
            )
            SettingSwitchRow(
                title = "显示空白符号",
                description = "空格显示为居中点，制表符显示为虚线横线",
                checked = prefs.showWhitespaceCharacters,
                onCheckedChange = { prefs.showWhitespaceCharacters = it }
            )
            SettingSwitchRow(
                title = "隐藏单个空格",
                description = "仅对连续两个及以上的空格进行特殊符号可视化",
                checked = prefs.hideIsolatedSingleSpaces,
                onCheckedChange = { prefs.hideIsolatedSingleSpaces = it }
            )
            SettingSwitchRow(
                title = "显示 ASCII 控制字符",
                description = "不可见字符渲染为 [NUL]、[ESC] 等方框缩写",
                checked = prefs.renderAsciiControlCharacters,
                onCheckedChange = { prefs.renderAsciiControlCharacters = it }
            )
            SettingSwitchRow(
                title = "显示 Unicode 特殊字符",
                description = "零宽字符、方向控制符渲染为 [ZWSP]、[LRM] 等标记",
                checked = prefs.renderSpecialUnicodeSymbols,
                onCheckedChange = { prefs.renderSpecialUnicodeSymbols = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Section 5: 缩进与换行控制
            SettingGroupHeader("缩进与换行标准")
            SettingSwitchRow(
                title = "自动沿用上一行缩进",
                checked = prefs.autoIndentOnEnter,
                onCheckedChange = { prefs.autoIndentOnEnter = it }
            )
            SettingSwitchRow(
                title = "使用制表符 (\\t) 代替空格缩进",
                checked = prefs.useTabForIndent,
                onCheckedChange = { prefs.useTabForIndent = it }
            )
            SettingSliderRow(
                title = "制表符尺寸 (Tab Stop)",
                value = prefs.tabStopSize.toFloat(),
                valueRange = 1f..12f,
                steps = 10,
                onValueChange = { prefs.tabStopSize = it.toInt() }
            )
            SettingSwitchRow(
                title = "显示缩进对齐指示虚线",
                checked = prefs.showIndentGuides,
                onCheckedChange = { prefs.showIndentGuides = it }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingClickableRow(
    title: String,
    valueText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onClick) {
            Text(text = valueText)
        }
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = "${value.toInt()}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}