package android.zero.studio.settings.data.mapper

import android.zero.studio.settings.data.dto.TranslatorDto
import android.zero.studio.settings.domain.model.Translator

fun TranslatorDto.toTranslator(): Translator {
    return Translator(
        name = name.replace(Regex("""\s*\(.*?\)"""), ""),
        languages = languages.map { it.name },
        avatarAssetPath = avatar
    )
}