package android.zero.studio.settings.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.zero.studio.settings.data.repository.ContributorsRepositoryImpl
import android.zero.studio.settings.domain.repository.ContributorsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ContributorsModule {

    @Provides
    @Singleton
    fun provideContributorsRepository(
        @ApplicationContext context: Context
    ): ContributorsRepository {
        return ContributorsRepositoryImpl(context)
    }
}