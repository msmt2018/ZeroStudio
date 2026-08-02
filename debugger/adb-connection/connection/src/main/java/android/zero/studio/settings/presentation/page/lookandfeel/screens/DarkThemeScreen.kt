@file:OptIn(ExperimentalMaterial3Api::class)

package android.zero.studio.settings.presentation.page.lookandfeel.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.zero.studio.R
import android.zero.studio.core.common.LocalSettings
import android.zero.studio.settings.data.SettingsKeys
import android.zero.studio.settings.presentation.components.scaffold.SettingsScaffold
import android.zero.studio.settingsdsl.ui.highlight.rememberHighlightState
import android.zero.studio.settings.presentation.state.rememberController
import android.zero.studio.settings.presentation.viewmodel.SettingsViewModel
import android.zero.studio.settingsdsl.resolver.resolveAll
import android.zero.studio.settingsdsl.ui.item.settingsContent

@Composable
fun DarkThemeScreen(
    modifier: Modifier = Modifier,
    highlightKey: String? = null,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val controller = settingsViewModel.rememberController()
    val hapticsEnabled = LocalSettings.current[SettingsKeys.HapticsAndVibration]

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val highlightedKey = rememberHighlightState(
        highlightKeyName = highlightKey,
        page = settingsViewModel.darkThemePage,
        listState = listState,
        headerItemCount = 0,
        keyResolver = { SettingsKeys.valueOfOrNull(it) },
        topAppBarState = topAppBarState,
    )

    val page = remember { settingsViewModel.darkThemePage }
    val resolvedGroups = page.resolveAll(highlightedKey = highlightedKey)

    SettingsScaffold(
        modifier = modifier,
        listState = listState,
        topAppBarState = topAppBarState,
        topBarTitle = stringResource(R.string.dark_theme),
        content = { innerPadding, topBarScrollBehavior ->
            LazyColumn(
                modifier = Modifier.fillMaxWidth().nestedScroll(topBarScrollBehavior.nestedScrollConnection),
                state = listState,
                contentPadding = innerPadding,
            ) {
                settingsContent(
                    groups = resolvedGroups,
                    controller = controller,
                    hapticsEnabled = hapticsEnabled
                )

                item { Spacer(modifier = Modifier.fillMaxWidth().height(25.dp)) }
            }
        },
    )
}
