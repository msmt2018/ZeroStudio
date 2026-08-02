package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 8. 设置中心：账号、认证托管、同步、主题、网络和应用信息入口 UI 空壳。 */
@Composable
fun GitSettingsScreen() {
    GitPhoneScreen {
        GitTopBar("设置中心")
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).background(Color(0xFFD9C1AB), CircleShape), contentAlignment = Alignment.Center) { Text("👤") }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("username", style = GitMockText.top)
                Text("bA37S", style = GitMockText.small)
                Text("usernamem@gmail.com", style = GitMockText.small)
            }
        }
        Spacer(Modifier.height(22.dp))
        listOf("认证托管 (Credentials)", "同步选项", "主题设置 (Themes)", "网络设置", "应用信息").forEach { item ->
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("◎", style = GitMockText.body.copy(color = GitMockColor.secondary), modifier = Modifier.width(34.dp))
                Text(item, style = GitMockText.body, modifier = Modifier.weight(1f))
                Text("›", style = GitMockText.body.copy(color = GitMockColor.secondary))
            }
        }
    }
}
