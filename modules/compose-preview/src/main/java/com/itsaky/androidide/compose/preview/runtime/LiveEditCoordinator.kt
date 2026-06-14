/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * v2.2 P3 Live Edit (Hot Reload) 协调器.
 *
 * ## 状态机
 *
 * ```
 *   IDLE ── source change ──▶ DEBOUNCING (300ms)
 *   DEBOUNCING ── ok ──▶ COMPILING ──▶ DEXING ──▶ SWAPPING ──▶ RENDERING ──▶ IDLE
 *   任意阶段 ── fail ──▶ ERROR ──▶ IDLE
 *   任意状态 ── cancel() ──▶ IDLE
 *   任意状态 ── pause() ──▶ IDLE (但忽略 source change)
 * ```
 *
 * ## 关键不变量
 *
 * - **串行化**: 一次 reload 未完成不会接受下一次 (通过 [reloadMutex]).
 * - **失败保留旧 preview**: 编译/dex 错误不会清空 ClassLoader, 旧 dex 继续渲染, 只更新 indicator.
 * - **去抖**: 300ms 内的多次 source change 合并为一次 reload.
 * - **暂停**: [setPaused] 开启后, source change 事件被忽略, 但 manual [forceReload] 仍然有效.
 *
 * ## 协作组件
 *
 * - [SourceChangeWatcher] 提供 source change 事件
 * - [LiveEditCallback] 是外部注入的 "compile + swap + re-render" 实现
 *   (典型实现: Repository.recompile + ComposeClassLoader.swap + ComposableRenderer.render)
 * - [LiveEditStatsRegistry] 记录统计
 * - [LiveEditIndicator] / DebugDrawer 监听 [state] 变化
 */
class LiveEditCoordinator(
    private val watcher: SourceChangeWatcher = SourceChangeWatcher(),
    private val debounceMs: Long = 300L,
) {

    private val LOG = LoggerFactory.getLogger(LiveEditCoordinator::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val collectJobRef = AtomicReference<Job?>(null)
    private val reloadMutex = Mutex()

    private val pausedRef = AtomicBoolean(false)
    private val activeRef = AtomicBoolean(false)

    private val _state = MutableStateFlow<LiveEditState>(LiveEditState.Idle)
    val state: StateFlow<LiveEditState> = _state.asStateFlow()

    private val _lastResult = AtomicReference<LiveEditResult?>(null)
    val lastResult: LiveEditResult? get() = _lastResult.get()

    private var callback: LiveEditCallback? = null

    /**
     * 启动监听并装载 [callback].
     *
     * - [callback] 提供 compile / swap / reRender 三个动作.
     * - [watchFile] 可选; 提供后启动 WatchService, 否则仅接受手动 [notifySourceChanged].
     * - 重复 start 会停止前一个 session.
     */
    fun start(callback: LiveEditCallback, watchFile: File? = null) {
        stop()

        this.callback = callback
        activeRef.set(true)

        if (watchFile != null) {
            val ok = watcher.startWatch(watchFile)
            if (!ok) {
                LOG.warn("WatchService failed to start; only manual reload will work")
            }
        }

        collectJobRef.set(scope.launch {
            watcher.events.collectLatest { event ->
                if (pausedRef.get()) {
                    LOG.debug("Paused, ignoring source change (hash={})", event.sourceHash)
                    return@collectLatest
                }
                handleSourceChange(event)
            }
        })

        LOG.info("LiveEditCoordinator started (debounce={}ms)", debounceMs)
    }

    /**
     * 停止监听并取消所有协程. UI destroy 时调用.
     */
    fun stop() {
        activeRef.set(false)
        watcher.stopWatch()
        collectJobRef.getAndSet(null)?.cancel()
        callback = null
        _state.value = LiveEditState.Idle
        LOG.info("LiveEditCoordinator stopped")
    }

    /**
     * 手动推送一个 source change 事件 (e.g. 编辑器 buffer 改变).
     */
    fun notifySourceChanged(sourceText: String) {
        watcher.notifySourceChanged(sourceText)
    }

    /**
     * 暂停 / 恢复 hot reload. 暂停时忽略 [SourceChangeWatcher] 事件.
     */
    fun setPaused(paused: Boolean) {
        pausedRef.set(paused)
        LiveEditStatsRegistry.setPaused(paused)
        LOG.info("LiveEdit paused={}", paused)
        if (paused) {
            _state.value = LiveEditState.Idle
        }
    }

    val isPaused: Boolean get() = pausedRef.get()

    /**
     * 手动触发一次 reload (即使 paused 也会执行). 用于 DebugDrawer "Force Reload" 按钮.
     */
    suspend fun forceReload(sourceText: String? = null): LiveEditResult {
        val cb = callback ?: return LiveEditResult.Error("Callback not installed")
        val text = sourceText ?: _lastResult.get()?.sourceText
            ?: return LiveEditResult.Error("No source text available; provide it explicitly")
        return reload(cb, text, sourceHash = SourceChangeWatcher.fnv1aHash(text), sourcePath = _lastResult.get()?.sourcePath)
    }

    private suspend fun handleSourceChange(event: SourceChangeEvent) {
        if (!activeRef.get()) return
        val cb = callback ?: return
        reload(cb, event.sourceText, event.sourceHash, event.sourcePath)
    }

    private suspend fun reload(
        cb: LiveEditCallback,
        sourceText: String,
        sourceHash: Int,
        sourcePath: String?,
    ): LiveEditResult = reloadMutex.withLock {
        val startTs = System.currentTimeMillis()
        _state.value = LiveEditState.Debouncing
        delay(debounceMs)
        if (!activeRef.get()) return@withLock LiveEditResult.Cancelled

        val result = try {
            _state.value = LiveEditState.Compiling
            val parsed = cb.parseSource(sourceText)
            if (parsed == null) {
                LiveEditResult.Error("No @Preview function found in source")
            } else {
                _state.value = LiveEditState.Dexing
                val compileResult = cb.recompile(sourceText, parsed)
                if (compileResult == null) {
                    LiveEditResult.Error("Recompile returned null")
                } else {
                    _state.value = LiveEditState.Swapping
                    cb.swapClassLoader(compileResult.dexFile, compileResult.className)
                    _state.value = LiveEditState.Rendering
                    cb.reRender(compileResult)
                    LiveEditResult.Success(compileResult, System.currentTimeMillis() - startTs)
                }
            }
        } catch (e: Throwable) {
            LOG.error("Hot reload failed", e)
            LiveEditResult.Error(e.message ?: e::class.java.simpleName)
        }

        when (result) {
            is LiveEditResult.Success -> {
                _state.value = LiveEditState.Idle
                LiveEditStatsRegistry.recordSuccess(result.elapsedMs, sourceHash)
                _lastResult.set(
                    LiveEditSnapshot(
                        sourceText = sourceText,
                        sourcePath = sourcePath,
                        sourceHash = sourceHash,
                        lastReloadMs = result.elapsedMs,
                        success = true,
                    )
                )
            }
            is LiveEditResult.Error -> {
                _state.value = LiveEditState.Error(result.message)
                LiveEditStatsRegistry.recordError(result.message, sourceHash)
                _lastResult.set(
                    LiveEditSnapshot(
                        sourceText = sourceText,
                        sourcePath = sourcePath,
                        sourceHash = sourceHash,
                        lastReloadMs = System.currentTimeMillis() - startTs,
                        success = false,
                        errorMessage = result.message,
                    )
                )
            }
            LiveEditResult.Cancelled -> {
                _state.value = LiveEditState.Idle
            }
        }

        result
    }

    /**
     * 释放所有资源. 之后 [start] 需要重新调用才能继续工作.
     */
    fun release() {
        stop()
        scope.cancel()
    }

    companion object {
        @Volatile
        private var instance: LiveEditCoordinator? = null

        /**
         * 全局单例. UI 层用这个; 测试可自行 new.
         */
        fun getOrCreate(): LiveEditCoordinator {
            return instance ?: synchronized(this) {
                instance ?: LiveEditCoordinator().also { instance = it }
            }
        }
    }
}

/**
 * 状态机状态. UI 层用 `when` 分发.
 */
sealed class LiveEditState {
    data object Idle : LiveEditState()
    data object Debouncing : LiveEditState()
    data object Compiling : LiveEditState()
    data object Dexing : LiveEditState()
    data object Swapping : LiveEditState()
    data object Rendering : LiveEditState()
    data class Error(val message: String) : LiveEditState()
}

/**
 * 单次 reload 结果.
 */
sealed class LiveEditResult {
    data class Success(
        val compilation: Any, // CompilationResult (避免跨模块类型强依赖, runtime 侧用 Any)
        val elapsedMs: Long,
    ) : LiveEditResult()

    data class Error(val message: String) : LiveEditResult()
    data object Cancelled : LiveEditResult()
}

/**
 * 最近一次 reload 的快照 (用于 "Force Reload" 无新 sourceText 时复用).
 */
data class LiveEditSnapshot(
    val sourceText: String,
    val sourcePath: String?,
    val sourceHash: Int,
    val lastReloadMs: Long,
    val success: Boolean,
    val errorMessage: String? = null,
)

/**
 * 注入的回调: Coordinator 不直接依赖 Repository / Compiler / Renderer,
 * 通过这个接口解耦, 方便测试 + 多场景复用.
 */
interface LiveEditCallback {
    /**
     * 解析 source 找到 @Preview 函数. 返回 null 表示无 preview.
     */
    fun parseSource(sourceText: String): Any? // ParsedPreviewSource

    /**
     * 重新编译 + dex. 返回 Any 是为了避免循环依赖 (runtime 引用 data layer 较重).
     * 实际类型是 CompilationResult (dexFile / className / runtimeDex / projectDexFiles).
     */
    fun recompile(sourceText: String, parsed: Any): Any?

    /**
     * 通知 ClassLoader 切换到新 dex.
     */
    fun swapClassLoader(dexFile: File, className: String)

    /**
     * 触发 ComposableRenderer 重新渲染.
     */
    fun reRender(compilation: Any)
}
