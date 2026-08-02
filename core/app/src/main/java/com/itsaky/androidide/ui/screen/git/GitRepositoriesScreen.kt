package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 7. 仓库管理：类似工作区切换的多仓库卡片网格 UI 空壳。 */
@Composable
fun GitRepositoriesScreen() {
    val repos = listOf("current" to "Current repi", "current" to "Currapgo", "current" to "Chgpsgo", "regogav" to "Current rep", "repspsls" to "Syre rep", "iepscoin" to "repo")
    GitPhoneScreen {
        GitTopBar("我的仓库")
        repos.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { repo ->
                    GitCard(Modifier.weight(1f).padding(5.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("◉ ${repo.first}", style = GitMockText.body.copy(color = GitMockColor.blue))
                            Text(repo.second, style = GitMockText.small)
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⑂", style = GitMockText.small)
                                Spacer(Modifier.width(8.dp))
                                GitPill(if (repo.first == "iepscoin") "Branch" else "current", color = GitMockColor.blueDark)
                                Spacer(Modifier.weight(1f))
                                Text("⟳", style = GitMockText.small)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GitPill("＋ Add Clone") }
        GitBottomHandle()
    }
}
