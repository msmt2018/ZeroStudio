@file:OptIn(ExperimentalSharedTransitionApi::class)

package android.zero.studio.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import android.zero.studio.commandexamples.presentation.screens.CommandExamplesScreen
import android.zero.studio.core.common.LocalAnimatedContentScope
import android.zero.studio.core.domain.model.SharedTextHolder
import android.zero.studio.home.presentation.screens.HomeScreen
import android.zero.studio.onboarding.presentation.screens.OnboardingScreen
import android.zero.studio.qstiles.presentation.screen.CreateTileScreen
import android.zero.studio.qstiles.presentation.screen.TileDashBoardScreen
import android.zero.studio.settings.presentation.page.about.screens.AboutScreen
import android.zero.studio.settings.presentation.page.aimodels.screens.AiModelsScreen
import android.zero.studio.settings.presentation.page.aimodels.screens.ModelsScreen
import android.zero.studio.settings.presentation.page.autoupdate.screens.AutoUpdateScreen
import android.zero.studio.settings.presentation.page.backup.screens.BackupAndRestoreScreen
import android.zero.studio.settings.presentation.page.backup.screens.BackupSchedulerScreen
import android.zero.studio.settings.presentation.page.behavior.screens.BehaviorScreen
import android.zero.studio.settings.presentation.page.changelog.screens.ChangelogScreen
import android.zero.studio.settings.presentation.page.contributors.screens.ContributorsScreen
import android.zero.studio.settings.presentation.page.contributors.screens.TranslatorsScreen
import android.zero.studio.settings.presentation.page.crashhistory.screens.CrashDetailsScreen
import android.zero.studio.settings.presentation.page.crashhistory.screens.CrashHistoryScreen
import android.zero.studio.settings.presentation.page.languages.screens.LanguagesScreen
import android.zero.studio.settings.presentation.page.licenses.screens.LicensesScreen
import android.zero.studio.settings.presentation.page.lookandfeel.screens.DarkThemeScreen
import android.zero.studio.settings.presentation.page.lookandfeel.screens.LookAndFeelScreen
import android.zero.studio.settings.presentation.page.lookandfeel.screens.UiScaleScreen
import android.zero.studio.settings.presentation.page.mainscreen.screen.SettingsScreen
import android.zero.studio.settings.presentation.page.search.screens.SettingsSearchScreen
import android.zero.studio.shell.file_browser.presentation.screens.FileBrowserScreen
import android.zero.studio.shell.local_adb_shell.presentation.screens.LocalAdbScreen
import android.zero.studio.shell.fastboot.presentation.screens.FastbootScreen
import android.zero.studio.shell.otg_adb_shell.presentation.screens.OtgAdbScreen
import android.zero.studio.shell.wifi_adb_shell.presentation.screens.PairingOtherDeviceScreen
import android.zero.studio.shell.wifi_adb_shell.presentation.screens.PairingOwnDeviceScreen
import android.zero.studio.shell.wifi_adb_shell.presentation.screens.WifiAdbScreen
import kotlin.reflect.KType

@Composable
fun Navigation(isFirstLaunch: Boolean = false) {
    val navController = rememberNavController()

    CompositionLocalProvider(
        LocalNavController provides navController,
    ) {
        LaunchedEffect(Unit) {
            SharedTextHolder.text?.let {
                navController.navigate(NavRoutes.LocalAdbScreen)
            }
        }

        NavHost(
            navController = navController,
            startDestination = if (isFirstLaunch) NavRoutes.OnboardingScreen else NavRoutes.HomeScreen,
            enterTransition = { slideFadeInFromRight() },
            exitTransition = { slideFadeOutToLeft() },
            popEnterTransition = { slideFadeInFromLeft() },
            popExitTransition = { slideFadeOutToRight() }
        ) {
            composable<NavRoutes.OnboardingScreen> {
                OnboardingScreen()
            }

            composable<NavRoutes.HomeScreen> {
                HomeScreen()
            }

            composable<NavRoutes.SettingsScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.SettingsScreen>()
                SettingsScreen(highlightKey = route.highlightKey)
            }

            composable<NavRoutes.LookAndFeelScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.LookAndFeelScreen>()
                LookAndFeelScreen(highlightKey = route.highlightKey)
            }

            composable<NavRoutes.DarkThemeScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.DarkThemeScreen>()
                DarkThemeScreen(highlightKey = route.highlightKey)
            }

            composable<NavRoutes.UiScaleScreen> {
                UiScaleScreen()
            }

            composable<NavRoutes.BehaviorScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.BehaviorScreen>()
                BehaviorScreen(highlightKey = route.highlightKey)
            }

            composable<NavRoutes.AboutScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.AboutScreen>()
                AboutScreen(highlightKey = route.highlightKey)
            }

            composable<NavRoutes.CommandExamplesScreen> {
                CommandExamplesScreen()
            }

            composable<NavRoutes.TranslatorsScreen> {
                TranslatorsScreen()
            }

            composable<NavRoutes.ContributorsScreen> {
                ContributorsScreen()
            }

            composable<NavRoutes.ChangelogScreen> {
                ChangelogScreen()
            }

            composable<NavRoutes.SettingsSearchScreen> {
                SettingsSearchScreen()
            }

            composable<NavRoutes.LanguagesScreen> {
                LanguagesScreen()
            }

            composable<NavRoutes.LicensesScreen> {
                LicensesScreen()
            }

            animatedComposable<NavRoutes.CrashHistoryScreen> {
                CrashHistoryScreen()
            }

            animatedComposable<NavRoutes.CrashDetailsScreen> {
                CrashDetailsScreen()
            }

            composable<NavRoutes.AutoUpdateScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.AutoUpdateScreen>()
                AutoUpdateScreen(highlightKey = route.highlightKey)
            }

            composable<NavRoutes.BackupAndRestoreScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.BackupAndRestoreScreen>()
                BackupAndRestoreScreen(highlightKey = route.highlightKey)
            }

            composable<NavRoutes.BackupSchedulerScreen> {
                BackupSchedulerScreen()
            }

            composable<NavRoutes.LocalAdbScreen> {
                LocalAdbScreen()
            }

            composable<NavRoutes.OtgAdbScreen> {
                OtgAdbScreen()
            }

            composable<NavRoutes.FastbootScreen> {
                FastbootScreen()
            }

            composable<NavRoutes.PairingOwnDeviceScreen> {
                PairingOwnDeviceScreen()
            }

            composable<NavRoutes.PairingOtherDeviceScreen> {
                PairingOtherDeviceScreen()
            }

            composable<NavRoutes.WifiAdbScreen> {
                WifiAdbScreen()
            }

            composable<NavRoutes.FileBrowserScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.FileBrowserScreen>()
                FileBrowserScreen(
                    deviceAddress = route.deviceAddress,
                    connectionMode = route.connectionMode,
                    isOwnDevice = route.isOwnDevice
                )
            }

            composable<NavRoutes.TileDashboardScreen> {
                TileDashBoardScreen()
            }

            composable<NavRoutes.CreateTileScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.CreateTileScreen>()
                CreateTileScreen(tileId = route.tileId)
            }

            composable<NavRoutes.AiModelManagerScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<NavRoutes.AiModelManagerScreen>()
                AiModelsScreen(highlightKey = route.highlightKey)
            }

            composable<NavRoutes.ModelsScreen> {
                ModelsScreen()
            }

        }
    }
}

inline fun <reified T : Any> NavGraphBuilder.animatedComposable(
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards EnterTransition?)? = null,
    noinline exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards ExitTransition?)? = null,
    noinline popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards EnterTransition?)? = enterTransition,
    noinline popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards ExitTransition?)? = exitTransition,
    noinline sizeTransform: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards SizeTransform?)? = null,
    noinline content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit)
) {
    composable<T>(
        typeMap = typeMap,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        sizeTransform = sizeTransform
    ) { backStackEntry ->
        val animatedContentScope = this

        CompositionLocalProvider(
            LocalAnimatedContentScope provides animatedContentScope
        ) {
            content(backStackEntry)
        }
    }
}