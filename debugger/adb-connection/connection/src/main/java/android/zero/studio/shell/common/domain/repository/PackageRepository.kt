package android.zero.studio.shell.common.domain.repository

import android.zero.studio.shell.common.domain.model.PackageInfo

interface PackageRepository {
    suspend fun getInstalledPackages(): List<PackageInfo>
    suspend fun refreshPackages()
}
