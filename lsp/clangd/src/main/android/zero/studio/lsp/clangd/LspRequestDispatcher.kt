package android.zero.studio.lsp.clangd

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.zero.studio.lsp.clangd.model.CompletionResult
import android.zero.studio.lsp.clangd.model.HoverResult
import android.zero.studio.lsp.clangd.model.Location
import android.zero.studio.lsp.clangd.project.LspProjectManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * LSP 请求调度器
 * 
 * 职责：
 * - 同一文件复用单个调度通道，串行发送请求
 * - Completion 拥有更高优先级
 * - Hover 限流，避免频繁请求
 */
object LspRequestDispatcher {

    private const val TAG = "LspRequestDispatcher"
    private const val MIN_HOVER_INTERVAL_MS = 120L
    private const val HOVER_COMPLETION_SUPPRESS_MS = 800L
    private const val HOVER_TYPING_COOLDOWN_MS = 600L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestChannels = ConcurrentHashMap<String, FileRequestChannel>()
    private val hoverLimiter = HoverRateLimiter(MIN_HOVER_INTERVAL_MS)
    private val completionActivity = ConcurrentHashMap<String, AtomicLong>()
    private val typingActivity = ConcurrentHashMap<String, AtomicLong>()
    private val definitionJobs = ConcurrentHashMap<String, Job>()
    private val referenceJobs = ConcurrentHashMap<String, Job>()

    // ========================================================================
    // 请求类型
    // ========================================================================
    
    private sealed class RequestType<T>(val label: String, val priority: Int) {
        object Hover : RequestType<HoverResult?>("Hover", priority = 1)
        object Completion : RequestType<CompletionResult?>("Completion", priority = 2)
    }

    // ========================================================================
    // 公开 API
    // ========================================================================
    
    /**
     * 通知文档内容变更
     */
    fun notifyDocumentChange(filePath: String) {
        val fileUri = buildUri(filePath)
        val now = SystemClock.elapsedRealtime()
        typingActivity.computeIfAbsent(fileUri) { AtomicLong(now) }.set(now)
    }

    /**
     * 请求 Hover
     */
    fun requestHover(
        filePath: String,
        line: Int,
        column: Int,
        workDir: String?,
        onResult: (HoverResult?) -> Unit
    ) {
        val fileUri = buildUri(filePath)
        val caretSignature = "$line:$column"
        
        if (shouldSuppressHover(fileUri, caretSignature)) return
        
        val identity = buildIdentity(filePath, line, column)
        if (!hoverLimiter.shouldAllow(fileUri, caretSignature)) {
            Log.d(TAG, "Hover throttled key=$fileUri caret=$caretSignature")
            return
        }
        
        submitToChannel(
            fileUri = fileUri,
            type = RequestType.Hover,
            identity = identity,
            blockProvider = {
                if (!ensureInitialized(workDir)) return@submitToChannel null
                LspService.requestHover(fileUri, line, column).also { result ->
                    if (result == null) {
                        Log.d(TAG, "Hover result null uri=$fileUri pos=$line:$column")
                    }
                }
            },
            onResult = onResult
        )
    }

    /**
     * 请求 Completion
     */
    fun requestCompletion(
        filePath: String,
        line: Int,
        column: Int,
        workDir: String?,
        onResult: (CompletionResult?) -> Unit,
        timeoutMs: Long? = null,
        triggerCharacter: String? = null
    ) {
        val fileUri = buildUri(filePath)
        markCompletionActivity(fileUri)
        
        if (timeoutMs != null) {
            Log.d(TAG, "Completion timeout override ${timeoutMs}ms for uri=$fileUri pos=$line:$column")
        }
        
        submitToChannel(
            fileUri = fileUri,
            type = RequestType.Completion,
            identity = buildIdentity(filePath, line, column),
            blockProvider = {
                if (!ensureInitialized(workDir)) return@submitToChannel null
                LspProjectManager.getEditorForFile(filePath)?.flushPendingSync()
                LspService.requestCompletion(
                    fileUri = fileUri,
                    line = line,
                    character = column,
                    triggerCharacter = triggerCharacter,
                    timeoutMs = timeoutMs
                ).also { result ->
                    if (result == null) {
                        Log.w(TAG, "Completion result null uri=$fileUri pos=$line:$column")
                    }
                }
            },
            onResult = onResult,
            dropPendingHover = true
        )
    }

    /**
     * 请求 Definition
     */
    fun requestDefinition(
        filePath: String,
        line: Int,
        column: Int,
        workDir: String?,
        onResult: (List<Location>?) -> Unit
    ) {
        val fileUri = buildUri(filePath)
        val key = "$fileUri:$line:$column"
        
        launchRequest(
            key = key,
            jobs = definitionJobs,
            workDir = workDir,
            methodLabel = "Definition",
            request = { LspService.requestDefinition(fileUri, line, column) },
            onResult = onResult
        )
    }

    /**
     * 请求 References
     */
    fun requestReferences(
        filePath: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
        workDir: String?,
        onResult: (List<Location>?) -> Unit
    ) {
        val fileUri = buildUri(filePath)
        val key = "$fileUri:$line:$column"
        
        launchRequest(
            key = key,
            jobs = referenceJobs,
            workDir = workDir,
            methodLabel = "References",
            request = { LspService.requestReferences(fileUri, line, column, includeDeclaration) },
            onResult = onResult
        )
    }


    // ========================================================================
    // 内部实现
    // ========================================================================
    
    private fun buildUri(filePath: String): String = Uri.fromFile(File(filePath)).toString()

    private fun buildIdentity(filePath: String, line: Int, column: Int): String {
        val version = LspProjectManager.getEditorForFile(filePath)?.currentVersion() ?: -1
        return "$line:$column:$version"
    }

    private fun shouldSuppressHover(fileUri: String, caretSignature: String): Boolean {
        val channel = requestChannels[fileUri]
        val hasCompletionInFlight = channel?.hasActiveOrPending(RequestType.Completion) == true
        if (hasCompletionInFlight) {
            Log.d(TAG, "Hover suppressed due to active completion key=$fileUri")
            return true
        }
        
        val now = SystemClock.elapsedRealtime()
        val lastActivity = completionActivity[fileUri]?.get() ?: 0L
        if (lastActivity != 0L && now - lastActivity < HOVER_COMPLETION_SUPPRESS_MS) {
            Log.d(TAG, "Hover suppressed due to recent completion key=$fileUri")
            return true
        }
        
        val lastTyping = typingActivity[fileUri]?.get() ?: 0L
        if (lastTyping != 0L && now - lastTyping < HOVER_TYPING_COOLDOWN_MS) {
            Log.d(TAG, "Hover suppressed due to typing cooldown key=$fileUri")
            return true
        }
        return false
    }

    private fun markCompletionActivity(fileUri: String) {
        val now = SystemClock.elapsedRealtime()
        completionActivity.computeIfAbsent(fileUri) { AtomicLong(now) }.set(now)
    }

    private fun <T> submitToChannel(
        fileUri: String,
        type: RequestType<T>,
        identity: String,
        blockProvider: suspend () -> T?,
        onResult: (T?) -> Unit,
        dropPendingHover: Boolean = false
    ) {
        val channel = requestChannels.computeIfAbsent(fileUri) { key ->
            FileRequestChannel(key, scope) { releasedKey, worker ->
                requestChannels.remove(releasedKey, worker)
            }
        }
        if (dropPendingHover) {
            channel.cancelPending(RequestType.Hover)
        }
        channel.submit(type, identity, blockProvider, onResult)
    }

    private fun <T> launchRequest(
        key: String,
        jobs: ConcurrentHashMap<String, Job>,
        workDir: String?,
        methodLabel: String,
        request: suspend () -> T?,
        onResult: (T?) -> Unit
    ) {
        jobs[key]?.cancel()
        jobs[key] = scope.launch {
            try {
                Log.d(TAG, "Launching $methodLabel request key=$key")
                if (!ensureInitialized(workDir)) {
                    Log.w(TAG, "LSP not available for $methodLabel key=$key")
                    withContext(Dispatchers.Main) { onResult(null) }
                    return@launch
                }
                
                val result = runCatching { request() }
                    .onFailure {
                        if (it !is CancellationException) {
                            Log.e(TAG, "$methodLabel request failed", it)
                        }
                    }
                    .getOrNull()
                    
                withContext(Dispatchers.Main) { onResult(result) }
            } finally {
                jobs.remove(key)
            }
        }
    }

    private suspend fun ensureInitialized(workDir: String?): Boolean {
        if (LspService.isInitialized) return true
        val initialized = LspService.initialize(workDir = workDir ?: "/")
        if (!initialized) {
            Log.w(TAG, "LspService initialize failed for workDir=$workDir")
        }
        return initialized
    }

    // ========================================================================
    // Hover 限流器
    // ========================================================================
    
    private class HoverRateLimiter(private val minIntervalMs: Long) {
        private data class HoverState(var signature: String, var timestamp: Long)
        private val states = ConcurrentHashMap<String, HoverState>()

        fun shouldAllow(key: String, caretSignature: String): Boolean {
            val now = SystemClock.elapsedRealtime()
            val state = states[key]
            if (state == null || state.signature != caretSignature || now - state.timestamp >= minIntervalMs) {
                states[key] = HoverState(caretSignature, now)
                return true
            }
            return false
        }
    }

    // ========================================================================
    // 请求通道
    // ========================================================================
    
    private class FileRequestChannel(
        private val key: String,
        private val scope: CoroutineScope,
        private val onIdle: (String, FileRequestChannel) -> Unit
    ) {
        private var job: Job? = null
        private var currentTask: MethodTask<*>? = null
        private val pendingTasks = HashMap<RequestType<*>, MethodTask<*>>()
        private val sequence = AtomicLong(0)

        fun cancelPending(type: RequestType<*>) {
            val removed = synchronized(this) { pendingTasks.remove(type) }
            removed?.markSkipped()
        }

        fun <T> submit(
            type: RequestType<T>,
            identity: String,
            block: suspend () -> T?,
            callback: (T?) -> Unit
        ) {
            var replacedTask: MethodTask<*>? = null
            var appended = false
            var needsStart = false

            synchronized(this) {
                when {
                    currentTask?.matches(type, identity) == true -> {
                        @Suppress("UNCHECKED_CAST")
                        (currentTask as MethodTask<T>).addCallback(callback)
                        appended = true
                    }
                    pendingTasks[type]?.identity == identity -> {
                        @Suppress("UNCHECKED_CAST")
                        (pendingTasks[type] as MethodTask<T>).addCallback(callback)
                        appended = true
                    }
                    else -> {
                        val task = MethodTask(type, identity, type.label, type.priority, sequence.incrementAndGet(), block, callback)
                        replacedTask = pendingTasks.put(type, task)
                        if (job == null) needsStart = true
                    }
                }
            }

            if (appended) return
            replacedTask?.markSkipped()
            if (needsStart) startLoop()
        }

        fun hasActiveOrPending(type: RequestType<*>): Boolean {
            synchronized(this) {
                return currentTask?.type == type || pendingTasks.containsKey(type)
            }
        }

        private fun startLoop() {
            synchronized(this) {
                if (job == null) {
                    job = scope.launch { runLoop() }
                }
            }
        }

        private suspend fun runLoop() {
            while (true) {
                val next = synchronized(this) {
                    val candidate = pendingTasks.values.maxWithOrNull(TASK_COMPARATOR)
                    if (candidate == null) {
                        currentTask = null
                        job = null
                        null
                    } else {
                        pendingTasks.remove(candidate.type)
                        currentTask = candidate
                        candidate
                    }
                } ?: break
                next.run()
            }
            onIdle(key, this)
        }

        companion object {
            private val TASK_COMPARATOR = Comparator<MethodTask<*>> { a, b ->
                when {
                    a.priority != b.priority -> b.priority - a.priority
                    a.sequence == b.sequence -> 0
                    a.sequence < b.sequence -> 1
                    else -> -1
                }
            }
        }
    }

    // ========================================================================
    // 方法任务
    // ========================================================================
    
    private class MethodTask<T>(
        val type: RequestType<T>,
        val identity: String,
        private val label: String,
        val priority: Int,
        val sequence: Long,
        private val block: suspend () -> T?,
        initialCallback: (T?) -> Unit
    ) {
        private val callbacks = mutableListOf(initialCallback)

        fun matches(otherType: RequestType<*>, otherIdentity: String): Boolean {
            return type == otherType && identity == otherIdentity
        }

        fun addCallback(callback: (T?) -> Unit) {
            callbacks.add(callback)
        }

        fun markSkipped() {
            Log.d(TAG, "$label request skipped identity=$identity")
        }

        suspend fun run() {
            val result = runCatching { block() }
                .onFailure { Log.e(TAG, "$label request failed identity=$identity", it) }
                .getOrNull()
            val snapshot = callbacks.toList().also { callbacks.clear() }
            withContext(Dispatchers.Main) {
                snapshot.forEach { callback -> runCatching { callback(result) } }
            }
        }
    }
}
