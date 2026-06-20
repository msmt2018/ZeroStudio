package me.rerere.rikkahub

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import me.rerere.rikkahub.ui.activity.SafeModeActivity
import me.rerere.rikkahub.utils.CrashHandler

/**
 * Pure fragment host for the main chat experience. Anything that can live inside
 * [RouteFragment] lives there — this activity only contains the bits that are
 * impossible (or meaningless) to express from a Fragment:
 *  - the window-level lifecycle callbacks ([dispatchKeyEvent], [onCreate], [onNewIntent])
 *  - the [enableEdgeToEdge] / nav-bar contrast setup that targets the Activity window
 *  - the [CrashHandler] redirect that decides whether the activity even opens
 */
class RouteActivity : FragmentActivity() {

    // Volume key listener registry — last registered handler wins. Kept on the
    // activity because it is consumed by [dispatchKeyEvent], which fragments
    // cannot override.
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }

        val container = FrameLayout(this).apply { id = android.R.id.content }
        setContentView(container)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(android.R.id.content, RouteFragment())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (supportFragmentManager.findFragmentById(android.R.id.content) as? RouteFragment)
            ?.onNewIntent(intent)
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}
