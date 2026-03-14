package android.zero.studio.lsp.clangd.project

import android.content.Context
import android.util.Log
import android.zero.studio.lsp.clangd.LspResultCache
import android.zero.studio.lsp.clangd.LspService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * LSP 项目管理器
 * 
 * 全局单例，管理所有 LSP 项目的生命周期：
 * - 项目的创建、获取、切换、关闭
 * - 当前活动项目的管理
 * - 项目切换时的资源清理
 */
object LspProjectManager {
    
    private const val TAG = "LspProjectManager"
    
    // 所有项目 (projectPath -> LspProject)
    private val projects = ConcurrentHashMap<String, LspProject>()
    
    // 当前活动项目
    @Volatile
    private var currentProject: LspProject? = null
    
    // 操作锁
    private val operationMutex = Mutex()
    
    // 协程作用域
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 监听器
    private val projectChangeListeners = CopyOnWriteArraySet<ProjectChangeListener>()
    
    // 是否已初始化 LSP 重启监听
    @Volatile
    private var lspListenerInitialized = false
    
    /**
     * 初始化管理器
     */
    fun initialize() {
        if (lspListenerInitialized) return
        
        // 监听 LSP 服务初始化状态
        LspService.addInitializationListener { initialized ->
            if (initialized) {
                // clangd 重启后，重新发送所有打开的文档
                managerScope.launch {
                    resendAllDocuments()
                }
            }
        }
        
        lspListenerInitialized = true
        Log.i(TAG, "LspProjectManager initialized")
    }
    
    /**
     * 获取当前项目
     */
    fun getCurrentProject(): LspProject? = currentProject
    
    /**
     * 获取项目
     */
    fun getProject(projectPath: String): LspProject? {
        val absolutePath = File(projectPath).absolutePath
        return projects[absolutePath]
    }
    
    /**
     * 创建项目
     */
    fun createProject(context: Context, projectPath: String): LspProject {
        val absolutePath = File(projectPath).absolutePath
        
        val existing = projects[absolutePath]
        if (existing != null) {
            Log.d(TAG, "Reusing existing project: $absolutePath")
            return existing
        }
        
        val project = LspProject(context.applicationContext, absolutePath)
        projects[absolutePath] = project
        Log.i(TAG, "Created project: $absolutePath")
        return project
    }
    
    /**
     * 获取或创建项目
     */
    fun getOrCreateProject(context: Context, projectPath: String): LspProject {
        return getProject(projectPath) ?: createProject(context, projectPath)
    }

    /**
     * 打开项目（设为当前项目）
     * 
     * 如果已有其他项目打开，会先关闭旧项目的资源
     */
    suspend fun openProject(context: Context, projectPath: String): LspProject = operationMutex.withLock {
        val absolutePath = File(projectPath).absolutePath
        
        // 检查是否是同一个项目
        val current = currentProject
        if (current != null && current.projectPath == absolutePath) {
            Log.d(TAG, "Project already open: $absolutePath")
            return current
        }
        
        // 关闭旧项目
        if (current != null) {
            Log.i(TAG, "Switching project: ${current.projectPath} -> $absolutePath")
            closeProjectInternal(current)
        }
        
        // 创建或获取新项目
        val project = getOrCreateProject(context, absolutePath)
        
        // 初始化项目
        val initialized = project.initialize()
        if (!initialized) {
            Log.e(TAG, "Failed to initialize project: $absolutePath")
        }
        
        currentProject = project
        notifyProjectChanged(null, project)
        
        Log.i(TAG, "Project opened: $absolutePath")
        return project
    }
    
    /**
     * 关闭项目
     */
    suspend fun closeProject(projectPath: String) = operationMutex.withLock {
        val absolutePath = File(projectPath).absolutePath
        val project = projects[absolutePath] ?: return
        
        closeProjectInternal(project)
        
        // 如果关闭的是当前项目，清除引用
        if (currentProject?.projectPath == absolutePath) {
            val oldProject = currentProject
            currentProject = null
            notifyProjectChanged(oldProject, null)
        }
    }
    
    /**
     * 关闭当前项目
     */
    suspend fun closeCurrentProject() = operationMutex.withLock {
        val project = currentProject ?: return
        
        closeProjectInternal(project)
        
        val oldProject = currentProject
        currentProject = null
        notifyProjectChanged(oldProject, null)
    }
    
    /**
     * 内部关闭项目
     */
    private suspend fun closeProjectInternal(project: LspProject) {
        Log.i(TAG, "Closing project: ${project.projectPath}")
        
        // 释放项目资源
        project.dispose()
        
        // 从映射中移除
        projects.remove(project.projectPath)
        
        // 清理项目相关缓存
        LspResultCache.invalidateProject(project.projectPath)
        
        Log.i(TAG, "Project closed: ${project.projectPath}")
    }
    
    /**
     * 关闭所有项目
     */
    suspend fun closeAllProjects() = operationMutex.withLock {
        Log.i(TAG, "Closing all projects")
        
        val projectList = projects.values.toList()
        projects.clear()
        
        projectList.forEach { project ->
            try {
                project.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing project: ${project.projectPath}", e)
            }
        }
        
        val oldProject = currentProject
        currentProject = null
        
        if (oldProject != null) {
            notifyProjectChanged(oldProject, null)
        }
        
        // 清理所有缓存
        LspResultCache.clearAll()
        
        Log.i(TAG, "All projects closed")
    }
    
    /**
     * 重新发送所有文档（clangd 重启后）
     */
    private suspend fun resendAllDocuments() {
        val project = currentProject ?: return
        
        Log.d(TAG, "Resending all documents for project: ${project.projectPath}")
        
        project.getOpenedFiles().forEach { filePath ->
            project.getEditor(filePath)?.resendDocument()
        }
    }
    
    /**
     * 获取所有项目
     */
    fun getAllProjects(): List<LspProject> = projects.values.toList()
    
    /**
     * 获取项目数量
     */
    fun getProjectCount(): Int = projects.size
    
    /**
     * 检查是否有项目打开
     */
    fun hasOpenProject(): Boolean = currentProject != null
    
    /**
     * 根据文件路径查找所属项目
     */
    fun findProjectForFile(filePath: String): LspProject? {
        val absolutePath = File(filePath).absolutePath
        
        // 优先检查当前项目
        currentProject?.let { project ->
            if (project.containsFile(absolutePath)) {
                return project
            }
        }
        
        // 检查所有项目
        return projects.values.find { it.containsFile(absolutePath) }
    }
    
    // ========================================================================
    // 监听器
    // ========================================================================
    
    /**
     * 添加项目变化监听器
     */
    fun addProjectChangeListener(listener: ProjectChangeListener) {
        projectChangeListeners.add(listener)
    }
    
    /**
     * 移除项目变化监听器
     */
    fun removeProjectChangeListener(listener: ProjectChangeListener) {
        projectChangeListeners.remove(listener)
    }
    
    private fun notifyProjectChanged(oldProject: LspProject?, newProject: LspProject?) {
        projectChangeListeners.forEach { 
            it.onProjectChanged(oldProject, newProject) 
        }
    }
    
    /**
     * 项目变化监听器
     */
    fun interface ProjectChangeListener {
        fun onProjectChanged(oldProject: LspProject?, newProject: LspProject?)
    }
    
    // ========================================================================
    // 便捷方法
    // ========================================================================
    
    /**
     * 为文件创建编辑器（自动关联到当前项目或创建新项目）
     */
    suspend fun createEditorForFile(
        context: Context,
        filePath: String,
        projectPath: String? = null
    ): LspEditor? {
        val absoluteFilePath = File(filePath).absolutePath
        
        // 确定项目路径
        val effectiveProjectPath = projectPath 
            ?: File(absoluteFilePath).parent 
            ?: return null
        
        // 获取或打开项目
        val project = if (currentProject?.projectPath == effectiveProjectPath) {
            currentProject!!
        } else {
            openProject(context, effectiveProjectPath)
        }
        
        // 创建编辑器
        return project.getOrCreateEditor(absoluteFilePath)
    }
    
    /**
     * 获取当前项目的编辑器
     */
    fun getEditorForFile(filePath: String): LspEditor? {
        val absolutePath = File(filePath).absolutePath
        return currentProject?.getEditor(absolutePath)
            ?: findProjectForFile(absolutePath)?.getEditor(absolutePath)
    }
}
