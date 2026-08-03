package android.zero.studio.crashreporter.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import android.zero.studio.crashreporter.data.model.CrashLogEntity

@Database(entities = [CrashLogEntity::class], version = 1, exportSchema = false)
abstract class CrashDatabase : RoomDatabase() {
    abstract fun crashLogDao(): CrashLogDao
}