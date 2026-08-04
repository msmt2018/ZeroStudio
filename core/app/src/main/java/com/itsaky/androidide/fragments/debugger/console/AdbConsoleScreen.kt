         }
                                    }
                                }
                            }
                            // 标签筛选 chip 行（落实 spec §6.5「筛选 labels」）
                            if (allLabels.isNotEmpty()) {
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    item {
                                        LabelChip(
                                            text = "全部",
                                            selected = selectedLabel == null,
                                            onClick = { viewModel.setSelectedLabel(null) },
                                        )
                                    }
                                    items(allLabels, key = { it }) { label ->
                                        LabelChip(
                                            text = label,
                                            selected = selectedLabel == label,
                                            onClick = {
                                                viewModel.setSelectedLabel(
                                                    if (selectedLabel == label) null else label
                                                )
                                            },
                                        )
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

    // 添加自定义命令对话框
    if (showAddCommandDialog) {
        AddCommandDialog(
            onDismiss = { showAddCommandDialog = false },
            onConfirm = { cmd, desc, labels ->
                viewModel.addCustomCommand(cmd, desc, labels)
            },
        )
    }

    // 预置命令加载进度
    loadProgress?.let { p ->
        androidx.compose.material3.LinearProgressIndicator(
            progress = { p },
            modifier = Modifier.fillMaxWidth(),
            color = c.primary,
        )
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

/**
 * 标签筛选 chip。落实 spec §6.5「筛选 labels」。
 *
 * @param text 标签名
 * @param selected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun LabelChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = deviceConnectionColors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) c.primary else c.surfaceHighlight,
        border = BorderStroke(
            1.dp,
            if (selected) c.primary else c.border,
        ),
        onClick = onClick,
    ) {
        Text(
            text = text,
            color = if (selected) c.textPrimary else c.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * FAB 菜单。落实 spec §6.7：加载预置命令 / 添加自定义命令 / 书签。
 *
 * 展开时显示三个子操作按钮 + 一个主切换按钮。
 */
@Composable
private fun FloatingActionButtonMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onLoadDefaults: () -> Unit,
    onAddCustom: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val c = deviceConnectionColors
    Box {
        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = onToggle,
            icon = { Icon(Icons.Default.Add, "菜单", tint = c.textPrimary) },
            text = { Text(if (expanded) "关闭" else "更多", color = c.textPrimary) },
            containerColor = c.primary,
            contentColor = c.textPrimary,
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = onToggle,
        ) {
            DropdownMenuItem(
                text = { Text("加载预置命令", color = c.textPrimary) },
                leadingIcon = { Icon(Icons.Default.Download, null, tint = c.primary) },
                onClick = onLoadDefaults,
            )
            DropdownMenuItem(
                text = { Text("添加自定义命令", color = c.textPrimary) },
                leadingIcon = { Icon(Icons.Default.Edit, null, tint = c.primary) },
                onClick = onAddCustom,
            )
            DropdownMenuItem(
                text = { Text("仅看收藏", color = c.textPrimary) },
                leadingIcon = { Icon(Icons.Default.Bookmark, null, tint = c.statusYellow) },
                onClick = onToggleFavorite,
            )
        }
    }
}

/**
 * 添加自定义命令对话框。落实 spec §6.7 FAB 菜单「添加自定义命令」。
 */
@Composable
private fun AddCommandDialog(
    onDismiss: () -> Unit,
    onConfirm: (command: String, description: String, labels: List<String>) -> Unit,
) {
    val c = deviceConnectionColors
    var command by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var labelsText by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义命令", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        containerColor = c.surfacePanel,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("命令", color = c.textSecondary) },
                    placeholder = { Text("如：adb shell pm list packages", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述", color = c.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = labelsText,
                    onValueChange = { labelsText = it },
                    label = { Text("标签（逗号分隔）", color = c.textSecondary) },
                    placeholder = { Text("如：pm, shell", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            DcPrimaryButton(
                text = "添加",
                onClick = {
                    val labels = labelsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(command.trim(), description.trim(), labels)
                    onDismiss()
                },
                enabled = command.isNotBlank(),
            )
        },
        dismissButton = {
            DcSecondaryButton(text = "取消", onClick = onDismiss)
        },
    )
}