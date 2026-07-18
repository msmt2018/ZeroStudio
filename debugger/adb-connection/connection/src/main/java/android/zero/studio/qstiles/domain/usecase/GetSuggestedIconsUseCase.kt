package android.zero.studio.qstiles.domain.usecase

import android.zero.studio.qstiles.data.provider.TileIconProvider
import android.zero.studio.qstiles.domain.processor.TileCommandKeywordProcessor
import android.zero.studio.qstiles.domain.scorer.TileIconScorer
import javax.inject.Inject

class GetSuggestedIconsUseCase @Inject constructor(
    private val processor: TileCommandKeywordProcessor,
    private val scorer: TileIconScorer
) {

    operator fun invoke(command: String): List<String> {
        val keywords = processor.extractKeywords(command)

        return scorer
            .scoreIcons(keywords, TileIconProvider.icons)
            .map { it.id }
    }
}