package com.zerostudio.idea.editor.core.watcher

import android.os.Build
import android.os.FileObserver
import com.zerostudio.idea.editor.core.config.EditorEventBus
import com.zerostudio.idea.editor.core.config.EditorEngineEvent
import kotlinx.coroutines.*
import java.io.File

/**
 * 双重文件监听机制（Linux Inotify + 轮询兜底）
 */
class ExternalFileWatcher(
    val targetFile: File,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var observer: FileObserver? = null
    private var lastKnownModifiedTime: Long = targetFile.lastModified()
    private var isWatching = false

    fun startWatching() {
        if (isWatching) return
        isWatching = true

        // Linux Inotify 事件驱动
        val mask = FileObserver.MODIFY or FileObserver.DELETE_SELF or FileObserver.ATTRIB
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            observer = object : FileObserver(targetFile, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleKernelEvent(event)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            observer = object : FileObserver(targetFile.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleKernelEvent(event)
                }
            }
        }
        observer?.startWatching()

        // 协程心跳轮询（针对沙盒外部存储 SD 卡的 Inotify 静默失效兜底）
        scope.launch {
            while (isActive && isWatching) {
                delay(2000)
                checkFileHeartbeat()
            }
        }
    }

    private fun handleKernelEvent(event: Int) {
        if ((event and FileObserver.DELETE_SELF) != 0) {
            EditorEventBus.emit(EditorEngineEvent.FileExternallyDeleted(targetFile.absolutePath))
        } else if ((event and FileObserver.MODIFY) != 0) {
            val currentMod = targetFile.lastModified()
            if (currentMod != lastKnownModifiedTime) {
                lastKnownModifiedTime = currentMod
                EditorEventBus.emit(EditorEngineEvent.FileExternallyModified(targetFile.absolutePath))
            }
        }
    }

    private fun checkFileHeartbeat() {
        if (!targetFile.exists()) {
            isWatching = false
            EditorEventBus.emit(EditorEngineEvent.FileExternallyDeleted(targetFile.absolutePath))
            return
        }
        val currentMod = targetFile.lastModified()
        if (currentMod > lastKnownModifiedTime) {
            lastKnownModifiedTime = currentMod
            EditorEventBus.emit(EditorEngineEvent.FileExternallyModified(targetFile.absolutePath))
        }
    }

    fun stopWatching() {
        isWatching = false
        observer?.stopWatching()
        observer = null
        scope.cancel()
    }
}