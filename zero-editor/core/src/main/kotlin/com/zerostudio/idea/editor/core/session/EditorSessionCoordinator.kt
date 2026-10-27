package com.zerostudio.idea.editor.core.session

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 编辑器单个标签页的持久化物理状态
 */
data class DocumentSessionSnapshot(
    val filePath: String,
    val cursorLine: Int,
    val cursorColumn: Int,
    val scrollX: Int,
    val scrollY: Int,
    val activePageIndex: Int,
    val isDirty: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 会话恢复与序列化控制器
 */
object EditorSessionCoordinator {

    private const val SESSION_FILE = "editor_session_cache.json"

    /**
     * 保存所有打开文档的光标与物理位移快照
     */
    suspend fun persistSessions(context: Context, sessions: List<DocumentSessionSnapshot>) = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, SESSION_FILE)
        val jsonRoot = JSONObject()
        val array = org.json.JSONArray()

        for (snapshot in sessions) {
            val item = JSONObject().apply {
                put("filePath", snapshot.filePath)
                put("cursorLine", snapshot.cursorLine)
                put("cursorColumn", snapshot.cursorColumn)
                put("scrollX", snapshot.scrollX)
                put("scrollY", snapshot.scrollY)
                put("activePageIndex", snapshot.activePageIndex)
                put("isDirty", snapshot.isDirty)
                put("timestamp", snapshot.timestamp)
            }
            array.put(item)
        }
        jsonRoot.put("sessions", array)

        cacheFile.writeText(jsonRoot.toString())
    }

    /**
     * 恢复会话列表
     */
    suspend fun restoreSessions(context: Context): List<DocumentSessionSnapshot> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, SESSION_FILE)
        if (!cacheFile.exists()) return@withContext emptyList()

        return@withContext runCatching {
            val content = cacheFile.readText()
            val jsonRoot = JSONObject(content)
            val array = jsonRoot.getJSONArray("sessions")
            val list = mutableListOf<DocumentSessionSnapshot>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DocumentSessionSnapshot(
                        filePath = obj.getString("filePath"),
                        cursorLine = obj.getInt("cursorLine"),
                        cursorColumn = obj.getInt("cursorColumn"),
                        scrollX = obj.getInt("scrollX"),
                        scrollY = obj.getInt("scrollY"),
                        activePageIndex = obj.getInt("activePageIndex"),
                        isDirty = obj.getBoolean("isDirty"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }
}