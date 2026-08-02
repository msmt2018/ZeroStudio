package android.zero.studio.settings.data.repository

import android.zero.studio.settings.domain.model.DriveAuthEvent
import android.zero.studio.settings.domain.repository.GoogleDriveRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/** No-op implementation used when Google Drive backup is not available in this module. */
class NoOpGoogleDriveRepositoryImpl @Inject constructor() : GoogleDriveRepository {

    override val isAvailable: Boolean = false

    private val _authEvents = MutableSharedFlow<DriveAuthEvent>()
    override val authEvents: SharedFlow<DriveAuthEvent> = _authEvents.asSharedFlow()

    override val isConsentPending: Boolean = false

    override suspend fun uploadBackup(data: ByteArray, fileName: String): Boolean = false

    override suspend fun downloadBackup(): Pair<ByteArray, String>? = null

    override suspend fun deleteAllBackups(): Boolean = false

    override suspend fun ensureAuthorized(): Boolean = false

    override fun onConsentGranted() {
        // no-op
    }

    override suspend fun getHeadlessDriveService(): Any? = null
}
