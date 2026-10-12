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

package com.itsaky.androidide.activities.preferences

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.itsaky.androidide.R
import com.itsaky.androidide.preferences.DialogPreference
import com.itsaky.androidide.preferences.EditTextPreference
import com.itsaky.androidide.preferences.IPreference
import com.itsaky.androidide.preferences.IPreferenceGroup
import com.itsaky.androidide.preferences.IPreferenceScreen
import com.itsaky.androidide.preferences.SimpleClickablePreference
import com.itsaky.androidide.preferences.SimplePreference
import com.itsaky.androidide.preferences.SingleChoicePreference
import com.itsaky.androidide.preferences.SwitchPreference

/**
 * 纯 Compose 版首选项设置屏幕。
 * 递归解析已存在的 IPreference 结构树，完全摆脱对 PreferenceFragmentCompat 和 XML 的依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    rootChildren: List<IPreference>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  // 维护多级子设置屏幕的导航栈
  var screenStack by remember { mutableStateOf<List<IPreferenceScreen>>(emptyList()) }
  val currentScreen = screenStack.lastOrNull()

  // 处理物理与手势返回逻辑
  BackHandler(enabled = screenStack.isNotEmpty()) {
    screenStack = screenStack.dropLast(1)
  }

  val title = if (currentScreen != null) {
    resolvePrefText(currentScreen.title)
  } else {
    stringResource(R.string.ide_preferences)
  }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      topBar = {
        TopAppBar(
            title = {
              Text(
                  text = title,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
              )
            },
            navigationIcon = {
              IconButton(onClick = {
                if (screenStack.isNotEmpty()) {
                  screenStack = screenStack.dropLast(1)
                } else {
                  onBack()
                }
              }) {
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
      },
  ) { innerPadding ->
    val itemsToShow = currentScreen?.children ?: rootChildren

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      items(itemsToShow, key = { it.key }) { prefItem ->
        if (prefItem is IPreferenceGroup) {
          PreferenceGroupCard(
              group = prefItem,
              onNavigateScreen = { screenStack = screenStack + it },
          )
        } else {
          Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            PreferenceNodeItem(
                item = prefItem,
                onNavigateScreen = { screenStack = screenStack + it },
            )
          }
        }
      }
      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun PreferenceGroupCard(
    group: IPreferenceGroup,
    onNavigateScreen: (IPreferenceScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    val groupTitle = resolvePrefText(group.title)
    if (groupTitle.isNotBlank()) {
      Text(
          text = groupTitle,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
      )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        group.children.forEachIndexed { index, child ->
          PreferenceNodeItem(
              item = child,
              onNavigateScreen = onNavigateScreen,
          )
          if (index < group.children.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
        }
      }
    }
  }
}

@Composable
private fun PreferenceNodeItem(
    item: IPreference,
    onNavigateScreen: (IPreferenceScreen) -> Unit,
) {
  val context = LocalContext.current
  // 为复用现有的 preferences 扩展中的逻辑，惰性生成对应的 Preference 桥接对象
  val bridgePref = remember(item) {
    runCatching { item.onCreateView(context) }.getOrNull()
  }

  when (item) {
    is IPreferenceScreen -> {
      PreferenceRow(
          title = resolvePrefText(item.title, bridgePref?.title),
          summary = resolvePrefText(item.summary, bridgePref?.summary),
          icon = item.icon,
          onClick = { onNavigateScreen(item) },
          trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
      )
    }

    is SwitchPreference -> {
      var isChecked by remember {
        mutableStateOf(
            item.getValue?.invoke()
                ?: (bridgePref as? TwoStatePreference)?.isChecked
                ?: false
        )
      }

      PreferenceRow(
          title = resolvePrefText(item.title, bridgePref?.title),
          summary = resolvePrefText(item.summary, bridgePref?.summary),
          icon = item.icon,
          onClick = {
            val next = !isChecked
            isChecked = next
            item.setValue?.invoke(next)
            (bridgePref as? TwoStatePreference)?.let {
              it.isChecked = next
              it.callChangeListener(next)
            }
          },
          trailing = {
            Switch(
                checked = isChecked,
                onCheckedChange = { next ->
                  isChecked = next
                  item.setValue?.invoke(next)
                  (bridgePref as? TwoStatePreference)?.let {
                    it.isChecked = next
                    it.callChangeListener(next)
                  }
                },
            )
          },
      )
    }

    is SingleChoicePreference -> {
      var showDialog by remember { mutableStateOf(false) }
      var dynamicSummary by remember { mutableStateOf(bridgePref?.summary?.toString()) }

      PreferenceRow(
          title = resolvePrefText(item.title, bridgePref?.title),
          summary = dynamicSummary ?: resolvePrefText(item.summary),
          icon = item.icon,
          onClick = { showDialog = true },
      )

      if (showDialog && bridgePref != null) {
        val entries = remember(bridgePref) { item.getEntries(bridgePref) }
        var selectedIndex by remember {
          mutableIntStateOf(entries.indexOfFirst { it.isChecked }.coerceAtLeast(0))
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(resolvePrefText(item.title, bridgePref.title)) },
            text = {
              Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                entries.forEachIndexed { index, entry ->
                  Row(
                      modifier = Modifier
                          .fillMaxWidth()
                          .clickable { selectedIndex = index }
                          .padding(vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                    RadioButton(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(entry.label.toString(), style = MaterialTheme.typography.bodyLarge)
                  }
                }
              }
            },
            confirmButton = {
              TextButton(onClick = {
                val chosen = entries.getOrNull(selectedIndex)
                item.onChoiceConfirmed(bridgePref, chosen, selectedIndex)
                dynamicSummary = bridgePref.summary?.toString()
                showDialog = false
              }) {
                Text(stringResource(android.R.string.ok))
              }
            },
            dismissButton = {
              TextButton(onClick = { showDialog = false }) {
                Text(stringResource(android.R.string.cancel))
              }
            },
        )
      }
    }

    is EditTextPreference -> {
      var showDialog by remember { mutableStateOf(false) }
      var currentText by remember { mutableStateOf("") }
      var dynamicSummary by remember { mutableStateOf(bridgePref?.summary?.toString()) }

      PreferenceRow(
          title = resolvePrefText(item.title, bridgePref?.title),
          summary = dynamicSummary ?: resolvePrefText(item.summary),
          icon = item.icon,
          onClick = {
            currentText = dynamicSummary ?: ""
            showDialog = true
          },
      )

      if (showDialog && bridgePref != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(resolvePrefText(item.title, bridgePref.title)) },
            text = {
              OutlinedTextField(
                  value = currentText,
                  onValueChange = { currentText = it },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true,
              )
            },
            confirmButton = {
              TextButton(onClick = {
                item.onPreferenceChanged(bridgePref, currentText)
                dynamicSummary = currentText
                showDialog = false
              }) {
                Text(stringResource(android.R.string.ok))
              }
            },
            dismissButton = {
              TextButton(onClick = { showDialog = false }) {
                Text(stringResource(android.R.string.cancel))
              }
            },
        )
      }
    }

    is DialogPreference -> {
      PreferenceRow(
          title = resolvePrefText(item.title, bridgePref?.title),
          summary = resolvePrefText(item.summary, bridgePref?.summary),
          icon = item.icon,
          onClick = {
            if (bridgePref != null) {
              val builder = MaterialAlertDialogBuilder(context)
              item.onConfigureDialog(bridgePref, builder)
              builder.show()
            }
          },
      )
    }

    is SimplePreference -> {
      PreferenceRow(
          title = resolvePrefText(item.title, bridgePref?.title),
          summary = resolvePrefText(item.summary, bridgePref?.summary),
          icon = item.icon,
          onClick = {
            if (bridgePref != null) {
              item.onPreferenceClick(bridgePref)
            }
          },
      )
    }

    is SimpleClickablePreference -> {
      PreferenceRow(
          title = resolvePrefText(item.title, bridgePref?.title),
          summary = resolvePrefText(item.summary, bridgePref?.summary),
          icon = item.icon,
          onClick = {
            if (bridgePref != null) {
              item.onPreferenceClick(bridgePref)
            }
          },
      )
    }

    else -> {
      PreferenceRow(
          title = resolvePrefText(item.key),
          summary = null,
          icon = null,
          onClick = {},
      )
    }
  }
}

@Composable
private fun PreferenceRow(
    title: String,
    summary: String?,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
  Row(
      modifier = modifier
          .fillMaxWidth()
          .clickable(onClick = onClick)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null && icon != 0) {
      Icon(
          painter = painterResource(icon),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.width(16.dp))
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      if (!summary.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }

    if (trailing != null) {
      Spacer(modifier = Modifier.width(12.dp))
      trailing()
    }
  }
}

@Composable
private fun resolvePrefText(resOrText: Any?, dynamicFallback: CharSequence? = null): String {
  if (!dynamicFallback.isNullOrBlank()) return dynamicFallback.toString()
  return when (resOrText) {
    is Int -> if (resOrText != 0) stringResource(resOrText) else ""
    is CharSequence -> resOrText.toString()
    else -> ""
  }
}