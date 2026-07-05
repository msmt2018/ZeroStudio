package android.zero.studio.termux.ui.routes

sealed class MainActivityRoutes(val route: String) {
    data object Settings : MainActivityRoutes("settings")
    data object MainScreen : MainActivityRoutes("main")
}