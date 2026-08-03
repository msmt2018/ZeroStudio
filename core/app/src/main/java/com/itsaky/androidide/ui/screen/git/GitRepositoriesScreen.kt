package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 7. 仓库管理：使用 Compose 流式布局管理仓库项目卡片，适配不同屏幕宽度。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitRepositoriesScreen() {
    val repos = listOf(
        GitRepositoryCardState("current", "Current repi", "current"),
        GitRepositoryCardState("current", "Currapgo", "current"),
        GitRepositoryCardState("current", "Chgpsgo", "current"),
        GitRepositoryCardState("regogav", "Current rep", "current"),
        GitRepositoryCardState("repspsls", "Syre rep", "current"),
        GitRepositoryCardState("iepscoin", "repo", "Branch"),
    )

    GitPhoneScreen {
        GitTopBar("我的仓库")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2,
        ) {
            repos.forEach { repo ->
                RepositoryProjectCard(
                    repo = repo,
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GitPill("＋ Add Clone") }
        GitBottomHandle()
    }
}

@Composable
private fun RepositoryProjectCard(repo: GitRepositoryCardState, modifier: Modifier = Modifier) {
    GitCard(modifier) {
        Column(Modifier.padding(10.dp)) {
            Text("◉ ${repo.name}", style = GitMockText.body.copy(color = GitMockColor.blue))
            Text(repo.subtitle, style = GitMockText.small)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⑂", style = GitMockText.small)
                Spacer(Modifier.weight(.12f))
                GitPill(repo.branchLabel, color = GitMockColor.blueDark)
                Spacer(Modifier.weight(1f))
                Text("⟳", style = GitMockText.small)
            }
        }
    }
}

private data class GitRepositoryCardState(
    val name: String,
    val subtitle: String,
    val branchLabel: String,
)
