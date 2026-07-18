package android.zero.studio.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import android.zero.studio.commandexamples.data.local.repository.CommandRepositoryImpl
import android.zero.studio.commandexamples.domain.repository.CommandRepository
import android.zero.studio.crashreporter.data.repository.CrashRepositoryImpl
import android.zero.studio.crashreporter.domain.repository.CrashRepository
import android.zero.studio.settings.data.repository.BackupAndRestoreRepositoryImpl
import android.zero.studio.settings.domain.repository.BackupAndRestoreRepository
import android.zero.studio.shell.common.data.repository.BookmarkRepositoryImpl
import android.zero.studio.shell.common.data.repository.PackageRepositoryImpl
import android.zero.studio.shell.common.domain.repository.BookmarkRepository
import android.zero.studio.shell.common.domain.repository.PackageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCommandRepository(
        commandRepositoryImpl: CommandRepositoryImpl
    ): CommandRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(
        bookmarkRepositoryImpl: BookmarkRepositoryImpl
    ): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindBackupAndRestoreRepository(
        backupAndRestoreRepositoryImpl: BackupAndRestoreRepositoryImpl
    ): BackupAndRestoreRepository

    @Binds
    @Singleton
    abstract fun bindCrashRepository(
        crashRepositoryImpl: CrashRepositoryImpl
    ): CrashRepository

    @Binds
    @Singleton
    abstract fun bindPackageRepository(
        packageRepositoryImpl: PackageRepositoryImpl
    ): PackageRepository
}
