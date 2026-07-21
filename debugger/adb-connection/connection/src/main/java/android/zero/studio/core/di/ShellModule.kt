package android.zero.studio.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.zero.studio.shell.common.data.repository.ShellRepositoryImpl
import android.zero.studio.shell.common.domain.repository.ShellRepository
import android.zero.studio.shell.local_adb_shell.data.shell.ShellCommandExecutor
import android.zero.studio.shell.local_adb_shell.data.shizuku.ShizukuPermissionHandler
import android.zero.studio.shell.otg_adb_shell.data.repository.OtgRepositoryImpl
import android.zero.studio.shell.otg_adb_shell.domain.repository.OtgRepository
import android.zero.studio.shell.fastboot.data.repository.FastbootRepositoryImpl
import android.zero.studio.shell.fastboot.domain.repository.FastbootRepository
import android.zero.studio.shell.wifi_adb_shell.data.local.database.WifiAdbDeviceDao
import android.zero.studio.shell.wifi_adb_shell.data.repository.WifiAdbRepositoryImpl
import android.zero.studio.shell.wifi_adb_shell.domain.repository.WifiAdbRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ShellModule {
    @Provides
    fun provideShellRepository(
        shellCommandExecutor: ShellCommandExecutor,
        shizukuPermissionHandler: ShizukuPermissionHandler,
        @ApplicationContext context: Context
    ): ShellRepository =
        ShellRepositoryImpl(shellCommandExecutor, shizukuPermissionHandler, context)

    @Provides
    @Singleton
    fun provideOtgRepository(@ApplicationContext context: Context): OtgRepository {
        return OtgRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideWifiAdbRepository(
        @ApplicationContext context: Context,
        deviceDao: WifiAdbDeviceDao
    ): WifiAdbRepository {
        return WifiAdbRepositoryImpl(context, deviceDao)
    }

    @Provides
    @Singleton
    fun provideFastbootRepository(@ApplicationContext context: Context): FastbootRepository {
        return FastbootRepositoryImpl(context)
    }
}
