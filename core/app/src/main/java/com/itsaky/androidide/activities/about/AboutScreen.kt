/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.activities.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.BuildConfig
import com.itsaky.androidide.R
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.app.configuration.IDEBuildConfigProvider
import com.itsaky.androidide.buildinfo.BuildInfo
import com.itsaky.androidide.utils.BuildInfoUtils
import com.itsaky.androidide.utils.flashSuccess

/**
 * 关于页面。
  * @author android_zero
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenContributors: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val app = context.applicationContext as BaseApplication
  val clipboardManager = LocalClipboardManager.current
  val scrollState = rememberScrollState()

  val colorScheme = MaterialTheme.colorScheme
  val colorPositive = remember { Color(0xFF2E7D32) }
  val colorNegative = remember { Color(0xFFC62828) }
  val isOfficial = remember { BuildInfoUtils.isOfficialBuild(context) }

  val versionAnnotatedText = remember(colorPositive, colorNegative, isOfficial) {
    buildAnnotatedString {
      append("Version: ")
      append(BuildInfo.VERSION_NAME_SIMPLE)
      append("-")
      append(IDEBuildConfigProvider.getInstance().cpuAbiName)
      append(" (")

      val buildTypeColor = if (BuildConfig.BUILD_TYPE == "release") colorPositive else colorNegative
      withStyle(SpanStyle(color = buildTypeColor, fontWeight = FontWeight.Bold)) {
        append(BuildConfig.BUILD_TYPE)
      }

      append("/")

      val officialColor = if (isOfficial) colorPositive else colorNegative
      withStyle(SpanStyle(color = officialColor, fontWeight = FontWeight.Bold)) {
        append(BuildInfoUtils.getBuildType(context).lowercase())
      }

      append(")")
    }
  }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      topBar = {
        TopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.about),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
              )
            },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surface,
                titleContentColor = colorScheme.onSurface,
            ),
        )
      },
  ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // App 头部信息卡片
      Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
      ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Image(
              painter = painterResource(R.mipmap.ic_launcher),
              contentDescription = stringResource(R.string.app_name),
              modifier = Modifier.size(80.dp),
          )

          Spacer(modifier = Modifier.height(12.dp))

          Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = colorScheme.onSurface,
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text(
              text = stringResource(R.string.greeting_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
          )

          Spacer(modifier = Modifier.height(14.dp))

          // 点击复制详细构建信息
          Surface(
              shape = RoundedCornerShape(12.dp),
              color = colorScheme.surfaceContainerHigh,
              modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {
                    clipboardManager.setText(AnnotatedString(BuildInfoUtils.getBuildInfoHeader()))
                    context.flashSuccess(R.string.copied)
                  },
          ) {
            Text(
                text = versionAnnotatedText,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
          }
        }
      }

      // 社区与社交卡片
      SectionCard(title = stringResource(R.string.title_socials)) {
        AboutItemRow(
            icon = R.drawable.ic_website,
            title = stringResource(R.string.about_option_website),
            description = BuildInfo.PROJECT_SITE,
            onClick = { app.openWebsite() },
        )
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
        AboutItemRow(
            icon = R.drawable.ic_email,
            title = stringResource(R.string.about_option_email),
            description = BaseApplication.EMAIL,
            onClick = { app.emailUs() },
        )
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
        AboutItemRow(
            icon = R.drawable.ic_telegram,
            title = stringResource(R.string.discussions_on_telegram),
            description = BaseApplication.TELEGRAM_GROUP_URL,
            onClick = { app.openTelegramGroup() },
        )
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
        AboutItemRow(
            icon = R.drawable.ic_telegram,
            title = stringResource(R.string.official_tg_channel),
            description = BaseApplication.TELEGRAM_CHANNEL_URL,
            onClick = { app.openTelegramChannel() },
        )
      }

      // 参与贡献卡片
      SectionCard(title = stringResource(R.string.title_misc)) {
        AboutItemRow(
            icon = R.drawable.ic_code,
            title = stringResource(R.string.title_contribute),
            description = stringResource(R.string.summary_contribute),
            onClick = { app.openUrl(BaseApplication.CONTRIBUTOR_GUIDE_URL) },
        )
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
        AboutItemRow(
            icon = R.drawable.ic_heart_outline,
            title = stringResource(R.string.title_contributors),
            description = stringResource(R.string.summary_contributors),
            onClick = onOpenContributors,
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        content()
      }
    }
  }
}

@Composable
private fun AboutItemRow(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier
          .fillMaxWidth()
          .clickable(onClick = onClick)
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
    )

    Spacer(modifier = Modifier.width(16.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}