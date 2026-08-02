package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 4. 修改列表：核心枢纽面板，未跟踪/已修改/已暂存 + 底部提交并推送 UI 空壳。 */
@Composable
fun GitChangesScreen() {
    GitPhoneScreen {
        GitTopBar("当前修改 (3)")
        Section("未跟踪 (1)", listOf("README.md" to "README.md"))
        Section("已修改 (3)", listOf("app.js" to "app.js", "style.css" to "style.css_style.css"))
        Section("已暂存 (2)", listOf("main_act.kt" to "main_aet.kt", "stings.xml" to "strings.xml"))
        Spacer(Modifier.weight(1f))
        GitCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Commit Message", style = GitMockText.top)
                    Text("Commit", style = GitMockText.small)
                    Spacer(Modifier.height(10.dp))
                    GitPill("暂存全部 (Add All)", color = GitMockColor.cardSoft)
                }
                GitPill("⌘ 提交并推送")
            }
        }
        GitBottomHandle()
    }
}

@Composable
private fun Section(title: String, files: List<Pair<String, String>>) {
    Text(title, style = GitMockText.top, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    files.chunked(2).forEach { rowFiles ->
        Row(Modifier.fillMaxWidth()) {
            rowFiles.forEach { file ->
                GitCard(Modifier.weight(1f).padding(end = 8.dp, bottom = 8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GitFileIcon(file.first.take(1), GitMockColor.yellow)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.first, style = GitMockText.body)
                            Text(file.second, style = GitMockText.small)
                        }
                        Text("›", style = GitMockText.body)
                    }
                }
            }
            if (rowFiles.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
