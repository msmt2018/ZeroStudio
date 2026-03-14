package android.zero.studio.lsp.clangd

import android.zero.studio.lsp.clangd.model.CompletionResult
import java.util.LinkedHashMap

/**
 * LSP 结果缓存
 * 
 * 缓存补全结果，避免相同上下文反复请求 clangd
 */
object LspResultCache {

    private const val MAX_COMPLETION_ENTRIES = 128

    private data class CompletionKey(
        val filePath: String,
        val line: Int,
        val identifierStart: Int,
        val identifierHash: Int,
        val scopeHash: Int
    )

    private data class CompletionEntry(
        val identifierSnapshot: String,
        val scopeSignature: String,
        val version: Int,
        val result: CompletionResult
    )

    private val completionCache = object : LinkedHashMap<CompletionKey, CompletionEntry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CompletionKey, CompletionEntry>?): Boolean {
            return size > MAX_COMPLETION_ENTRIES
        }
    }

    private val lastCompletionByFile = HashMap<String, CompletionResult>()

    @Synchronized
    fun getCompletion(
        filePath: String,
        line: Int,
        identifierStart: Int,
        identifierSnapshot: String,
        scopeSignature: String,
        documentVersion: Int
    ): CompletionResult? {
        val key = CompletionKey(
            filePath = filePath,
            line = line,
            identifierStart = identifierStart,
            identifierHash = identifierSnapshot.hashCode(),
            scopeHash = scopeSignature.hashCode()
        )
        val entry = completionCache[key] ?: return null
        
        val mismatchedContext = entry.identifierSnapshot != identifierSnapshot ||
            entry.scopeSignature != scopeSignature
            
        return if (mismatchedContext) {
            completionCache.remove(key)
            null
        } else {
            entry.result
        }
    }

    @Synchronized
    fun putCompletion(
        filePath: String,
        line: Int,
        identifierStart: Int,
        identifierSnapshot: String,
        scopeSignature: String,
        documentVersion: Int,
        result: CompletionResult
    ) {
        val key = CompletionKey(
            filePath = filePath,
            line = line,
            identifierStart = identifierStart,
            identifierHash = identifierSnapshot.hashCode(),
            scopeHash = scopeSignature.hashCode()
        )
        completionCache[key] = CompletionEntry(
            identifierSnapshot = identifierSnapshot,
            scopeSignature = scopeSignature,
            version = documentVersion,
            result = result
        )
        lastCompletionByFile[filePath] = result
    }

    @Synchronized
    fun invalidateFile(filePath: String) {
        val iterator = completionCache.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.filePath == filePath) {
                iterator.remove()
            }
        }
        lastCompletionByFile.remove(filePath)
    }

    @Synchronized
    fun getLastCompletion(filePath: String): CompletionResult? = lastCompletionByFile[filePath]
    
    /**
     * 清理项目相关的所有缓存
     */
    @Synchronized
    fun invalidateProject(projectPath: String) {
        val iterator = completionCache.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.filePath.startsWith(projectPath)) {
                iterator.remove()
            }
        }
        
        val fileIterator = lastCompletionByFile.entries.iterator()
        while (fileIterator.hasNext()) {
            if (fileIterator.next().key.startsWith(projectPath)) {
                fileIterator.remove()
            }
        }
    }
    
    /**
     * 清理所有缓存
     */
    @Synchronized
    fun clearAll() {
        completionCache.clear()
        lastCompletionByFile.clear()
    }
}
