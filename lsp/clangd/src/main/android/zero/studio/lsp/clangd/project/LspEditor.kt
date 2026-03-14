package android.zero.studio.lsp.clangd.project

import android.net.Uri
import android.util.Log
import android.zero.studio.lsp.clangd.LspRequestDispatcher
import android.zero.studio.lsp.clangd.LspResultCache
import android.zero.studio.lsp.clangd.LspService
import android.zero.studio.lsp.clangd.model.CompletionResult
import android.zero.studio.lsp.clangd.model.HoverResult
import android.zero.studio.lsp.clangd.model.Location
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference

/**
 * LSP 编辑器
 * 
 * 管理单个文件的 LSP 连接，包括：
 * - 文档同步（didOpen、didChange、didClose）
 * - LSP 请求（completion、hover、definition、references）
 * - 编辑器事件监听
 */
class LspEditor(
    val project: LspProject,
    val filePath: String
) {
    companion object {
        private const val TAG = "LspEditor"
        private const val SYNC_DELAY_MS = 300L
    }
    
    // 文件 URI
    val fileUri: String = Uri.fromFile(File(filePath)).toString()
    
    // 文件扩展名
    val fileExt: String = File(filePath).extension.lowercase()
    
    // 编辑器弱引用
    private var editorRef: WeakReference<CodeEditor?> = WeakReference(null)
    
    // 事件订阅
    private var contentChangeSubscription: SubscriptionReceipt<ContentChangeEvent>? = null
    
    // 文档状态
    @Volatile
    var isOpened: Boolean = false
        private set
    
    @Volatile
    var isConnected: Boolean = false
        private set
    
    @Volatile
    private var isDisposed: Boolean = false
    
    // 文档版本
    @Volatile
    private var version: Int = 1
    
    // 最后的文档快照
    @Volatile
    private var lastSnapshot: String? = null
    
    // 同步任务
    private var pendingSyncJob: Job? = null
    
    // 操作锁
    private val operationMutex = Mutex()
    
    /**
     * 绑定编辑器
     */
    var editor: CodeEditor?
        get() = editorRef.get()
        set(value) {
            if (value == null) {
                throw IllegalArgumentException("Editor cannot be null")
            }
            
            // 取消旧的订阅
            contentChangeSubscription?.unsubscribe()
            
            editorRef = WeakReference(value)
            
            // 订阅内容变化事件
            contentChangeSubscription = value.subscribeEvent<ContentChangeEvent> { _, _ ->
                scheduleSync()
            }
            
            Log.d(TAG, "Editor bound: $filePath")
        }
    
    /**
     * 获取编辑器内容
     */
    val editorContent: String
        get() = editor?.text?.toString() ?: ""

    /**
     * 连接到 LSP 服务
     */
    suspend fun connect(): Boolean = operationMutex.withLock {
        if (isDisposed) {
            Log.w(TAG, "Cannot connect disposed editor: $filePath")
            return false
        }
        
        if (isConnected) {
            Log.d(TAG, "Already connected: $filePath")
            return true
        }
        
        // 确保项目已初始化
        if (project.status != ProjectStatus.INITIALIZED) {
            val initialized = project.initialize()
            if (!initialized) {
                Log.e(TAG, "Project not initialized, cannot connect: $filePath")
                return false
            }
        }
        
        isConnected = true
        Log.i(TAG, "Connected: $filePath")
        return true
    }
    
    /**
     * 断开 LSP 连接
     */
    suspend fun disconnect() {
        operationMutex.withLock {
            if (!isConnected) return@withLock
            
            // 关闭文档
            if (isOpened) {
                closeDocument()
            }
            
            isConnected = false
            Log.i(TAG, "Disconnected: $filePath")
        }
    }
    
    /**
     * 打开文档（通知 LSP 服务）
     */
    suspend fun openDocument(): Boolean = operationMutex.withLock {
        if (isDisposed) {
            Log.w(TAG, "Cannot open disposed editor: $filePath")
            return false
        }
        
        if (isOpened) {
            Log.d(TAG, "Document already opened: $filePath")
            return true
        }
        
        if (!isConnected) {
            val connected = withContext(Dispatchers.IO) { 
                operationMutex.withLock { } // 释放锁后重新获取
                connect() 
            }
            if (!connected) return false
        }
        
        val content = withContext(Dispatchers.Main) { editorContent }
        
        return try {
            LspService.didOpenDocument(fileUri, content)
            lastSnapshot = content
            isOpened = true
            LspRequestDispatcher.notifyDocumentChange(filePath)
            Log.i(TAG, "Document opened: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open document: $filePath", e)
            false
        }
    }
    
    /**
     * 关闭文档（通知 LSP 服务）
     */
    suspend fun closeDocument() {
        if (!isOpened) return
        
        pendingSyncJob?.cancel()
        
        try {
            LspService.didCloseDocument(fileUri)
            Log.d(TAG, "Document closed: $filePath")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close document: $filePath", e)
        }
        
        isOpened = false
        lastSnapshot = null
        version = 1
    }
    
    /**
     * 保存文档
     */
    suspend fun saveDocument() {
        if (!isOpened) return
        
        // 立即同步待处理的更改
        flushPendingSync()
        
        Log.d(TAG, "Document saved: $filePath")
    }
    
    /**
     * 调度同步
     */
    private fun scheduleSync() {
        if (isDisposed || !isOpened) return
        
        pendingSyncJob?.cancel()
        pendingSyncJob = project.coroutineScope.launch {
            delay(SYNC_DELAY_MS)
            sendSnapshot()
        }
    }
    
    /**
     * 立即同步
     */
    suspend fun flushPendingSync() {
        if (isDisposed || !isOpened) return
        pendingSyncJob?.cancel()
        sendSnapshot()
        delay(100) // 给 clangd 时间处理
    }
    
    /**
     * 发送文档快照
     */
    private suspend fun sendSnapshot() {
        val snapshot = withContext(Dispatchers.Main) { editorContent }
        
        if (snapshot == lastSnapshot) {
            Log.d(TAG, "No content changes: $filePath")
            return
        }
        
        val nextVersion = ++version
        
        try {
            LspService.didChangeDocument(fileUri, snapshot, nextVersion)
            lastSnapshot = snapshot
            LspRequestDispatcher.notifyDocumentChange(filePath)
            Log.d(TAG, "Document changed: $filePath, version=$nextVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send didChange: $filePath", e)
        }
    }
    
    /**
     * 重新发送文档（clangd 重启后）
     */
    suspend fun resendDocument() {
        if (isDisposed || !isOpened) return
        
        val snapshot = lastSnapshot ?: withContext(Dispatchers.Main) { editorContent }
        
        try {
            LspService.didOpenDocument(fileUri, snapshot)
            version = 1
            lastSnapshot = snapshot
            LspRequestDispatcher.notifyDocumentChange(filePath)
            Log.d(TAG, "Document resent: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resend document: $filePath", e)
        }
    }
    
    /**
     * 获取当前版本
     */
    fun currentVersion(): Int? = if (isOpened && !isDisposed) version else null

    // ========================================================================
    // LSP 请求
    // ========================================================================
    
    /**
     * 请求代码补全
     */
    suspend fun requestCompletion(
        line: Int,
        character: Int,
        triggerCharacter: String? = null,
        timeoutMs: Long? = null
    ): CompletionResult? {
        if (!isOpened || isDisposed) return null
        return LspService.requestCompletion(fileUri, line, character, triggerCharacter, timeoutMs)
    }
    
    /**
     * 请求悬停信息
     */
    suspend fun requestHover(line: Int, character: Int): HoverResult? {
        if (!isOpened || isDisposed) return null
        return LspService.requestHover(fileUri, line, character)
    }
    
    /**
     * 请求跳转定义
     */
    suspend fun requestDefinition(line: Int, character: Int): List<Location>? {
        if (!isOpened || isDisposed) return null
        return LspService.requestDefinition(fileUri, line, character)
    }
    
    /**
     * 请求查找引用
     */
    suspend fun requestReferences(
        line: Int,
        character: Int,
        includeDeclaration: Boolean = true
    ): List<Location>? {
        if (!isOpened || isDisposed) return null
        return LspService.requestReferences(fileUri, line, character, includeDeclaration)
    }
    
    // ========================================================================
    // 诊断信息
    // ========================================================================
    
    /**
     * 获取诊断信息
     */
    fun getDiagnostics() = project.diagnosticsContainer.getDiagnostics(fileUri)
    
    /**
     * 诊断信息更新回调
     */
    fun onDiagnosticsUpdate() {
        // 可以在这里触发 UI 更新
        Log.d(TAG, "Diagnostics updated: $filePath, count=${getDiagnostics().size}")
    }
    
    // ========================================================================
    // 生命周期
    // ========================================================================
    
    /**
     * 释放资源
     */
    suspend fun dispose() {
        if (isDisposed) {
            Log.d(TAG, "Already disposed: $filePath")
            return
        }
        
        Log.d(TAG, "Disposing editor: $filePath")
        isDisposed = true
        
        // 取消待处理的同步
        pendingSyncJob?.cancel()
        
        // 取消事件订阅
        withContext(Dispatchers.Main) {
            contentChangeSubscription?.unsubscribe()
        }
        
        // 关闭文档
        if (isOpened) {
            closeDocument()
        }
        
        // 清理引用
        editorRef.clear()
        
        // 清理缓存
        LspResultCache.invalidateFile(filePath)
        
        // 清理诊断信息
        project.diagnosticsContainer.clearFile(fileUri)
        
        // 从项目中移除
        project.removeEditor(this)
        
        Log.i(TAG, "Editor disposed: $filePath")
    }
    
    override fun toString(): String {
        return "LspEditor(file=$filePath, opened=$isOpened, connected=$isConnected)"
    }
}
