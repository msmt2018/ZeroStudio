package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 凭证管理界面。
 *
 * 对应 SVG 设计图 "右边顶部弹窗" 的用户资料区 + 认证菜单：
 * 顶部 "凭证管理" Header，用户资料卡（头像 + 姓名 + 邮箱），菜单列表
 * （认证 / SSH 密钥 / Git 配置 / 设置 / 帮助），以及 "Saved Credentials" 凭证项列表
 * （GitHub Token / SSH Key 标签）。使用静态 mock 数据。
 */
@Composable
fun GitCredentialsScreen() {
    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(GitColors.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "凭证管理",
                style = GitTypography.titleMedium.copy(color = GitColors.onSurface)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 用户资料卡 + 菜单列表（同一卡片，GitDivider 分隔）
            GitCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    // 用户资料 Row
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(GitColors.primary, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AZ",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Android Zero",
                                color = GitColors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "androidzero@example.com",
                                color = GitColors.outline,
                                fontSize = 11.sp
                            )
                        }
                    }
                    GitDivider()
                    // 菜单项
                    MenuItem("认证 (Token / Auth)", MenuTrailing.DOT)
                    GitDivider()
                    MenuItem("SSH 密钥管理", MenuTrailing.SQUARE)
                    GitDivider()
                    MenuItem("全局 Git 配置", MenuTrailing.SQUARE)
                    // 分组分隔（带额外留白）
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        GitDivider()
                    }
                    MenuItem("设置 (Settings)", MenuTrailing.GEAR)
                    GitDivider()
                    MenuItem("帮助与反馈", MenuTrailing.QUESTION)
                }
            }

            // 凭证列表标题
            Text(
                text = "Saved Credentials (2)",
                style = GitTypography.bodySmall.copy(color = GitColors.outline),
                modifier = Modifier.padding(top = 8.dp)
            )

            // 凭证项 1: GitHub Token
            CredentialItem(
                title = "GitHub Token",
                subtitle = "user@example.com",
                label = "Token",
                filled = true
            )
            // 凭证项 2: SSH Key
            CredentialItem(
                title = "SSH Key",
                subtitle = "~/.ssh/id_rsa",
                label = "SSH",
                filled = false
            )
        }
    }
}

/** 菜单项右侧图标类型。 */
private enum class MenuTrailing { DOT, SQUARE, GEAR, QUESTION }

/**
 * 菜单项：左侧标题 + 右侧图标（primary 圆点 / 灰色方块 / 齿轮 / 问号）。
 */
@Composable
private fun MenuItem(label: String, trailing: MenuTrailing) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = GitColors.onSurface,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        when (trailing) {
            MenuTrailing.DOT -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(GitColors.primary, RoundedCornerShape(50))
            )
            MenuTrailing.SQUARE -> Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(GitColors.outlineVariant)
            )
            MenuTrailing.GEAR -> Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(1.dp, GitColors.outline, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙",
                    color = GitColors.outline,
                    fontSize = 10.sp
                )
            }
            MenuTrailing.QUESTION -> Text(
                text = "?",
                color = GitColors.outline,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 凭证项：标题 + 副标题 + 右侧标签（Token 填充紫 / SSH 描边）。
 */
@Composable
private fun CredentialItem(
    title: String,
    subtitle: String,
    label: String,
    filled: Boolean
) {
    GitCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = GitColors.onSurface,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    color = GitColors.outline,
                    fontSize = 11.sp
                )
            }
            // 右侧标签
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 18.dp)
                    .then(
                        if (filled) {
                            Modifier.background(GitColors.primaryDim, RoundedCornerShape(4.dp))
                        } else {
                            Modifier.border(1.dp, GitColors.outlineVariant, RoundedCornerShape(4.dp))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (filled) GitColors.primary else GitColors.outline,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
