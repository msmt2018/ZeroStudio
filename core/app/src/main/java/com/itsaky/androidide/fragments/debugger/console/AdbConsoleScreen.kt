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