package android.zero.studio.settings.data.repository

import android.content.Context
import android.zero.studio.settings.data.mapper.toGitHubContributor
import android.zero.studio.settings.data.mapper.toTranslator
import android.zero.studio.settings.data.parser.GitHubContributorParser
import android.zero.studio.settings.data.parser.TranslatorParser
import android.zero.studio.settings.domain.model.GitHubContributor
import android.zero.studio.settings.domain.model.Translator
import android.zero.studio.settings.domain.repository.ContributorsRepository

class ContributorsRepositoryImpl(
    private val context: Context
) : ContributorsRepository {

    override fun getTranslators(): List<Translator> {

        return TranslatorParser
            .loadJson(context)
            .sortedByDescending { it.translated }
            .map { it.toTranslator() }
    }

    override fun getGitHubContributors(): List<GitHubContributor> {

        return GitHubContributorParser
            .loadJson(context)
            .sortedByDescending { it.contributions }
            .map { it.toGitHubContributor() }
    }
}