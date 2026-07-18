package android.zero.studio.qstiles.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.zero.studio.qstiles.data.executor.RootExecutor
import android.zero.studio.qstiles.data.executor.ShizukuExecutor
import android.zero.studio.qstiles.data.provider.TileNotificationHelper
import android.zero.studio.qstiles.data.repository.TileLogRepositoryImpl
import android.zero.studio.qstiles.data.repository.TileRepositoryImpl
import android.zero.studio.qstiles.domain.executor.CommandExecutor
import android.zero.studio.qstiles.domain.executor.TileExecutionManager
import android.zero.studio.qstiles.domain.processor.TileCommandKeywordProcessor
import android.zero.studio.qstiles.domain.processor.TileIconMatcher
import android.zero.studio.qstiles.domain.repository.TileLogRepository
import android.zero.studio.qstiles.domain.repository.TileRepository
import android.zero.studio.qstiles.domain.usecase.CreateTileUseCase
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TileModule {

    @Provides
    @Singleton
    fun provideTileRepository(impl: TileRepositoryImpl): TileRepository = impl

    @Provides
    @Singleton
    fun provideTileLogRepository(impl: TileLogRepositoryImpl): TileLogRepository = impl

    @Provides
    fun provideIconMatcher(): TileIconMatcher {
        return TileIconMatcher(TileCommandKeywordProcessor())
    }

    @Provides
    fun provideCreateTileUseCase(
        repo: TileRepository,
        matcher: TileIconMatcher
    ) = CreateTileUseCase(repo, matcher)

    @Provides
    @Singleton
    @Named("shizuku")
    fun provideShizukuExecutor(impl: ShizukuExecutor): CommandExecutor = impl

    @Provides
    @Singleton
    @Named("root")
    fun provideRootExecutor(impl: RootExecutor): CommandExecutor = impl

    @Provides
    @Singleton
    fun provideTileNotificationHelper(
        @ApplicationContext context: Context
    ): TileNotificationHelper = TileNotificationHelper(context)

    @Provides
    @Singleton
    fun provideTileExecutionManager(
        @Named("shizuku") shizukuExecutor: CommandExecutor,
        @Named("root") rootExecutor: CommandExecutor,
        logRepository: TileLogRepository,
        notificationHelper: TileNotificationHelper
    ): TileExecutionManager = TileExecutionManager(
        shizukuExecutor = shizukuExecutor,
        rootExecutor = rootExecutor,
        logRepository = logRepository,
        notificationHelper = notificationHelper
    )
}