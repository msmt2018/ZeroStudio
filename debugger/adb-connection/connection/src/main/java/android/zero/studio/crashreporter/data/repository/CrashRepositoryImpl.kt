package android.zero.studio.crashreporter.data.repository

import android.zero.studio.crashreporter.data.database.CrashLogDao
import android.zero.studio.crashreporter.data.model.CrashLogEntity
import android.zero.studio.crashreporter.domain.model.CrashReport
import android.zero.studio.crashreporter.domain.repository.CrashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CrashRepositoryImpl @Inject constructor(private val dao: CrashLogDao) : CrashRepository {
    override suspend fun addCrash(crash: CrashReport) {
        dao.insertCrash(
            CrashLogEntity(
                timestamp = crash.timestamp,
                deviceBrand = crash.deviceBrand,
                deviceModel = crash.deviceModel,
                manufacturer = crash.manufacturer,
                osVersion = crash.osVersion,
                socManufacturer = crash.socManufacturer,
                cpuAbi = crash.cpuAbi,
                appPackageName = crash.appPackageName,
                appVersionName = crash.appVersionName,
                appVersionCode = crash.appVersionCode,
                stackTrace = crash.stackTrace
            )
        )
    }

    override suspend fun getLatestCrash(): CrashReport? {
        return dao.getLatestCrash()
    }

    override fun getAllCrashes(): Flow<List<CrashReport>> {
        return dao.getAllCrashes().map { list ->
            list.map { crash ->
                CrashReport(
                    timestamp = crash.timestamp,
                    deviceBrand = crash.deviceBrand,
                    deviceModel = crash.deviceModel,
                    manufacturer = crash.manufacturer,
                    osVersion = crash.osVersion,
                    socManufacturer = crash.socManufacturer,
                    cpuAbi = crash.cpuAbi,
                    appPackageName = crash.appPackageName,
                    appVersionName = crash.appVersionName,
                    appVersionCode = crash.appVersionCode,
                    stackTrace = crash.stackTrace
                )
            }
        }
    }

    override suspend fun clearAllCrashes() {
        dao.clearAllCrashes()
    }
}
