package android.zero.studio.settings.domain.usecase

import android.content.Context
import android.zero.studio.R
import android.zero.studio.settings.data.source.VersionToChangelogs
import android.zero.studio.settings.domain.model.ChangelogItem

class GetAllChangelogsUseCase(
    private val context: Context,
) {
    operator fun invoke(): List<ChangelogItem> {
    return emptyList()
}

}