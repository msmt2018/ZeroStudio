package android.zero.studio.settings.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LanguageDto(
    val id: String,
    val name: String
)