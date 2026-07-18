package android.zero.studio.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.zero.studio.commandexamples.data.local.database.CommandDao
import android.zero.studio.commandexamples.data.local.database.CommandDatabase
import android.zero.studio.commandexamples.data.local.source.preloadedCommands
import android.zero.studio.core.common.converters.StringListConverter
import android.zero.studio.core.data.local.database.GithubRepoStatsDao
import android.zero.studio.core.data.local.database.GithubRepoStatsDatabase
import android.zero.studio.crashreporter.data.database.CrashDatabase
import android.zero.studio.crashreporter.data.database.CrashLogDao
import android.zero.studio.shell.common.data.database.BookmarkDao
import android.zero.studio.shell.common.data.database.BookmarkDatabase
import android.zero.studio.shell.wifi_adb_shell.data.local.database.WifiAdbDeviceDao
import android.zero.studio.shell.wifi_adb_shell.data.local.database.WifiAdbDeviceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CommandDatabase {
        lateinit var database: CommandDatabase
        
        database = Room.databaseBuilder(
            context,
            CommandDatabase::class.java,
            "command_database"
        )
            .addTypeConverter(StringListConverter())
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        database.commandDao().insertAllCommands(preloadedCommands)
                    }
                }
            })
            .fallbackToDestructiveMigration(false)
            .build()
        
        return database
    }


    @Provides
    fun provideCommandDao(database: CommandDatabase): CommandDao {
        return database.commandDao()
    }

    @Provides
    @Singleton
    fun provideBookmarkDatabase(@ApplicationContext context: Context): BookmarkDatabase {
        return Room.databaseBuilder(
            context,
            BookmarkDatabase::class.java,
            "bookmark_database"
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideBookmarkDao(database: BookmarkDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    @Singleton
    fun provideCrashDatabase(
        @ApplicationContext context: Context
    ): CrashDatabase {
        return Room.databaseBuilder(
            context,
            CrashDatabase::class.java,
            "crash_database"
        ).fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }

    @Provides
    fun provideCrashLogDao(db: CrashDatabase): CrashLogDao {
        return db.crashLogDao()
    }

    @Provides
    @Singleton
    fun provideGithubRepoStatsDatabase(@ApplicationContext context: Context): GithubRepoStatsDatabase {
        return Room.databaseBuilder(
            context,
            GithubRepoStatsDatabase::class.java,
            "github_repo_stats_database"
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideGithubRepoStatsDao(database: GithubRepoStatsDatabase): GithubRepoStatsDao {
        return database.githubRepoStatsDao()
    }

    @Provides
    @Singleton
    fun provideWifiAdbDeviceDatabase(@ApplicationContext context: Context): WifiAdbDeviceDatabase {
        return Room.databaseBuilder(
            context,
            WifiAdbDeviceDatabase::class.java,
            "wifi_adb_device_database"
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideWifiAdbDeviceDao(database: WifiAdbDeviceDatabase): WifiAdbDeviceDao {
        return database.wifiAdbDeviceDao()
    }
}