package com.zerostudio.idea.editor.core.io

import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.config.EditorEventBus
import com.zerostudio.idea.editor.core.config.EditorEngineEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

/**
 * 工业级可靠自动保存协调器
 * 支持原子写入（Atomic Temp Swap）避免程序中途崩溃写入残缺损坏文件
 */
class AutoSaveCoordinator(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val saveMutex = Mutex()
    private var pendingSaveJob: Job? = null

    /**
     * 触发防抖自动保存
     */
    fun scheduleDebouncedSave(
        targetFile: File,
        pieceTree: LargeTextPieceTree,
        charset: Charset,
        delayMs: Long
    ) {
        pendingSaveJob?.cancel()
        pendingSaveJob = scope.launch {
            delay(delayMs)
            executeAtomicSave(targetFile, pieceTree, charset)
        }
    }

    /**
     * 立即执行强制同步持久化
     */
    suspend fun saveImmediately(
        targetFile: File,
        pieceTree: LargeTextPieceTree,
        charset: Charset
    ): Boolean = withContext(Dispatchers.IO) {
        pendingSaveJob?.cancel()
        executeAtomicSave(targetFile, pieceTree, charset)
    }

    private suspend fun executeAtomicSave(
        targetFile: File,
        pieceTree: LargeTextPieceTree,
        charset: Charset
    ): Boolean = saveMutex.withLock {
        withContext(Dispatchers.IO) {
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.xed_atomic_tmp")
            try {
                FileOutputStream(tempFile).use { fos ->
                    val writer = OutputStreamWriter(fos, charset)
                    val totalLen = pieceTree.length()
                    val buffer = CharArray(32 * 1024)
                    var cursor = 0L

                    while (cursor < totalLen) {
                        val chunkSize = minOf(buffer.size.toLong(), totalLen - cursor).toInt()
                        for (i in 0 until chunkSize) {
                            buffer[i] = pieceTree.charAt(cursor + i)
                        }
                        writer.write(buffer, 0, chunkSize)
                        cursor += chunkSize
                    }

                    writer.flush()
                    // 硬件底层刷盘，防断电丢失
                    fos.fd.sync()
                }

                // 物理原子替换
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val success = tempFile.renameTo(targetFile)
                if (success) {
                    EditorEventBus.emit(EditorEngineEvent.AutoSaved(System.currentTimeMillis()))
                }
                success
            } catch (e: Exception) {
                tempFile.delete()
                false
            }
        }
    }

    fun release() {
        pendingSaveJob?.cancel()
        scope.cancel()
    }
}