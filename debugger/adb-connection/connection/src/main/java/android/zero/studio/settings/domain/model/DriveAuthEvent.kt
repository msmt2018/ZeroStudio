package android.zero.studio.settings.domain.model

import android.content.IntentSender

sealed class DriveAuthEvent {
    data class ConsentRequired(val intentSender: IntentSender) : DriveAuthEvent()
}