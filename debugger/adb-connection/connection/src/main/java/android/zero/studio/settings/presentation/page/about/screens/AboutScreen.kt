@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package android.zero.studio.settings.presentation.page.about.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.zero.studio.BuildConfig
import android.zero.studio.R
import android.zero.studio.core.common.LocalSettings
import android.zero.studio.core.common.constants.UrlConst
import android.zero.studio.core.presentation.components.animatedcomposables.AnimatedAdbIcon
import android.zero.studio.core.presentation.components.card.CustomCard
import android.zero.studio.core.presentation.components.haptic.withHaptic
import android.zero.studio.core.presentation.components.shape.SineWaveShape
import android.zero.studio.core.presentation.components.shape.WaveEdge
import android.zero.studio.core.presentation.components.svg.DynamicColorImageVectors
import android.zero.studio.core.presentation.components.svg.vectors.appBranding
import android.zero.studio.core.presentation.components.text.AutoResizeableText
import android.zero.studio.core.presentation.theme.CustomCardShape
import android.zero.studio.core.presentation.utils.syncedRotationAndScale
import android.zero.studio.core.utils.openUrl
import android.zero.studio.navigation.LocalNavController
import android.zero.studio.settings.data.SettingsKeys
import android.zero.studio.settings.presentation.components.card.SupportMeCard
import android.zero.studio.settings.presentation.components.image.ProfilePic
import android.zero.studio.settings.presentation.components.scaffold.SettingsScaffold
import android.zero.studio.settings.presentation.event.SettingsUiEvent
import android.zero.studio.settings.presentation.state.rememberController
import android.zero.studio.settings.presentation.viewmodel.SettingsViewModel
import android.zero.studio.settingsdsl.resolver.resolveAll
import android.zero.studio.settingsdsl.ui.highlight.rememberHighlightState
import android.zero.studio.settingsdsl.ui.item.settingsContent

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    highlightKey: String? = null,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val hapticsEnabled = LocalSettings.current[SettingsKeys.HapticsAndVibration]
    val controller = settingsViewModel.rememberController()
    val (angle, scale) = syncedRotationAndScale()

    LaunchedEffect(Unit) {
        settingsViewModel.uiEvent.collect { event ->
            when (event) {
                is SettingsUiEvent.Navigate -> navController.navigate(event.route)
                is SettingsUiEvent.OpenUrl -> openUrl(event.url, context)
                else -> {}
            }
        }
    }

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val highlightedKey = rememberHighlightState(
        highlightKeyName = highlightKey,
        page = settingsViewModel.aboutPage,
        listState = listState,
        headerItemCount = 2,
        keyResolver = { SettingsKeys.valueOfOrNull(it) },
        topAppBarState = topAppBarState,
    )

    val page = remember { settingsViewModel.aboutPage }
    val resolvedGroups = page.resolveAll(highlightedKey = highlightedKey)

    SettingsScaffold(
        modifier = modifier,
        listState = listState,
        topAppBarState = topAppBarState,
        topBarTitle = stringResource(R.string.about),
        content = { innerPadding, topBarScrollBehavior ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
                state = listState,
                contentPadding = innerPadding,
            ) {
                // App info header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .requiredSize(120.dp)
                                    .graphicsLayer {
                                        rotationZ = angle()
                                        scaleX = scale()
                                        scaleY = scale()
                                    }
                                    .clip(MaterialShapes.Cookie9Sided.toShape())
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            )

                            AnimatedAdbIcon(
                                modifier = Modifier.size(75.dp),
                                headColor = MaterialTheme.colorScheme.tertiary,
                                eyeColor = MaterialTheme.colorScheme.onTertiary
                            )
                        }

                        Image(
                            imageVector = DynamicColorImageVectors.appBranding(),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally),
                            contentScale = ContentScale.Fit,
                            contentDescription = null,
                        )

                        FlowRow(
                            itemVerticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                15.dp,
                                Alignment.CenterHorizontally
                            ),
                            verticalArrangement = Arrangement.spacedBy(15.dp),
                        ) {
                            AppHandlesChip(
                                icon = painterResource(R.drawable.ic_telegram),
                                title = stringResource(R.string.telegram),
                                description = stringResource(R.string.discussions),
                                onClick = { openUrl(UrlConst.URL_TELEGRAM_CHANNEL, context) })
                            AppHandlesChip(
                                icon = painterResource(R.drawable.ic_github),
                                title = stringResource(R.string.github),
                                description = stringResource(R.string.repository),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = { openUrl(UrlConst.URL_GITHUB_REPO, context) })
                            AppHandlesChip(
                                icon = painterResource(R.drawable.ic_version_tag),
                                title = BuildConfig.VERSION_NAME,
                                description = stringResource(R.string.current_version),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { openUrl(UrlConst.URL_GITHUB_RELEASES, context) })
                            AppHandlesChip(
                                icon = painterResource(R.drawable.ic_license),
                                title = stringResource(R.string.gpl_3_0),
                                description = stringResource(R.string.license),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = { openUrl(UrlConst.URL_GITHUB_REPO_LICENSE, context) })
                            AppHandlesChip(
                                icon = painterResource(R.drawable.ic_crowdin),
                                title = stringResource(R.string.crowdin),
                                description = stringResource(R.string.translations),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { openUrl(UrlConst.URL_CROWDIN_PROJECT, context) })
                        }
                    }
                }

                // Lead developer section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                SineWaveShape(
                                    amplitude = 10f,
                                    frequency = 5f,
                                    edge = WaveEdge.Both
                                )
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .animateItem(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.lead_developer),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 25.dp)
                                .align(Alignment.Start)
                                .animateItem()
                        )
                        ProfilePic(model = R.drawable.ic_launcher_foreground, size = 150.dp)
                        Text(
                            text = "Hridayan",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.des_hridayan),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic
                        )
                        SupportMeCard(
                            modifier = modifier.padding(
                                start = 15.dp,
                                end = 15.dp,
                                bottom = 25.dp
                            )
                        )
                    }
                }

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
}

@Composable
private fun AppHandlesChip(
    modifier: Modifier = Modifier,
    icon: Painter,
    title: String,
    description: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit = {},
) {
    CustomCard(
        modifier = modifier,
        shape = CustomCardShape(50),
        colors = CardDefaults.cardColors(containerColor, contentColor),
        onClick = withHaptic(HapticFeedbackType.VirtualKey) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painter = icon, contentDescription = null, tint = contentColor)
            Column {
                AutoResizeableText(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized
                )
                AutoResizeableText(
                    text = description,
                    style = MaterialTheme.typography.bodySmallEmphasized,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
