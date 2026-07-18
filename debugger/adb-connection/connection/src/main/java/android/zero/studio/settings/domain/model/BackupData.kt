package android.zero.studio.settings.domain.model

import android.zero.studio.commandexamples.data.local.model.CommandEntity
import android.zero.studio.qstiles.data.model.TileLogEntity
import android.zero.studio.qstiles.domain.model.TileConfig
import android.zero.studio.shell.common.data.model.BookmarkEntity
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val settings: Map<String, String?>? = null,
    val commands: List<CommandEntity>? = null,
    val bookmarks: List<BookmarkEntity>? = null,
    val tiles: List<TileConfig>? = null,
    val tileLogs: List<TileLogEntity>? = null,
    val backupTime: String,
    val backupType: String,
    val backupMode: String
)
