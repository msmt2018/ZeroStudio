package android.zero.studio.settings.domain.usecase

import android.content.Context
import android.zero.studio.R
import android.zero.studio.settings.data.source.VersionToChangelogs
import android.zero.studio.settings.data.source.versionToChangelogsMap
import android.zero.studio.settings.domain.model.ChangelogItem

class GetAllChangelogsUseCase(
    private val context: Context,
    private val versionToChangelogs: List<VersionToChangelogs> = versionToChangelogsMap
) {
    operator fun invoke(): List<ChangelogItem> {
        return versionToChangelogs.map { item ->

            val text = context.getString(
                if (item.resId != 0) item.resId else R.string.no_changelog_found
            )

            ChangelogItem(versionName = item.version, changelog = text)
        }
    }
}