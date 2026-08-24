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
 *  along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.app

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.itsaky.androidide.eventbus.events.preferences.PreferenceChangeEvent
import com.itsaky.androidide.ui.themes.IDETheme
import com.itsaky.androidide.ui.themes.IThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Compose-first base activity for IDE screens.
 *
 * Compose content is hosted in a Material 3 [MaterialTheme] when [composeContent] is supplied.
 * Dark mode is derived from Compose's [isSystemInDarkTheme], so a configuration change caused by
 * the application night-mode preference automatically recomposes the screen instead of
 * maintaining a second activity-level dark-mode state. Existing View/Fragment screens can share
 * this lifecycle and migrate without a parallel activity hierarchy. The application remains
 * responsible for persisting the preferred night mode and app locale through the official AndroidX
 * [AppCompatDelegate] APIs.
 *
 * EventBus registration is limited to the `STARTED` lifecycle state. Work launched through
 * [launchWhileStarted] is cancelled while the activity is stopped and is recreated when it starts
 * again; use [lifecycleScope] for work that should instead live until the activity is destroyed.
 */
abstract class BaseIDEComposeActivity : BaseIDEActivity() {

  private companion object {
    const val SELECTED_THEME_PREFERENCE = "idpref_general_theme"
  }

  /**
   * Compose content for a Compose-first screen, or `null` for an existing View/Fragment screen.
   *
   * Keeping this optional allows activities to migrate incrementally without maintaining a second
   * activity hierarchy. New activities should override this instead of calling `setContent`.
   */
  protected open val composeContent: (@Composable () -> Unit)? = null

  private var selectedTheme by mutableStateOf(IDETheme.DEFAULT)

  private val eventBusObserver =
      object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
          selectedTheme = IThemeManager.getInstance().getCurrentTheme()
          if (subscribeToEvents && !EventBus.getDefault().isRegistered(this@BaseIDEComposeActivity)) {
            EventBus.getDefault().register(this@BaseIDEComposeActivity)
          }
        }

        override fun onStop(owner: LifecycleOwner) {
          if (EventBus.getDefault().isRegistered(this@BaseIDEComposeActivity)) {
            EventBus.getDefault().unregister(this@BaseIDEComposeActivity)
          }
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    selectedTheme = IThemeManager.getInstance().getCurrentTheme()
    lifecycle.addObserver(eventBusObserver)
    composeContent?.let { content ->
      setContent {
        IDEComposeTheme(theme = selectedTheme, synchronizeSystemBars = enableSystemBarTheming) {
          content()
        }
      }
    }
  }

  override fun onDestroy() {
    lifecycle.removeObserver(eventBusObserver)
    super.onDestroy()
  }

  /**
   * Starts [block] only while this activity is visible.
   *
   * The coroutine is cancelled at `STOPPED`, avoiding background collection and thread-pool work
   * while the screen is not needed, then starts again at the next `STARTED` transition.
   */
  protected fun launchWhileStarted(block: suspend CoroutineScope.() -> Unit): Job =
      lifecycleScope.launch { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block) }

  /** Updates the app-wide night-mode preference using AndroidX's supported global API. */
  @MainThread
  protected fun setApplicationNightMode(@AppCompatDelegate.NightMode mode: Int) {
    AppCompatDelegate.setDefaultNightMode(mode)
  }

  /** Updates the app-wide locale list using AndroidX's per-app language API. */
  @MainThread
  protected fun setApplicationLocales(locales: LocaleListCompat) {
    AppCompatDelegate.setApplicationLocales(locales)
  }

  /** Convenience overload for a BCP-47 language-tag list, for example `"zh-CN"`. */
  @MainThread
  protected fun setApplicationLocales(languageTags: String) {
    setApplicationLocales(LocaleListCompat.forLanguageTags(languageTags))
  }

  /** Keeps the Compose color scheme in sync when the IDE theme preference changes. */
  @Subscribe(threadMode = ThreadMode.MAIN)
  open fun onBasePreferenceChanged(event: PreferenceChangeEvent) {
    if (event.key == SELECTED_THEME_PREFERENCE) {
      selectedTheme = IThemeManager.getInstance().getCurrentTheme()
      if (composeContent == null) recreateActivitySafe()
    }
  }
}

/** Applies the IDE's Material 3 color scheme to a Compose subtree. */
@Composable
private fun BaseIDEComposeActivity.IDEComposeTheme(
    theme: IDETheme,
    synchronizeSystemBars: Boolean,
    content: @Composable () -> Unit,
) {
  // This is Compose's configuration-backed source of truth. It updates when the system setting or
  // AppCompatDelegate's application night mode changes, without an additional mutable UI-mode flag.
  val darkTheme = isSystemInDarkTheme()
  val colorScheme =
      when {
        theme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
        darkTheme -> theme.schemeDark ?: darkColorScheme()
        else -> theme.schemeLight ?: lightColorScheme()
      }

  if (synchronizeSystemBars) {
    SideEffect {
      window.statusBarColor = colorScheme.surface.toArgb()
      window.navigationBarColor = colorScheme.surface.toArgb()
      WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
      }
    }
  }

  MaterialTheme(colorScheme = colorScheme, content = content)
}
