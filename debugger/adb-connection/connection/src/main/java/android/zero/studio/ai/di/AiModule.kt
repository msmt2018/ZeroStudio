package android.zero.studio.ai.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.zero.studio.ai.data.local.database.AiCacheDao
import android.zero.studio.ai.data.local.database.AiCacheDatabase
import android.zero.studio.ai.data.repository.AiAnalysisRepositoryImpl
import android.zero.studio.ai.data.repository.AiModelRepositoryImpl
import android.zero.studio.ai.data.repository.LlamaInferenceEngine
import android.zero.studio.ai.domain.repository.AiAnalysisRepository
import android.zero.studio.ai.domain.repository.AiModelRepository
import android.zero.studio.ai.domain.usecase.AnalyzeCommandUseCase
import android.zero.studio.ai.domain.usecase.DetectDangerLevelUseCase
import android.zero.studio.ai.domain.usecase.GenerateCorrectionsUseCase
import android.zero.studio.ai.domain.usecase.GetCachedAnalysisUseCase
import android.zero.studio.commandexamples.domain.repository.CommandRepository
import android.zero.studio.settings.domain.repository.SettingsRepository
import javax.inject.Singleton

/**
 * Hilt module providing all AI feature dependencies.
 * Self-contained — does not modify existing DI modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiCacheDatabase(@ApplicationContext context: Context): AiCacheDatabase {
        return Room.databaseBuilder(
            context,
            AiCacheDatabase::class.java,
            "ai_cache_database"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideAiCacheDao(database: AiCacheDatabase): AiCacheDao {
        return database.aiCacheDao()
    }

    @Provides
    @Singleton
    fun provideAiAnalysisRepository(
        cacheDao: AiCacheDao,
        inferenceEngine: LlamaInferenceEngine,
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context
    ): AiAnalysisRepository {
        return AiAnalysisRepositoryImpl(cacheDao, inferenceEngine, settingsRepository, context)
    }

    @Provides
    @Singleton
    fun provideAiModelRepository(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): AiModelRepository {
        return AiModelRepositoryImpl(context, settingsRepository)
    }

    @Provides
    fun provideDetectDangerLevelUseCase(): DetectDangerLevelUseCase {
        return DetectDangerLevelUseCase()
    }

    @Provides
    fun provideAnalyzeCommandUseCase(
        analysisRepository: AiAnalysisRepository,
        modelRepository: AiModelRepository
    ): AnalyzeCommandUseCase {
        return AnalyzeCommandUseCase(
            analysisRepository, modelRepository
        )
    }

    @Provides
    fun provideGenerateCorrectionsUseCase(
        commandRepository: CommandRepository
    ): GenerateCorrectionsUseCase {
        return GenerateCorrectionsUseCase(commandRepository)
    }

    @Provides
    fun provideGetCachedAnalysisUseCase(
        analysisRepository: AiAnalysisRepository
    ): GetCachedAnalysisUseCase {
        return GetCachedAnalysisUseCase(analysisRepository)
    }
}
