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
 *
 * @author android_zero
 */
package com.itsaky.androidide.utils

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

data class ProjectHistory(
    val name: String,
    val path: String,
    val timestamp: Long = System.currentTimeMillis(),
    val openCount: Int = 1, // 打开次数记录
    val isPinned: Boolean = false, // 是否置顶
) {
  val letter: String
    get() = name.take(1).uppercase()

  val color: Color
    get() {
      val hash = path.hashCode()
      return Color(
          red = (hash and 0xFF0000 shr 16) / 255f * 0.5f + 0.3f,
          green = (hash and 0x00FF00 shr 8) / 255f * 0.5f + 0.3f,
          blue = (hash and 0x0000FF) / 255f * 0.5f + 0.3f,
          alpha = 1f,
      )
    }
}

/** 极致优化的项目历史记录单例。 */
object RecentProjectsManager {
  private const val PREF_NAME = "recent_projects_prefs_v3"
  private const val KEY_HISTORY = "project_history_json"
  private val gson = Gson()

  private var cachedHistory: MutableList<ProjectHistory>? = null

  @OptIn(DelicateCoroutinesApi::class)
  private val writeDispatcher = newSingleThreadContext("RecentProjectsWriter")

  suspend fun getHistoryAsync(context: Context): List<ProjectHistory> =
      withContext(Dispatchers.IO) {
        cachedHistory?.let {
          return@withContext it.toList()
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null)

        val list =
            if (json.isNullOrEmpty()) {
              mutableListOf()
            } else {
              try {
                val type = object : TypeToken<List<ProjectHistory>>() {}.type
                val parsed: List<ProjectHistory> = gson.fromJson(json, type)
                parsed.toMutableList()
              } catch (e: Exception) {
                mutableListOf()
              }
            }
        cachedHistory = list
        return@withContext list.toList()
      }

  suspend fun addProjectAsync(context: Context, file: File) =
      withContext(Dispatchers.Default) {
        if (!file.exists()) return@withContext
        val path = file.absolutePath
        val history = cachedHistory ?: getHistoryAsync(context).toMutableList()

        val existingIndex = history.indexOfFirst { it.path == path }
        val newEntry =
            if (existingIndex != -1) {
              val old = history.removeAt(existingIndex)
              // Preserve the `isPinned` flag of an existing entry. The new
              // timestamp / openCount reflect the latest open, but the user
              // should not have to re-pin the project after every open.
              old.copy(timestamp = System.currentTimeMillis(), openCount = old.openCount + 1)
            } else {
              ProjectHistory(name = file.name, path = path)
            }

        history.add(0, newEntry)
        if (history.size > 20) history.removeAt(history.lastIndex)
        cachedHistory = history
        persistAsync(context, history)
      }

  /**
   * Permanently remove the project at [path] from the history and from the
   * shared-preferences backing file. Returns the file the project pointed at,
   * so the caller can also delete the project directory from disk if the
   * user requested a full cleanup.
   */
  suspend fun removeProjectAsync(context: Context, path: String): File? =
      withContext(Dispatchers.Default) {
        val history = cachedHistory ?: getHistoryAsync(context).toMutableList()
        val idx = history.indexOfFirst { it.path == path }
        if (idx == -1) {
          return@withContext null
        }
        val removed = history.removeAt(idx)
        cachedHistory = history
        persistAsync(context, history)
        return@withContext File(removed.path)
      }

  /**
   * Toggle the pinned state of the project at [path]. Pinned projects are
   * always shown at the top of any sorted list and survive a reopen.
   */
  suspend fun togglePinAsync(context: Context, path: String): Boolean? =
      withContext(Dispatchers.Default) {
        val history = cachedHistory ?: getHistoryAsync(context).toMutableList()
        val idx = history.indexOfFirst { it.path == path }
        if (idx == -1) {
          return@withContext null
        }
        val old = history[idx]
        val newEntry = old.copy(isPinned = !old.isPinned, timestamp = System.currentTimeMillis())
        history[idx] = newEntry
        cachedHistory = history
        persistAsync(context, history)
        return@withContext newEntry.isPinned
      }

  /**
   * Persist the supplied [history] to the shared-preferences file. All public
   * mutators should funnel through this so the on-disk JSON stays in sync
   * with the in-memory cache.
   */
  private suspend fun persistAsync(context: Context, history: List<ProjectHistory>) {
    withContext(writeDispatcher) {
      val json = gson.toJson(history)
      context
          .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
          .edit()
          .putString(KEY_HISTORY, json)
          .commit()
    }
  }
}
