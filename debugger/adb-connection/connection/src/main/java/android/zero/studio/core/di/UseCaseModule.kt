package android.zero.studio.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.zero.studio.core.domain.repository.DownloadRepository
import android.zero.studio.core.domain.usecase.DownloadApkUseCase
import android.zero.studio.settings.domain.usecase.GetAllChangelogsUseCase
import android.zero.studio.shell.common.domain.usecase.ExtractLastCommandOutputUseCase
import android.zero.studio.shell.common.domain.usecase.GetSaveOutputFileNameUseCase
import android.zero.studio.shell.local_adb_shell.data.shell.ShellCommandExecutor
import android.zero.studio.shell.local_adb_shell.data.shizuku.ShizukuPermissionHandler

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideGetChangelogsUseCase(@ApplicationContext context: Context): GetAllChangelogsUseCase =
        GetAllChangelogsUseCase(context)

    @Provides
    fun provideDownloadApkUseCase(repo: DownloadRepository): DownloadApkUseCase =
        DownloadApkUseCase(repo)

    @Provides
    fun provideShellCommandExecutor(): ShellCommandExecutor = ShellCommandExecutor()

    @Provides
    fun provideShizukuPermissionHandler(): ShizukuPermissionHandler = ShizukuPermissionHandler()

    @Provides
    fun provideExtractLastCommandOutputUseCase(): ExtractLastCommandOutputUseCase =
        ExtractLastCommandOutputUseCase()

    @Provides
    fun provideGetSaveOutputFileNameUseCase(): GetSaveOutputFileNameUseCase =
        GetSaveOutputFileNameUseCase()
}