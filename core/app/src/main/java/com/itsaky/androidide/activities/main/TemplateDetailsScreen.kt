/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.activities.main

import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.itsaky.androidide.R
import com.itsaky.androidide.templates.ITemplateWidgetViewProvider
import com.itsaky.androidide.templates.Template

/**
 * 纯 Compose 版模板工程详情与配置向导。
 * 替代旧版基于 XML 与 Fragment 的 TemplateDetailsFragment。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDetailsScreen(
    template: Template<*>?,
    isCreating: Boolean,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()

  Scaffold(
      modifier = modifier.fillMaxSize(),
      topBar = {
        Column {
          TopAppBar(
              title = {
                Text(
                    text = template?.templateName?.let { stringResource(it) } ?: "New Project",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
              },
              navigationIcon = {
                IconButton(onClick = onBack, enabled = !isCreating) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
              },
              colors = TopAppBarDefaults.topAppBarColors(
                  containerColor = MaterialTheme.colorScheme.surface,
              ),
          )
          if (isCreating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }
        }
      },
      bottomBar = {
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isCreating,
            ) {
              Text(stringResource(R.string.previous))
            }

            Button(
                onClick = onFinish,
                enabled = !isCreating && template != null,
            ) {
              Text(stringResource(R.string.finish))
            }
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
      if (template != null) {
        // 渲染模板内置参数控件组（输入框、包名、语言选项等）
        template.widgets.forEach { widget ->
          AndroidView(
              factory = { ctx ->
                val view = ITemplateWidgetViewProvider.getInstance().createView(ctx, widget)
                LinearLayout(ctx).apply {
                  orientation = LinearLayout.VERTICAL
                  addView(
                      view,
                      LinearLayout.LayoutParams(
                          LinearLayout.LayoutParams.MATCH_PARENT,
                          LinearLayout.LayoutParams.WRAP_CONTENT,
                      ),
                  )
                }
              },
              modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}