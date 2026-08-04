package com.itsaky.androidide.fragments.debugger.console

import android.zero.studio.commandexamples.data.local.model.CommandEntity
import android.zero.studio.shell.common.domain.model.OutputLine
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itsaky.androidide.debugger.connection.status.ChannelStatus
import com.itsaky.androidide.ui.theme.deviceconnection.DcPrimaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcSecondaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusDot
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusLevel
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionColors
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * adb 命令执行页 Compose UI。
 *
 * 见 spec §6：
 * - TopBar：返回 / 标题 / 通道选择器 / 刷新
 * - 活动连接条：当前通道状态点 + 设备名
 * - 命令示范列表（可折叠）：搜索 / 排序 / 收藏 / 删除
 * - 输出控制台：等宽字体 + 关键词高亮
 * - 输入栏：输入框 + 运行 / 停止 / 历史
 */
@Composable
fun AdbConsoleScreen(
    onBack: () -> Unit,
    onNavigateToConnection: () -> Unit,
    viewModel: AdbConsoleViewModel = hiltViewModel(),
) {
    val c = deviceConnectionColors
    val availableChannels by viewModel.availableChannels.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val input by viewModel.input.collectAsState()
    val output by viewModel.output.collectAsState()
    val running by viewModel.running.collectAsState()
    val history by viewModel.history.collectAsState()
    val query by viewModel.query.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val commandExamples by viewModel.commandExamples.collectAsState()
    val hasUsableConnection by viewModel.hasUsableConnection.collectAsState()

    var channelMenuExpanded by remember { mutableStateOf(false) }
    var examplesExpanded by remember { mutableStateOf(true) }
    var showHistory by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val consoleListState = rememberLazyListState()
    LaunchedEffect(output.size) {
        if (output.isNotEmpty()) {
            consoleListState.animateScrollToItem(output.lastIndex)
        }
    }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text("ADB 命令", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = c.textPrimary)
                    }
                },
                actions = {
                    // 通道选择器
                    Box {
                        Surface(
                            color = c.surfaceHighlight,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, c.border),
                            onClick = { channelMenuExpanded = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DcStatusDot(
                                    level = viewModel.statusForChannel(activeChannel)?.level
                                        ?: DcStatusLevel.RED,
                                    sizeDp = 8,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    activeChannel.displayName,
                                    color = c.textPrimary,
                                    fontSize = 13.sp,
                                )
                                Icon(Icons.Default.ArrowDropDown, null, tint = c.textSecondary)
                            }
                        }
                        DropdownMenu(
                            expanded = channelMenuExpanded,
                            onDismissRequest = { channelMenuExpanded = false },
                        ) {
                            availableChannels.forEach { ch ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            DcStatusDot(
                                                level = viewModel.statusForChannel(ch)?.level
                                                    ?: DcStatusLevel.RED,
                                                sizeDp = 8,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(ch.displayName, color = c.textPrimary)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setActiveChannel(ch)
                                        channelMenuExpanded = false
                                    },
                                )
                            }
                            if (availableChannels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("无可用通道", color = c.textSecondary) },
                                    onClick = { channelMenuExpanded = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Refresh, "刷新", tint = c.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surfacePanel),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            // 活动连接条
            ActiveConnectionBar(
                activeChannel = activeChannel,
                status = viewModel.statusForChannel(activeChannel),
                hasUsableConnection = hasUsableConnection,
                onNavigateToConnection = onNavigateToConnection,
                modifier = Modifier.fillMaxWidth(),
            )

            // 命令示范列表（可折叠）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = c.surfacePanel,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "命令示范 (${commandExamples.size})",
                            color = c.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { examplesExpanded = !examplesExpanded }) {
                            Icon(
                                if (examplesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                "折叠",
                                tint = c.textSecondary,
                            )
                        }
                    }
                    AnimatedVisibility(visible = examplesExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 搜索 + 排序
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = viewModel::setQuery,
                                    placeholder = { Text("搜索命令 / 描述 / 标签", fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = c.textSecondary) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                Box {
                                    DcSecondaryButton(
                                        text = sortType.label,
                                        onClick = { showSortMenu = true },
                                    )
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                    ) {
                                        CommandSortType.values().forEach { t ->
                                            DropdownMenuItem(
                                                text = { Text(t.label, color = c.textPrimary) },
                                                onClick = {
                                                    viewModel.setSortType(t)
                                                    showSortMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            // 命令卡片列表
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(commandExamples, key = { it.id }) { cmd ->
                                    CommandExampleItem(
                                        command = cmd,
                                        onClick = { viewModel.useCommand(cmd) },
                                        onToggleFavorite = { viewModel.toggleFavorite(cmd) },
                                        onDelete = { viewModel.deleteCommand(cmd.id) },
                                    )
                                }
                                if (commandExamples.isEmpty()) {
                                    item {
                                        Text(
                                            if (query.isBlank()) "暂无命令，可加载预置命令"
                                            else "无匹配命令",
                                            color = c.textSecondary,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 输出控制台
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = c.consoleBackground,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Terminal, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("输出", color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (output.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearOutput) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("清空", fontSize = 12.sp)
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        state = consoleListState,
                    ) {
                        items(output) { line ->
                            Text(
                                text = remember(line, c) { highlightLine(line, c) },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = c.consoleText,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (output.isEmpty()) {
                            item {
                                Text(
                                    "无输出。在下方输入命令并运行，或从命令示范列表中点击一条。",
                                    color = c.textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 输入栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = c.surfacePanel,
                border = BorderStroke(1.dp, c.border),
            ) {
                Column(modifier = Modifier.padding(8.dp).imePadding()) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = viewModel::setInput,
                        placeholder = { Text("输入命令（如 adb devices）", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = hasUsableConnection || activeChannel == AdbChannel.BASIC,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showHistory = !showHistory }) {
                            Icon(Icons.Default.History, "历史", tint = c.textSecondary)
                        }
                        if (running) {
                            DcPrimaryButton(
                                text = "停止",
                                icon = Icons.Default.Stop,
                                onClick = viewModel::stopCommand,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            DcPrimaryButton(
                                text = "运行",
                                icon = Icons.Default.PlayArrow,
                                enabled = input.isNotBlank() && (hasUsableConnection || activeChannel == AdbChannel.BASIC),
                                onClick = viewModel::runCommand,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    AnimatedVisibility(visible = showHistory && history.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            color = c.surfaceHighlight,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            LazyColumn {
                                items(history.reversed()) { cmd ->
                                    Surface(
                                        color = Color.Transparent,
                                        onClick = {
                                            viewModel.pickHistory(cmd)
                                            showHistory = false
                                        },
                                    ) {
                                        Text(
                                            cmd,
                                            color = c.textPrimary,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 顶部活动连接条。 */
@Composable
private fun ActiveConnectionBar(
    activeChannel: AdbChannel,
    status: ChannelStatus?,
    hasUsableConnection: Boolean,
    onNavigateToConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = deviceConnectionColors
    val level = status?.level ?: DcStatusLevel.RED
    val label = status?.label ?: "未连接"
    Surface(
        modifier = modifier,
        color = c.surfaceHighlight,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DcStatusDot(level = level, sizeDp = 10)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "通道: ${activeChannel.displayName}    $label",
                    color = c.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                status?.deviceName?.let {
                    Text("设备: $it", color = c.textSecondary, fontSize = 11.sp)
                }
            }
            if (!hasUsableConnection && activeChannel != AdbChannel.BASIC) {
                DcSecondaryButton(
                    text = "去连接",
                    onClick = onNavigateToConnection,
                )
            }
        }
    }
}

/** 单条命令示范卡片。 */
@Composable
private fun CommandExampleItem(
    command: CommandEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = deviceConnectionColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = c.surfaceHighlight,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, c.border.copy(alpha = 0.5f)),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    command.command,
                    color = c.consoleCommand,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
                if (command.description.isNotBlank()) {
                    Text(
                        command.description,
                        color = c.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                if (command.labels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        command.labels.take(3).forEach { label ->
                            Surface(
                                color = c.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    label,
                                    color = c.primary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (command.isFavourite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    "收藏",
                    tint = if (command.isFavourite) c.statusYellow else c.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = c.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** 把 [OutputLine] 转成带颜色的 [AnnotatedString]。 */
private fun highlightLine(line: OutputLine, colors: DeviceConnectionColors): AnnotatedString {
    val segments = ConsoleHighlighter.highlight(line.text, line.isError, colors)
    return buildAnnotatedString {
        segments.forEach { seg ->
            withStyle(SpanStyle(color = seg.color)) {
                append(seg.text)
            }
        }
    }
}