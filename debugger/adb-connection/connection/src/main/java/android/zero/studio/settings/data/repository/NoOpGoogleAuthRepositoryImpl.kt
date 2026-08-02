package android.zero.studio.settings.data.repository

import android.content.Context
import android.zero.studio.settings.domain.model.GoogleUserState
import android.zero.studio.settings.domain.repository.GoogleAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** No-op implementation used when Google Auth is not available in this module. */
class NoOpGoogleAuthRepositoryImpl @Inject constructor() : GoogleAuthRepository {

    override val isAvailable: Boolean = false

    private val _googleUserState = MutableStateFlow(GoogleUserState())
    override val googleUserState: StateFlow<GoogleUserState> = _googleUserState.asStateFlow()

    override suspend fun signIn(context: Context): Result<String> =
        Result.failure(UnsupportedOperationException("Google Sign-In is not available in this build."))

    override suspend fun signOut() {
        // no-op
    }

    override fun getAccountEmail(): String? = null
}
