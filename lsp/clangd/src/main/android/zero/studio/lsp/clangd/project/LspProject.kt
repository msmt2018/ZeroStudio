package android.zero.studio.lsp.clangd.project

import android.content.Context
import android.util.Log
import android.zero.studio.lsp.clangd.LspBinaryResolver
import android.zero.studio.lsp.clangd.LspResultCache
import android.zero.studio.lsp.clangd.LspService
import android.zero.studio.lsp.clangd.model.DiagnosticItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * LSP 项目
 * 
 * 管理单个项目的 LSP 生命周期，包括：
 * - 项目初始化和关闭
 * - 编辑器管理（创建、获取、移除）
 * - 诊断信息管理
 * - 资源清理
 */
class LspProject(
    val context: Context,
    val projectPath: String
) {
    companion object {
        private const val TAG = "LspProject"
    }

    // 项目 URI
    val projectUri: String = "file://$projectPath"
    
    // 协程作用域
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 编辑器映射 (filePath -> LspEditor)
    private val editors = ConcurrentHashMap<String, LspEditor>()
    
    // 诊断信息容器
    val diagnosticsContainer = DiagnosticsContainer()
    
    // 项目状态
    @Volatile
    var status: ProjectStatus = ProjectStatus.CREATED
        private set
    
    // 初始化锁
    private val initMutex = Mutex()
    
    // clangd 路径
    private var clangdPath: String? = null
    
    // 监听器
    private val statusListeners = CopyOnWriteArraySet<ProjectStatusListener>()
    
    /**
     * 初始化项目
     */
    suspend fun initialize(): Boolean = initMutex.withLock {
        if (status == ProjectStatus.INITIALIZED) {
            Log.d(TAG, "Project already initialized: $projectPath")
            return true
        }
        
        status = ProjectStatus.INITIALIZING
        notifyStatusChanged()
        
        // 解析 clangd 路径
        clangdPath = LspBinaryResolver.resolve(context)
        if (clangdPath.isNullOrBlank()) {
            Log.e(TAG, "clangd binary not found")
            status = ProjectStatus.ERROR
            notifyStatusChanged()
            return false
        }
        
        LspService.setClangdBinary(clangdPath)
        
        // 初始化 LSP 服务
        val success = LspService.initialize(
            clangdPath = clangdPath!!,
            workDir = projectPath
        )
        
        if (success) {
            status = ProjectStatus.INITIALIZED
            Log.i(TAG, "Project initialized: $projectPath")
        } else {
            status = ProjectStatus.ERROR
            Log.e(TAG, "Failed to initialize project: $projectPath")
        }
        
        notifyStatusChanged()
        return success
    }

    /**
     * 创建编辑器
     */
    fun createEditor(filePath: String): LspEditor {
        val absolutePath = File(filePath).absolutePath
        val existing = editors[absolutePath]
        if (existing != null) {
            Log.d(TAG, "Reusing existing editor: $absolutePath")
            return existing
        }
        
        val editor = LspEditor(this, absolutePath)
        editors[absolutePath] = editor
        Log.d(TAG, "Created editor: $absolutePath")
        return editor
    }
    
    /**
     * 获取编辑器
     */
    fun getEditor(filePath: String): LspEditor? {
        val absolutePath = File(filePath).absolutePath
        return editors[absolutePath]
    }
    
    /**
     * 获取或创建编辑器
     */
    fun getOrCreateEditor(filePath: String): LspEditor {
        return getEditor(filePath) ?: createEditor(filePath)
    }
    
    /**
     * 移除编辑器
     */
    fun removeEditor(filePath: String) {
        val absolutePath = File(filePath).absolutePath
        editors.remove(absolutePath)?.also { editor ->
            coroutineScope.launch {
                editor.dispose()
            }
            Log.d(TAG, "Removed editor: $absolutePath")
        }
    }
    
    /**
     * 移除编辑器（内部使用）
     */
    internal fun removeEditor(editor: LspEditor) {
        editors.remove(editor.filePath)
    }
    
    /**
     * 关闭所有编辑器
     */
    suspend fun closeAllEditors() {
        Log.d(TAG, "Closing all editors for project: $projectPath")
        val editorList = editors.values.toList()
        editors.clear()
        
        editorList.forEach { editor ->
            try {
                editor.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing editor: ${editor.filePath}", e)
            }
        }
    }
    
    /**
     * 获取所有打开的文件
     */
    fun getOpenedFiles(): List<String> {
        return editors.keys.toList()
    }
    
    /**
     * 获取编辑器数量
     */
    fun getEditorCount(): Int = editors.size
    
    /**
     * 添加状态监听器
     */
    fun addStatusListener(listener: ProjectStatusListener) {
        statusListeners.add(listener)
        // 立即通知当前状态
        listener.onStatusChanged(this, status)
    }
    
    /**
     * 移除状态监听器
     */
    fun removeStatusListener(listener: ProjectStatusListener) {
        statusListeners.remove(listener)
    }
    
    private fun notifyStatusChanged() {
        statusListeners.forEach { it.onStatusChanged(this, status) }
    }
    
    /**
     * 释放项目资源
     */
    suspend fun dispose() {
        if (status == ProjectStatus.DISPOSED) {
            Log.d(TAG, "Project already disposed: $projectPath")
            return
        }
        
        Log.i(TAG, "Disposing project: $projectPath")
        status = ProjectStatus.DISPOSING
        notifyStatusChanged()
        
        // 关闭所有编辑器
        closeAllEditors()
        
        // 清理诊断信息
        diagnosticsContainer.clear()
        
        // 清理缓存
        LspResultCache.invalidateProject(projectPath)
        
        // 关闭 LSP 服务
        LspService.shutdown()
        
        // 取消协程
        coroutineScope.cancel()
        
        // 清理监听器
        statusListeners.clear()
        
        status = ProjectStatus.DISPOSED
        Log.i(TAG, "Project disposed: $projectPath")
    }
    
    /**
     * 检查文件是否属于此项目
     */
    fun containsFile(filePath: String): Boolean {
        val absolutePath = File(filePath).absolutePath
        return absolutePath.startsWith(projectPath)
    }
    
    override fun toString(): String {
        return "LspProject(path=$projectPath, status=$status, editors=${editors.size})"
    }
}

/**
 * 项目状态
 */
enum class ProjectStatus {
    CREATED,
    INITIALIZING,
    INITIALIZED,
    DISPOSING,
    DISPOSED,
    ERROR
}

/**
 * 项目状态监听器
 */
fun interface ProjectStatusListener {
    fun onStatusChanged(project: LspProject, status: ProjectStatus)
}

/**
 * 诊断信息容器
 */
class DiagnosticsContainer {
    private val diagnostics = ConcurrentHashMap<String, List<DiagnosticItem>>()
    private val listeners = CopyOnWriteArraySet<DiagnosticsListener>()
    
    fun setDiagnostics(fileUri: String, items: List<DiagnosticItem>) {
        diagnostics[fileUri] = items
        listeners.forEach { it.onDiagnosticsUpdated(fileUri, items) }
    }
    
    fun getDiagnostics(fileUri: String): List<DiagnosticItem> {
        return diagnostics[fileUri] ?: emptyList()
    }
    
    fun getAllDiagnostics(): Map<String, List<DiagnosticItem>> {
        return diagnostics.toMap()
    }
    
    fun clearFile(fileUri: String) {
        diagnostics.remove(fileUri)
        listeners.forEach { it.onDiagnosticsUpdated(fileUri, emptyList()) }
    }
    
    fun clear() {
        val files = diagnostics.keys.toList()
        diagnostics.clear()
        files.forEach { fileUri ->
            listeners.forEach { it.onDiagnosticsUpdated(fileUri, emptyList()) }
        }
    }
    
    fun addListener(listener: DiagnosticsListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: DiagnosticsListener) {
        listeners.remove(listener)
    }
    
    fun interface DiagnosticsListener {
        fun onDiagnosticsUpdated(fileUri: String, diagnostics: List<DiagnosticItem>)
    }
}
