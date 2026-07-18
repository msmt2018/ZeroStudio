package android.zero.studio.qstiles.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import android.zero.studio.qstiles.data.dao.TileLogDao
import android.zero.studio.qstiles.data.model.TileLogEntity

@Database(
    entities = [TileLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TileLogDatabase : RoomDatabase() {
    abstract fun dao(): TileLogDao
}