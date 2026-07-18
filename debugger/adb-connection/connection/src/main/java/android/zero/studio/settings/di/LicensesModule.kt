package android.zero.studio.settings.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.zero.studio.settings.data.repository.LicensesRepositoryImpl
import android.zero.studio.settings.domain.repository.LicensesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LicensesModule {

    @Provides
    @Singleton
    fun provideLicensesRepository(
        @ApplicationContext context: Context,
    ): LicensesRepository = LicensesRepositoryImpl(context)
}
