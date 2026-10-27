package com.zerostudio.idea.editor.tab

import androidx.compose.runtime.*
import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.buffer.VirtualPageManager
import com.zerostudio.idea.editor.core.config.EditorEngineEvent
import com.zerostudio.idea.editor.core.config.EditorEventBus
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.core.io.AutoSaveCoordinator
import com.zerostudio.idea.editor.core.watcher.ExternalFileWatcher
import com.zerostudio.idea.editor.lsp.LspCoreClient
import com.zerostudio.idea.editor.lsp.ZeroLspEditorBridge
import com.zerostudio.idea.editor.ui.view.VirtualCodeEditorView
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.nio.charset.Charset
import java.util.UUID

@Stable
class ZeroEditorTabState(
    val initialFile: File?,
    val initialTitle: String
) {
    var title by mutableStateOf(initialTitle)
    var isDirty by mutableStateOf(false)
    var isReadOnly by mutableStateOf(false)
    var activePageIndex by mutableIntStateOf(0)
    var totalPages by mutableIntStateOf(1)
    var encoding by mutableStateOf(Charsets.UTF_8)
    var activeTasksCount by mutableIntStateOf(0)
}

/**
 * 工业级单标签页调度器
 */
class ZeroEditorTab(
    val file: File?,
    val preferences: EditorPreferencesState,
    val lspClient: LspCoreClient? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    val id: String = UUID.randomUUID().toString()
    val state = ZeroEditorTabState(
        initialFile = file,
        initialTitle = file?.name ?: "Untitled"
    )

    lateinit var pieceTree: LargeTextPieceTree
        private set
    lateinit var pageManager: VirtualPageManager
        private set

    private var autoSaveCoordinator: AutoSaveCoordinator? = null
    private var fileWatcher: ExternalFileWatcher? = null
    private var lspBridge: ZeroLspEditorBridge? = null
    var boundEditorView: VirtualCodeEditorView? = null
        private set

    init {
        initializeStorage()
        setupListeners()
    }

    private fun initializeStorage() {
        pieceTree = LargeTextPieceTree(file)
        pageManager = VirtualPageManager(pieceTree, preferences.autoPaginationThresholdChars)
        state.totalPages = pageManager.getTotalPages()
        state.activePageIndex = pageManager.currentPageIndex

        if (file != null) {
            autoSaveCoordinator = AutoSaveCoordinator()
            fileWatcher = ExternalFileWatcher(file).apply { startWatching() }
        }
    }

    private fun setupListeners() {
        scope.launch {
            EditorEventBus.events.collectLatest { event ->
                when (event) {
                    is EditorEngineEvent.PageSwapped -> {
                        state.activePageIndex = event.newPageIndex
                        state.totalPages = event.totalPages
                    }
                    is EditorEngineEvent.FileExternallyModified -> {
                        if (file != null && event.fileAbsolutePath == file.absolutePath) {
                            if (preferences.notifyFileModifiedExternal) {
                                // 触发重新加载逻辑
                            }
                        }
                    }
                    is EditorEngineEvent.FileExternallyDeleted -> {
                        if (file != null && event.fileAbsolutePath == file.absolutePath) {
                            if (preferences.notifyFileDeletedExternal) {
                                state.isDirty = true
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * 将标签页与 Compose 中活跃的 VirtualCodeEditorView 挂载
     */
    fun attachEditorView(editorView: VirtualCodeEditorView) {
        this.boundEditorView = editorView
        editorView.bindBufferEngine(pieceTree, preferences.autoPaginationThresholdChars)
        editorView.applyPreferences(preferences)

        file?.let {
            if (lspClient != null) {
                lspBridge = ZeroLspEditorBridge(editorView, lspClient, it.toURI().toString()).apply {
                    attach()
                }
            }
        }
    }

    /**
     * 内容变动回调（由编辑器输入驱动）
     */
    fun onContentChanged(newContent: Content) {
        state.isDirty = true
        // 触发防抖自动持久化
        if (preferences.autoSaveEnabled && file != null && !state.isReadOnly) {
            scope.launch(Dispatchers.IO) {
                pageManager.commitCurrentPageEdits(newContent)
                autoSaveCoordinator?.scheduleDebouncedSave(
                    targetFile = file,
                    pieceTree = pieceTree,
                    charset = state.encoding,
                    delayMs = preferences.autoSaveIntervalMs
                )
            }
        }
    }

    /**
     * 执行按指定编码重新加载
     */
    suspend fun reloadWithCharset(newCharset: Charset) = withContext(Dispatchers.IO) {
        if (file == null || !file.exists()) return@withContext
        state.encoding = newCharset
        // 重新构建内存映射并刷新当前页视口
        pieceTree = LargeTextPieceTree(file)
        pageManager = VirtualPageManager(pieceTree, preferences.autoPaginationThresholdChars)
        val freshContent = pageManager.loadCurrentPageContent()

        withContext(Dispatchers.Main) {
            boundEditorView?.setText(freshContent)
            state.isDirty = false
            state.totalPages = pageManager.getTotalPages()
            state.activePageIndex = pageManager.currentPageIndex
        }
    }

    /**
     * 手动触发强制同步保存
     */
    suspend fun save(): Boolean = withContext(Dispatchers.IO) {
        if (file == null || state.isReadOnly) return@withContext false
        boundEditorView?.let { editor ->
            pageManager.commitCurrentPageEdits(editor.text)
        }
        val success = autoSaveCoordinator?.saveImmediately(file, pieceTree, state.encoding) ?: false
        if (success) {
            withContext(Dispatchers.Main) { state.isDirty = false }
        }
        success
    }

    fun release() {
        lspBridge?.detach()
        fileWatcher?.stopWatching()
        autoSaveCoordinator?.release()
        boundEditorView = null
        scope.cancel()
    }
}