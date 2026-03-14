package android.zero.studio.lsp.clangd.project

import android.content.Context
import android.util.Log
import android.zero.studio.lsp.clangd.LspHealthMonitor
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * LSP 编辑器绑定
 * 
 * 简化 CodeEditor 与 LSP 项目系统的集成：
 * - 自动管理项目和编辑器的生命周期
 * - 提供简单的 bind/unbind API
 */
class LspEditorBinding private constructor(
    private val context: Context,
    private val codeEditor: CodeEditor,
    private val filePath: String,
    private val projectPath: String
) {
    companion object {
        private const val TAG = "LspEditorBinding"
        
        /**
         * 绑定编辑器到 LSP 系统
         * 
         * @param context 上下文
         * @param editor CodeEditor 实例
         * @param filePath 文件路径
         * @param projectPath 项目路径（可选，默认为文件所在目录）
         * @return LspEditorBinding 实例，用于后续解绑
         */
        fun bind(
            context: Context,
            editor: CodeEditor,
            filePath: String,
            projectPath: String? = null
        ): LspEditorBinding? {
            val absoluteFilePath = File(filePath).absolutePath
            val effectiveProjectPath = projectPath 
                ?: File(absoluteFilePath).parent 
                ?: return null
            
            // 初始化管理器
            LspProjectManager.initialize()
            
            // 启动健康监控
            LspHealthMonitor.start(context)
            
            val binding = LspEditorBinding(
                context = context.applicationContext,
                codeEditor = editor,
                filePath = absoluteFilePath,
                projectPath = effectiveProjectPath
            )
            
            binding.start()
            return binding
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    @Volatile
    private var lspEditor: LspEditor? = null
    
    @Volatile
    private var isDisposed = false
    
    /**
     * 获取 LSP 编辑器
     */
    fun getLspEditor(): LspEditor? = lspEditor
    
    /**
     * 获取项目
     */
    fun getProject(): LspProject? = LspProjectManager.getProject(projectPath)
    
    private fun start() {
        scope.launch {
            try {
                // 打开项目
                val project = LspProjectManager.openProject(context, projectPath)
                
                if (isDisposed) return@launch
                
                // 创建编辑器
                val editor = project.getOrCreateEditor(filePath)
                editor.editor = codeEditor
                
                if (isDisposed) {
                    editor.dispose()
                    return@launch
                }
                
                // 连接并打开文档
                if (editor.connect()) {
                    editor.openDocument()
                }
                
                lspEditor = editor
                Log.i(TAG, "Binding complete: $filePath")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind: $filePath", e)
            }
        }
    }
    
    /**
     * 解绑
     */
    fun unbind() {
        if (isDisposed) return
        isDisposed = true
        
        scope.launch {
            try {
                lspEditor?.dispose()
                lspEditor = null
                Log.i(TAG, "Unbound: $filePath")
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding: $filePath", e)
            }
        }.invokeOnCompletion {
            scope.cancel()
        }
    }
    
    /**
     * 立即同步文档
     */
    suspend fun flushSync() {
        lspEditor?.flushPendingSync()
    }
    
    /**
     * 保存文档
     */
    suspend fun saveDocument() {
        lspEditor?.saveDocument()
    }
}

/**
 * CodeEditor 扩展函数：绑定到 LSP
 */
fun CodeEditor.bindToLsp(
    context: Context,
    filePath: String,
    projectPath: String? = null
): LspEditorBinding? {
    return LspEditorBinding.bind(context, this, filePath, projectPath)
}
