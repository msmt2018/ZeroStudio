package android.zero.studio.settings.presentation.event

import android.content.Intent
import android.zero.studio.core.presentation.components.dialog.DialogKey
import android.zero.studio.settings.domain.model.BackupType

sealed class SettingsUiEvent {
    data class ShowToast(val message: String) : SettingsUiEvent()
    data class Navigate(val route: Any) : SettingsUiEvent()
    data class ShowDialog(val key : DialogKey) : SettingsUiEvent()
    data class OpenUrl(val url:String) : SettingsUiEvent()
    data class LaunchIntent(val intent: Intent) : SettingsUiEvent()
    data class RequestPermission(val permission: String) : SettingsUiEvent()

    data class RequestDocumentUriForBackup(val backupType: BackupType) : SettingsUiEvent()
    object RequestDocumentUriForRestore : SettingsUiEvent()

    // Google Drive events
    object RequestGoogleSignIn : SettingsUiEvent()
    data class RequestGoogleDriveBackup(val backupType: BackupType) : SettingsUiEvent()
    object RequestGoogleDriveRestore : SettingsUiEvent()
    object ShowFontStylesBottomSheet : SettingsUiEvent()
    object RequestAutoBackupFolderPicker : SettingsUiEvent()
}
