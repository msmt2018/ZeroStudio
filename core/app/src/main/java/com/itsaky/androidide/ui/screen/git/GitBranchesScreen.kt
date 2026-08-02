package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 2. 分支列表：本地/远程双标签、当前分支置顶和触控操作入口 UI 空壳。 */
@Composable
fun GitBranchesScreen() {
    GitPhoneScreen {
        GitTopBar("分支列表")
        Row(Modifier.fillMaxWidth()) {
            GitPill("本地 (4)", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            GitPill("远程 (10)", color = GitMockColor.panel, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        listOf("main" to "Last commit m1 · P. Bear", "feature/login (current)" to "Last push now · P. cat", "alarms.xml" to "2 changes and img.xml").forEachIndexed { index, branch ->
            GitCard(Modifier.fillMaxWidth().padding(vertical = 5.dp), radius = 9.dp) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (index == 1) "⑂" else "⌁", style = GitMockText.body.copy(color = GitMockColor.blue))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(branch.first, style = GitMockText.body)
                        Text(branch.second, style = GitMockText.small)
                    }
                    Text(if (index == 1) "■  ⋮" else "⌘  ⋮", style = GitMockText.small.copy(color = if (index == 1) GitMockColor.red else GitMockColor.secondary))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GitPill("New Branch", color = GitMockColor.background)
            Spacer(Modifier.width(10.dp))
            GitPill("＋")
        }
    }
}
