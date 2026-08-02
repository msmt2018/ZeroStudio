package android.zero.studio.shell.common.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import android.zero.studio.shell.common.data.model.BookmarkEntity

@Database(entities = [BookmarkEntity::class], version = 1, exportSchema = false)
abstract class BookmarkDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
}