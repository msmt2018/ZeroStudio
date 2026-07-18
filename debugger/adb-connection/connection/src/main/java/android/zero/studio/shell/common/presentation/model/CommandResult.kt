package android.zero.studio.shell.common.presentation.model

import android.zero.studio.shell.common.domain.model.OutputLine
import kotlinx.coroutines.flow.MutableStateFlow

data class CommandResult(
    val command: String,
    val outputFlow: MutableStateFlow<List<OutputLine>>
)