package android.zero.studio.shell.fastboot.domain.model

import android.zero.studio.fastboot.ResponseStatus

data class FastbootCommandResult(
    val command: String,
    val status: ResponseStatus,
    val data: String,
    val timestamp: Long = System.currentTimeMillis()
)
