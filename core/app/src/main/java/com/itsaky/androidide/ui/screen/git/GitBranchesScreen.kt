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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 2. 分支列表：本地/远程双标签、当前分支置顶和触控操作入口 UI 空壳。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitBranchesScreen() {
    val branches = listOf(
        "main" to "Last commit m1 · P. Bear",
        "feature/login (current)" to "Last push now · P. cat",
        "alarms.xml" to "2 changes and img.xml",
    )

    GitPhoneScreen {
        GitTopBar("分支列表")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GitPill("本地 (4)", modifier = Modifier.weight(1f))
            GitPill("远程 (10)", color = GitMockColor.panel, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(branches, key = { _, branch -> branch.first }) { index, branch ->
                GitCard(Modifier.fillMaxWidth(), radius = 9.dp) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (index == 1) "⑂" else "⌁", style = GitMockText.body.copy(color = GitMockColor.blue))
                        Spacer(Modifier.weight(.12f))
                        Column(Modifier.weight(1f)) {
                            Text(branch.first, style = GitMockText.body)
                            Text(branch.second, style = GitMockText.small)
                        }
                        Text(if (index == 1) "■  ⋮" else "⌘  ⋮", style = GitMockText.small.copy(color = if (index == 1) GitMockColor.red else GitMockColor.secondary))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GitPill("New Branch", color = GitMockColor.background)
            GitPill("＋")
        }
    }
}