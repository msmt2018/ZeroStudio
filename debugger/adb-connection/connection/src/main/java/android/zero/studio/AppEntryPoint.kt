package android.zero.studio

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import android.zero.studio.crashreporter.domain.repository.CrashRepository
import android.zero.studio.qstiles.data.provider.TileComponentManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun crashRepository(): CrashRepository
    fun tileComponentManager(): TileComponentManager
}
