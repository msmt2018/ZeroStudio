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
package com.itsaky.androidide.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.R.attr
import com.itsaky.androidide.eventbus.events.preferences.PreferenceChangeEvent
import com.itsaky.androidide.tasks.cancelIfActive
import com.itsaky.androidide.ui.themes.IThemeManager
import com.itsaky.androidide.utils.resolveAttr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Compose counterpart of [BaseIDEActivity].
 *
 * [FragmentActivity] is a Jetpack [androidx.activity.ComponentActivity], so it provides the
 * standard Compose `setContent` host while retaining `supportFragmentManager` for activities that
 * are migrated incrementally. Apart from replacing `bindLayout()` with [ComposeContent], the
 * theme, system-bar, EventBus, coroutine, preference, and fragment contracts intentionally match
 * [BaseIDEActivity].
 */
abstract class BaseComposeIDEActivity : FragmentActivity() {

  companion object {
    private const val KEY_UI_MODE = "idepref_general_uiMode"
    private const val KEY_SELECTED_THEME = "idpref_general_theme"
  }

  /** Defaults to true so base preference events are always observed. */
  open val subscribeToEvents: Boolean = true

  /** Set false for activities that manage system bars with edge-to-edge APIs themselves. */
  open var enableSystemBarTheming: Boolean = true

  open val navigationBarColor: Int
    get() = resolveAttr(attr.colorSurface)

  open val statusBarColor: Int
    get() = resolveAttr(attr.colorSurface)

  /** Scope for non-UI background work; it is cancelled with the Activity. */
  val activityScope = CoroutineScope(Dispatchers.Default)

  override fun onCreate(savedInstanceState: Bundle?) {
    // Apply before super so Compose, AndroidView, and any incrementally hosted legacy View all
    // receive the same theme resources as BaseIDEActivity layouts.
    IThemeManager.getInstance().applyTheme(this)

    if (enableSystemBarTheming) {
      window.apply {
        navigationBarColor = this@BaseComposeIDEActivity.navigationBarColor
        statusBarColor = this@BaseComposeIDEActivity.statusBarColor
      }
      applySystemBarIconAppearance()
    }

    // Preserve BaseIDEActivity's second application of the current theme. Theme implementations
    // use this pass to update configuration-dependent resources before content is installed.
    IThemeManager.getInstance().applyTheme(this)
    super.onCreate(savedInstanceState)
    preSetContentLayout()
    setContent { ComposeContent() }
  }

  override fun onResume() {
    super.onResume()
    if (enableSystemBarTheming) applySystemBarIconAppearance()
  }

  override fun onDestroy() {
    super.onDestroy()
    activityScope.cancelIfActive("Activity is being destroyed")
  }

  override fun onStart() {
    super.onStart()
    if (!EventBus.getDefault().isRegistered(this) && subscribeToEvents) {
      EventBus.getDefault().register(this)
    }
  }

  override fun onStop() {
    super.onStop()
    if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
  }

  /** Global preference listener with the same recreation behavior as [BaseIDEActivity]. */
  @Subscribe(threadMode = ThreadMode.MAIN)
  open fun onBasePreferenceChanged(event: PreferenceChangeEvent) {
    when (event.key) {
      KEY_UI_MODE -> Unit // IDEApplication updates this centrally through AppCompatDelegate.
      KEY_SELECTED_THEME -> recreateActivitySafe()
    }
  }

  /** Makes the Activity's fragment API available during gradual XML-to-Compose migration. */
  fun loadFragment(fragment: Fragment, id: Int) {
    supportFragmentManager.beginTransaction().replace(id, fragment).commit()
  }

  /** Recreate after a theme change so Compose and Android resources are both re-resolved. */
  protected fun recreateActivitySafe() {
    recreate()
  }

  /** Hook invoked before [ComposeContent], matching BaseIDEActivity's layout hook. */
  protected open fun preSetContentLayout() = Unit

  /** The Activity's Compose root. This replaces BaseIDEActivity.bindLayout(). */
  @Composable protected abstract fun ComposeContent()

  private fun applySystemBarIconAppearance() {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    val isNightMode =
        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    controller.isAppearanceLightStatusBars = !isNightMode
    controller.isAppearanceLightNavigationBars = !isNightMode
  }
}
