package android.zero.studio.qstiles.domain.processor

import android.zero.studio.qstiles.data.provider.TileIconProvider

class TileIconMatcher(
    private val keywordProcessor: TileCommandKeywordProcessor
) {
    fun suggestIcons(command: String): List<String> {
        val keywords = keywordProcessor.extractKeywords(command)

        val scores = mutableMapOf<String, Int>()

        keywords.forEach { keyword ->
            val icons = TileIconProvider.iconsByKeyword[keyword] ?: return@forEach
            icons.forEach { icon ->
                scores[icon.id] = (scores[icon.id] ?: 0) + 1
            }
        }

        return scores
            .toList()
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }
}