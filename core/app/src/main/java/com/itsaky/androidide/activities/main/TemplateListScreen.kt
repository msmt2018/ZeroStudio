/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.activities.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.R
import com.itsaky.androidide.templates.ITemplateProvider
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.Template
import com.itsaky.androidide.templates.TemplateCategory

/**
 * 纯 Compose 版项目模板选择页。
 * 替代旧版 ViewPager2 + TabLayout + TemplateListFragment。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    onSelectTemplate: (Template<*>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val provider = remember { ITemplateProvider.getInstance(reload = true) }
  val categories = remember { provider.getRegisteredCategories() }

  var selectedCategoryIndex by remember { mutableIntStateOf(0) }
  val currentCategory = categories.getOrNull(selectedCategoryIndex)

  val subCategories = remember(currentCategory) {
    currentCategory?.let { TemplateCategory.getSubCategories(it) }.orEmpty()
  }
  var selectedSubCategoryIndex by remember { mutableIntStateOf(0) }

  // 切换大分类时重置子分类
  LaunchedEffect(selectedCategoryIndex) {
    selectedSubCategoryIndex = 0
  }

  val templates = remember(currentCategory, selectedSubCategoryIndex) {
    if (currentCategory == null) emptyList<ProjectTemplate>()
    else {
      provider.getTemplatesFor(currentCategory)
          .filterIsInstance<ProjectTemplate>()
    }
  }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      topBar = {
        TopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.title_templates),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
              )
            },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
      },
  ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
      if (categories.isNotEmpty()) {
        ScrollableTabRow(
            selectedTabIndex = selectedCategoryIndex.coerceIn(0, categories.lastIndex),
            edgePadding = 16.dp,
        ) {
          categories.forEachIndexed { index, category ->
            Tab(
                selected = selectedCategoryIndex == index,
                onClick = { selectedCategoryIndex = index },
                text = { Text(category.title, maxLines = 1) },
            )
          }
        }
      }

      if (subCategories.size > 1) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(subCategories.indices.toList()) { index ->
            val sub = subCategories[index]
            FilterChip(
                selected = selectedSubCategoryIndex == index,
                onClick = { selectedSubCategoryIndex = index },
                label = { Text(sub.title, fontSize = 12.sp) },
            )
          }
        }
      }

      if (templates.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = "No templates available in this category.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(templates, key = { it.id }) { template ->
            TemplateCardItem(
                template = template,
                onClick = { onSelectTemplate(template) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TemplateCardItem(
    template: ProjectTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier
          .fillMaxWidth()
          .clickable(onClick = onClick),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Image(
          painter = painterResource(template.thumb),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier
              .fillMaxWidth()
              .height(100.dp)
              .clip(RoundedCornerShape(10.dp)),
      )

      Spacer(modifier = Modifier.height(10.dp))

      Text(
          text = stringResource(template.templateName),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )

      template.description?.let { descRes ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(descRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}