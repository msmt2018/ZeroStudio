package android.zero.studio.termux.ui.navHosts


import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import android.zero.studio.termux.settings.Settings
import androidx.activity.ComponentActivity
import android.zero.studio.termux.ui.animations.NavigationAnimationTransitions
import android.zero.studio.termux.ui.routes.MainActivityRoutes
import android.zero.studio.termux.ui.screens.downloader.Downloader
import android.zero.studio.termux.ui.screens.settings.Settings
import android.zero.studio.termux.ui.screens.terminal.Rootfs
import android.zero.studio.termux.ui.screens.terminal.TerminalScreen

var showStatusBar = mutableStateOf(Settings.statusBar)
var horizontal_statusBar = mutableStateOf(Settings.horizontal_statusBar)

fun showStatusBar(show: Boolean,window: Window){
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q){
        if (show){
            window.decorView.windowInsetsController!!.show(
                android.view.WindowInsets.Type.statusBars()
            )
        }else{
            window.decorView.windowInsetsController!!.hide(
                android.view.WindowInsets.Type.statusBars()
            )
        }
    }else{
        if (show){
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }else{
            WindowInsetsControllerCompat(window,window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}


@Composable
fun UpdateStatusBar(mainActivityActivity: ComponentActivity,show: Boolean = true){
    LaunchedEffect(show) {
        showStatusBar(show = show, window = mainActivityActivity.window)
    }
}

@Composable
fun MainActivityNavHost(modifier: Modifier = Modifier,navController: NavHostController,mainActivity: ComponentActivity) {
    NavHost(
        navController = navController,
        startDestination = MainActivityRoutes.MainScreen.route,
        enterTransition = { NavigationAnimationTransitions.enterTransition },
        exitTransition = { NavigationAnimationTransitions.exitTransition },
        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
    ) {

        composable(MainActivityRoutes.MainScreen.route) {
            if (Rootfs.isFilesDownloaded()){
                val config = LocalConfiguration.current
                if (Configuration.ORIENTATION_LANDSCAPE == config.orientation){
                    UpdateStatusBar(mainActivity, show = horizontal_statusBar.value)
                }else{
                    UpdateStatusBar(mainActivity, show = showStatusBar.value)
                }

                TerminalScreen(mainActivityActivity = mainActivity, navController = navController)
            }else{
                Downloader(mainActivity = mainActivity, navController = navController)
            }
        }
        composable(MainActivityRoutes.Settings.route) {
            UpdateStatusBar(mainActivity,show = true)
            Settings(navController = navController, mainActivity = mainActivity)
        }
    }
}
