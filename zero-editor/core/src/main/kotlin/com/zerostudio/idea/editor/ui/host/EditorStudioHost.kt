package com.zerostudio.idea.editor.ui.host

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.ui.compose.ThreeScreenState
import com.zerostudio.idea.editor.ui.compose.ZeroCodeEditorViewport
import com.zerostudio.idea.editor.ui.search.EditorSearchOverlay
import com.zerostudio.idea.editor.ui.settings.EditorPreferencesScreen
import com.zerostudio.idea.editor.ui.settings.dialogs.TextAnalyticsDialog

enum class WindowSizeClassCategory { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberWindowSizeCategory(): WindowSizeClassCategory {
    val config = LocalConfiguration.current
    return when {
        config.screenWidthDp < 600 -> WindowSizeClassCategory.COMPACT
        config.screenWidthDp < 840 -> WindowSizeClassCategory.MEDIUM
        else -> WindowSizeClassCategory.EXPANDED
    }
}

/**
 * 工业级混合自适应 IDE 主工作台
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorStudioHost(
    pieceTree: LargeTextPieceTree,
    modifier: Modifier = Modifier
) {
    val sizeCategory = rememberWindowSizeCategory()
    val preferences = remember { EditorPreferencesState() }

    var panelState by remember { mutableStateOf(ThreeScreenState.FULL) }
    var isSearchOverlayVisible by remember { mutableStateOf(false) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var isAnalyticsDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ZeroStudio Code Editor",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(onClick = { isSearchOverlayVisible = !isSearchOverlayVisible }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { isAnalyticsDialogVisible = true }) {
                        Icon(Icons.Default.Info, contentDescription = "文本全维统计")
                    }
                    IconButton(onClick = { isSettingsVisible = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "偏好设置")
                    }
                    // 仅在大屏与平板提供分屏比例强制切换
                    if (sizeCategory != WindowSizeClassCategory.COMPACT) {
                        IconButton(onClick = {
                            panelState = when (panelState) {
                                ThreeScreenState.PEEK -> ThreeScreenState.HALF
                                ThreeScreenState.HALF -> ThreeScreenState.FULL
                                ThreeScreenState.FULL -> ThreeScreenState.PEEK
                                ThreeScreenState.HIDDEN -> ThreeScreenState.FULL
                            }
                        }) {
                            Icon(Icons.Default.AccountBox, contentDescription = "三分屏比例模式")
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 主编辑器视口 (接入三分屏规则约束)
            ZeroCodeEditorViewport(
                state = panelState,
                preferences = preferences,
                pieceTree = pieceTree,
                onStateChangeRequested = { panelState = it },
                modifier = Modifier.fillMaxSize()
            )

            // 浮动搜索栏
            EditorSearchOverlay(
                visible = isSearchOverlayVisible,
                onDismiss = { isSearchOverlayVisible = false },
                onPerformSearch = { /* 执行搜索 */ },
                onNavigateNext = { /* 跳到下一处 */ },
                onNavigatePrev = { /* 跳到上一处 */ },
                onReplace = { _, _ -> /* 替换执行 */ },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 全维统计弹窗
            if (isAnalyticsDialogVisible) {
                TextAnalyticsDialog(
                    pieceTree = pieceTree,
                    onDismiss = { isAnalyticsDialogVisible = false }
                )
            }

            // 偏好设置全屏侧滑页
            AnimatedVisibility(
                visible = isSettingsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EditorPreferencesScreen(
                    prefs = preferences,
                    onNavigateBack = { isSettingsVisible = false },
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}