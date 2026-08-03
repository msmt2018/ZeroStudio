package android.zero.studio.commandexamples.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.zero.studio.commandexamples.data.local.model.CommandEntity
import android.zero.studio.core.common.converters.StringListConverter

@Database(entities = [CommandEntity::class], version = 2, exportSchema = false)
@TypeConverters(StringListConverter::class)
abstract class CommandDatabase : RoomDatabase() {
    abstract fun commandDao(): CommandDao
}