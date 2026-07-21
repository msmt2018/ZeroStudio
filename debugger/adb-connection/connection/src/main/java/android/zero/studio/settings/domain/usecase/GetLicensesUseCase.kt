package android.zero.studio.settings.domain.usecase

import android.zero.studio.settings.domain.model.LibraryItem
import android.zero.studio.settings.domain.repository.LicensesRepository
import javax.inject.Inject

/**
 * Use case: fetch all open-source libraries from the repository.
 *
 * Keeping this as a separate use case follows Clean Architecture
 * and makes the logic independently testable.
 */
class GetLicensesUseCase @Inject constructor(
    private val repository: LicensesRepository,
) {
    suspend operator fun invoke(): List<LibraryItem> = repository.getLibraries()
}
