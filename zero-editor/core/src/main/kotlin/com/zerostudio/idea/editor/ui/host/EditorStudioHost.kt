package com.zerostudio.idea.editor.ui.host

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zerostudio.idea.editor.core.buffer.mmap.MappedChunkBuffer
import com.zerostudio.idea.editor.core.buffer.paging.VirtualPageManager
import com.zerostudio.idea.editor.core.buffer.piecetree.PieceTreeCore
import com.zerostudio.idea.editor.core.buffer.sora.GlobalGutterRenderer
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.core.cursor.AdvancedMultiCursorEngine
import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.lsp.model.BuiltInLspManager
import com.zerostudio.idea.editor.lsp.model.LspCapabilitiesHandler
import com.zerostudio.idea.editor.lsp.model.LspServerInstance
import com.zerostudio.idea.editor.lsp.registry.LspRegistry
import com.zerostudio.idea.editor.ui.compose.ZeroCodeEditor
import com.zerostudio.idea.editor.ui.keys.ZeroEditorExtraKeysBar
import com.zerostudio.idea.editor.ui.lsp.LspLogStreamViewer
import com.zerostudio.idea.editor.ui.paging.PageNavigationOverlay
import com.zerostudio.idea.editor.ui.tabs.WorkspaceEditorTab
import com.zerostudio.idea.editor.ui.tabs.ZeroTabWorkspace
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorStudioHost(
    initialFile: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { EditorPreferencesState() }

    var editorViewInstance by remember { mutableStateOf<ZeroCodeEditorView?>(null) }
    val multiCursorEngine = remember(editorViewInstance) {
        editorViewInstance?.let { AdvancedMultiCursorEngine(it) }
    }

    // 1. 64位 Piece Tree + mmap 内核初始化
    val chunkBuffer = remember(initialFile) { MappedChunkBuffer(initialFile) }
    val pieceTree = remember(chunkBuffer) { PieceTreeCore(listOf(chunkBuffer)) }
    val pageManager = remember(pieceTree) {
        VirtualPageManager(pieceTree, preferences.autoPaginationThresholdChars)
    }

    // 2. 自适应行号排版器
    val gutterRenderer = remember(editorViewInstance, pageManager) {
        editorViewInstance?.let { GlobalGutterRenderer(it) { pageManager } }
    }

    // 3. LSP 桥接实例
    var activeConnector by remember { mutableStateOf<ZeroLspConnector?>(null) }
    var capabilitiesHandler by remember { mutableStateOf<LspCapabilitiesHandler?>(null) }
    var showLspLogViewer by remember { mutableStateOf(false) }

    // 4. 打开的多标签列表
    val openTabs = remember {
        mutableStateListOf(
            WorkspaceEditorTab(
                id = initialFile.absolutePath,
                file = initialFile,
                title = initialFile.name
            )
        )
    }
    var currentTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        BuiltInLspManager.installDefaults(context)
        LspRegistry.loadExternalServers(context)
    }

    // 挂载语言服务
    LaunchedEffect(editorViewInstance, initialFile) {
        val editor = editorViewInstance ?: return@LaunchedEffect
        val server = LspRegistry.findServerForExtension(initialFile.extension)
        if (server != null) {
            val instance = LspServerInstance(
                id = "${server.id}_instance",
                serverName = server.serverName,
                projectRoot = initialFile.parentFile ?: initialFile,
                connectionProvider = { server.getConnectionConfig().createProvider(it) as com.zerostudio.idea.editor.lsp.connection.LspConnection }
            )
            val connector = ZeroLspConnector(instance, editor, initialFile.toURI().toString())
            activeConnector = connector
            capabilitiesHandler = LspCapabilitiesHandler(connector)
            connector.initialize()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "ZeroStudio • ${openTabs[currentTabIndex].title}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        // 格式化当前文档
                        IconButton(onClick = {
                            scope.launch {
                                capabilitiesHandler?.let { it.connector.formatDocument() }
                            }
                        }) {
                            Icon(Icons.Default.Build, contentDescription = "代码格式化")
                        }

                        // 快速触发 Ctrl + Shift + L
                        IconButton(onClick = {
                            multiCursorEngine?.selectAllOccurrences()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "多光标选中所有匹配")
                        }

                        // 查看 LSP 日志
                        IconButton(onClick = { showLspLogViewer = true }) {
                            Icon(Icons.Default.Info, contentDescription = "LSP 日志流")
                        }

                        // 保存当前视口
                        IconButton(onClick = {
                            editorViewInstance?.let { view ->
                                pageManager.commitCurrentPage(view.text)
                                openTabs[currentTabIndex] = openTabs[currentTabIndex].copy(isDirty = false)
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "保存")
                        }
                    }
                )

                // 多标签条
                ZeroTabWorkspace(
                    tabs = openTabs,
                    activeTabIndex = currentTabIndex,
                    onTabSelect = { currentTabIndex = it },
                    onTabClose = { closedIdx ->
                        if (openTabs.size > 1) {
                            openTabs.removeAt(closedIdx)
                            currentTabIndex = currentTabIndex.coerceAtMost(openTabs.size - 1)
                        }
                    }
                )
            }
        },
        bottomBar = {
            ZeroEditorExtraKeysBar(
                editorView = editorViewInstance,
                preferences = preferences
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sora 主视图
            ZeroCodeEditor(
                pieceTree = pieceTree,
                preferences = preferences,
                onEditorInitialized = { editor ->
                    editorViewInstance = editor
                    editor.attachVirtualPageManager(pageManager)
                    // 应用全局行号自适应
                    gutterRenderer?.let {
                        editor.lineNumberMarginLeft = it.computeGutterWidth()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 底部无感翻页滑动条
            PageNavigationOverlay(
                editorView = editorViewInstance,
                pageManager = pageManager,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // 浮动覆盖：LSP 日志帧查看面板
            if (showLspLogViewer) {
                activeConnector?.instance?.let { instance ->
                    LspLogStreamViewer(
                        instance = instance,
                        onClose = { showLspLogViewer = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.9f))
                    )
                }
            }
        }
    }
}