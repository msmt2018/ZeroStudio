package android.zero.studio.settings.domain.usecase

import android.zero.studio.settings.data.SettingsKeys
import android.zero.studio.settings.domain.repository.SettingsRepository

class ToggleSettingUseCase(private val repo: SettingsRepository) {
    suspend operator fun invoke(key: SettingsKeys<Boolean>) = repo.toggleSetting(key)
}