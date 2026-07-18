package android.zero.studio.settings.domain.repository

import android.zero.studio.settings.domain.model.GitHubContributor
import android.zero.studio.settings.domain.model.Translator

interface ContributorsRepository {

    fun getTranslators(): List<Translator>

    fun getGitHubContributors(): List<GitHubContributor>
}