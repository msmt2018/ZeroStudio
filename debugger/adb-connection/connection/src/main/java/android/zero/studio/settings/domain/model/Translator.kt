package android.zero.studio.settings.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Translator(
    val name: String,
    val languages: List<String>,
    val avatarAssetPath: String
)