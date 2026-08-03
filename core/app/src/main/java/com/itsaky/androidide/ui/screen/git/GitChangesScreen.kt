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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 4. 修改列表：核心枢纽面板，未跟踪/已修改/已暂存 + 底部提交并推送 UI 空壳。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitChangesScreen() {
    GitPhoneScreen {
        GitTopBar("当前修改 (3)")
        ChangeSection("未跟踪 (1)", listOf("README.md" to "README.md"))
        ChangeSection("已修改 (3)", listOf("app.js" to "app.js", "style.css" to "style.css_style.css"))
        ChangeSection("已暂存 (2)", listOf("main_act.kt" to "main_aet.kt", "stings.xml" to "strings.xml"))
        Spacer(Modifier.weight(1f))
        GitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Commit Message", style = GitMockText.top)
                Text("Commit", style = GitMockText.small)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GitPill("暂存全部 (Add All)", color = GitMockColor.cardSoft)
                    GitPill("⌘ 提交并推送")
                }
            }
        }
        GitBottomHandle()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangeSection(title: String, files: List<Pair<String, String>>) {
    Text(title, style = GitMockText.top, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        files.forEach { file ->
            GitCard(Modifier.weight(1f).height(58.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GitFileIcon(file.first.take(1), GitMockColor.yellow)
                    Spacer(Modifier.weight(.12f))
                    Column(Modifier.weight(1f)) {
                        Text(file.first, style = GitMockText.body)
                        Text(file.second, style = GitMockText.small)
                    }
                    Text("›", style = GitMockText.body)
                }
            }
        }
    }
}