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

package com.itsaky.androidide.activities.contributors

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bumptech.glide.Glide
import com.itsaky.androidide.R
import com.itsaky.androidide.app.IDEApplication
import com.itsaky.androidide.contributors.Contributor
import com.itsaky.androidide.contributors.CrowdinTranslator
import com.itsaky.androidide.contributors.GitHubContributor
import com.itsaky.androidide.utils.getConnectionInfo
import com.itsaky.androidide.viewmodel.ContributorsViewModel

/**
 * 贡献者展示页面。
  * @author android_zero
 * 采用自适应流式网格（FlowRow）渲染头像，复用 Glide 图像缓存机制。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContributorsScreen(
    viewModel: ContributorsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scrollState = rememberScrollState()

  var crowdinTranslators by remember { mutableStateOf(emptyList<CrowdinTranslator>()) }
  var githubContributors by remember { mutableStateOf(emptyList<GitHubContributor>()) }
  var isLoading by remember { mutableStateOf(false) }

  val connectionInfo = remember { getConnectionInfo(context) }

  // 跨生命周期安全监听 ViewModel 中的数据变化
  DisposableEffect(lifecycleOwner) {
    val crowdinObserver = Observer<List<CrowdinTranslator>> { crowdinTranslators = it }
    val githubObserver = Observer<List<GitHubContributor>> { githubContributors = it }
    val loadingObserver = Observer<Boolean> { isLoading = it }

    viewModel._crowdinTranslators.observe(lifecycleOwner, crowdinObserver)
    viewModel._githubContributors.observe(lifecycleOwner, githubObserver)
    viewModel.observeLoadingState(lifecycleOwner, loadingObserver)

    onDispose {
      viewModel._crowdinTranslators.removeObserver(crowdinObserver)
      viewModel._githubContributors.removeObserver(githubObserver)
    }
  }

  LaunchedEffect(Unit) {
    if (connectionInfo.isConnected) {
      viewModel.fetchAll()
    }
  }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      topBar = {
        Column {
          TopAppBar(
              title = {
                Text(
                    text = stringResource(R.string.title_contributors),
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
                  containerColor = MaterialTheme.colorScheme.surface,
                  titleContentColor = MaterialTheme.colorScheme.onSurface,
              ),
          )
          if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }
        }
      },
  ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (!connectionInfo.isConnected) {
        // 无网络连接卡片
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.msg_no_internet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }

      // GitHub 贡献者卡片
      ContributorsSectionCard(
          title = stringResource(R.string.title_github_contributors),
          contributors = githubContributors,
      )

      // Crowdin 翻译者卡片
      ContributorsSectionCard(
          title = stringResource(R.string.title_crowdin_translators),
          contributors = crowdinTranslators,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContributorsSectionCard(
    title: String,
    contributors: List<Contributor>,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(bottom = 12.dp),
      )

      if (contributors.isEmpty()) {
        Text(
            text = "Loading contributors...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
      } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          contributors.forEach { contributor ->
            ContributorAvatarItem(contributor = contributor)
          }
        }
      }
    }
  }
}

@Composable
private fun ContributorAvatarItem(
    contributor: Contributor,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier = modifier
          .size(48.dp)
          .clip(CircleShape)
          .clickable {
            if (contributor.profileUrl.isNotBlank()) {
              IDEApplication.instance.openUrl(contributor.profileUrl)
            }
          },
  ) {
    AndroidView(
        factory = { ctx ->
          ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
          }
        },
        update = { imageView ->
          Glide.with(imageView)
              .load(contributor.avatarUrl)
              .placeholder(R.drawable.ic_account)
              .circleCrop()
              .into(imageView)
        },
        modifier = Modifier.fillMaxSize(),
    )
  }
}