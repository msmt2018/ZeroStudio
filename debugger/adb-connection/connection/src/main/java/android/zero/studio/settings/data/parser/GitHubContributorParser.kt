package android.zero.studio.settings.data.parser

import android.content.Context
import android.zero.studio.settings.data.dto.GitHubContributorDto
import kotlinx.serialization.json.Json

object GitHubContributorParser {

    fun loadJson(context: Context): List<GitHubContributorDto> {

        val json = context.assets
            .open("github/contributors.json")
            .bufferedReader()
            .use { it.readText() }

        return Json.decodeFromString(json)
    }
}
