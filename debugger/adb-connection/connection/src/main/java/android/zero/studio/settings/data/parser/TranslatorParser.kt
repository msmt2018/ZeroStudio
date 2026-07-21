package android.zero.studio.settings.data.parser

import android.content.Context
import android.zero.studio.settings.data.dto.TranslatorDto
import kotlinx.serialization.json.Json

object TranslatorParser {

    fun loadJson(context: Context): List<TranslatorDto> {

        val json = context.assets
            .open("crowdin/translators.json")
            .bufferedReader()
            .use { it.readText() }

        return Json.decodeFromString(json)
    }
}