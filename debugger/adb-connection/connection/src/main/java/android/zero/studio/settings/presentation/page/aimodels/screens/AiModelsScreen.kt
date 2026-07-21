@file:OptIn(ExperimentalMaterial3Api::class)

package android.zero.studio.settings.presentation.page.aimodels.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.zero.studio.R
import android.zero.studio.ai.data.local.model.ModelRegistry
import android.zero.studio.ai.presentation.viewmodel.AiModelManagerViewModel
import android.zero.studio.core.common.LocalDialogManager
import android.zero.studio.core.common.LocalSettings
import android.zero.studio.core.presentation.components.dialog.DialogKey
import android.zero.studio.core.presentation.components.dialog.createDialog
import android.zero.studio.navigation.LocalNavController
import android.zero.studio.settings.data.SettingsKeys
import android.zero.studio.settings.presentation.components.dialog.DeleteAiAnalysisCacheDialog
import android.zero.studio.settings.presentation.components.scaffold.SettingsScaffold
import android.zero.studio.settings.presentation.event.SettingsUiEvent
import android.zero.studio.settings.presentation.components.dialog.CacheDurationDialog
import android.zero.studio.settings.presentation.state.rememberController
import android.zero.studio.settings.presentation.viewmodel.SettingsViewModel
import `in`.hridayan.settingsdsl.resolver.resolveAll
import `in`.hridayan.settingsdsl.ui.highlight.rememberHighlightState
import `in`.hridayan.settingsdsl.ui.item.settingsContent

@Composable
fun AiModelsScreen(
    modifier: Modifier = Modifier,
    highlightKey: String? = null,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    aiViewModel: AiModelManagerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val dialogManager = LocalDialogManager.current
    val controller = settingsViewModel.rememberController()
    val settings = LocalSettings.current
    val hapticsEnabled = settings[SettingsKeys.HapticsAndVibration]

    val selectedModelName = ModelRegistry.findById(settings[SettingsKeys.SelectedModelId])?.name
    val cacheDays = settings[SettingsKeys.AiCacheDays]

    val cacheSizeBytes by aiViewModel.cacheSizeBytes.collectAsState()

    val formattedSize = remember(cacheSizeBytes) {
        Formatter.formatShortFileSize(context, cacheSizeBytes)
    }

    LaunchedEffect(Unit) {
        aiViewModel.refreshCacheSize()
    }

    LaunchedEffect(Unit) {
        settingsViewModel.uiEvent.collect { event ->
            when (event) {
                is SettingsUiEvent.Navigate -> navController.navigate(event.route)
                is SettingsUiEvent.ShowDialog -> dialogManager.show(event.key)
                else -> {}
            }
        }
    }

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val highlightedKey = rememberHighlightState(
        highlightKeyName = highlightKey,
        page = settingsViewModel.aiModelsPage,
        listState = listState,
        headerItemCount = 0,
        keyResolver = { SettingsKeys.valueOfOrNull(it) },
        topAppBarState = topAppBarState,
    )

    val page = remember { settingsViewModel.aiModelsPage }

    val resolvedGroups = page.resolveAll(
        highlightedKey = highlightedKey,
        descriptionOverrides = mapOf(
            SettingsKeys.AiModels to {
                selectedModelName ?: stringResource(R.string.no_model_selected)
            },
            SettingsKeys.AiCacheDays to {
                stringResource(R.string.n_days, cacheDays)
            },
            SettingsKeys.AiCacheClear to {
                stringResource(R.string.cache_size, formattedSize)
            }
        ),
    )

    SettingsScaffold(
        modifier = modifier,
        listState = listState,
        topAppBarState = topAppBarState,
        topBarTitle = stringResource(R.string.ai_models),
        content = { innerPadding, topBarScrollBehavior ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
                state = listState,
                contentPadding = innerPadding,
            ) {
                settingsContent(
                    groups = resolvedGroups,
                    controller = controller,
                    hapticsEnabled = hapticsEnabled
                )

                item {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(25.dp)
                    )
                }
            }
        },
    )

    // Cache duration dialog
    DialogKey.Settings.AiCacheDays.createDialog { dm ->
        CacheDurationDialog(
            currentDays = cacheDays,
            onDismiss = { dm.dismiss() },
            onConfirm = { days ->
                settingsViewModel.setInt(SettingsKeys.AiCacheDays, days)
                dm.dismiss()
            }
        )
    }

    DialogKey.Settings.AiCacheClearConfirmation.createDialog {
        DeleteAiAnalysisCacheDialog(
            onDismiss = { it.dismiss() },
            onDelete = {
                aiViewModel.clearCache()
                it.dismiss()
            })
    }
}