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

// import com.itsaky.androidide.common.R
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
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

abstract class BaseIDEActivity : AppCompatActivity() {

  companion object {
    private const val KEY_UI_MODE = "idepref_general_uiMode"
    private const val KEY_SELECTED_THEME = "idpref_general_theme"
  }

  // Default to true to ensure we catch theme events.
  // Subclasses can override to register custom events, but Base always listens.
  open val subscribeToEvents: Boolean = true

  open var enableSystemBarTheming: Boolean = true

  open val navigationBarColor: Int
    get() = resolveAttr(attr.colorSurface)

  open val statusBarColor: Int
    get() = resolveAttr(attr.colorSurface)

  /** [CoroutineScope] for executing tasks with the [Default][Dispatchers.Default] dispatcher. */
  val activityScope = CoroutineScope(Dispatchers.Default)

  override fun onCreate(savedInstanceState: Bundle?) {
    onCreateWithoutBinding(savedInstanceState)
    preSetContentLayout()
    setContentView(bindLayout())
  }

  /**
   * Runs the shared IDE activity lifecycle setup without inflating a View hierarchy.
   *
   * Compose-based activities call this from their own [onCreate] and then install content with
   * `setContent { ... }`, avoiding a redundant XML root while preserving theme and system-bar
   * initialization.
   */
  protected fun onCreateWithoutBinding(savedInstanceState: Bundle?) {
    // Apply Theme BEFORE super.onCreate to ensure layout inflation uses correct styles
    IThemeManager.getInstance().applyTheme(this)

    // System Bar Theming (for non-EdgeToEdge activities)
    if (enableSystemBarTheming) {
      window?.apply {
        navigationBarColor = this@BaseIDEActivity.navigationBarColor
        statusBarColor = this@BaseIDEActivity.statusBarColor
      }
      applySystemBarIconAppearance()
    }
    IThemeManager.getInstance().applyTheme(this)
    super.onCreate(savedInstanceState)
  }

  override fun onResume() {
    super.onResume()
    if (enableSystemBarTheming) {
      applySystemBarIconAppearance()
    }
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
    if (EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().unregister(this)
    }
  }

  /** Global Preference Listener for ALL Activities. */
  @Subscribe(threadMode = ThreadMode.MAIN)
  open fun onBasePreferenceChanged(event: PreferenceChangeEvent) {
    when (event.key) {
      KEY_UI_MODE -> {
        // UI mode changes are handled centrally in IDEApplication via AppCompatDelegate.
        // Avoid triggering a second recreation here because it can race with popup attach/update
        // and cause "PopupDecorView not attached to window manager" on some devices.
      }
      KEY_SELECTED_THEME -> {
        recreateActivitySafe()
      }
    }
  }

  private fun applySystemBarIconAppearance() {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    val isNightMode =
        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    controller.isAppearanceLightStatusBars = !isNightMode
    controller.isAppearanceLightNavigationBars = !isNightMode
  }

  /** Recreates the activity with state preservation. */
  protected fun recreateActivitySafe() {
    // Calling recreate() forces the Activity to destroy and create again with new Resources.
    // This is necessary to load values-night or new style attributes.
    // Android automatically restores savedInstanceState (Edit text content, scroll position, etc.)
    recreate()
  }

  fun loadFragment(fragment: Fragment, id: Int) {
    val transaction = supportFragmentManager.beginTransaction()
    transaction.replace(id, fragment)
    transaction.commit()
  }

  protected open fun preSetContentLayout() {}

  protected abstract fun bindLayout(): View
}
