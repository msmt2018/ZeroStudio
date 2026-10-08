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

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import com.itsaky.androidide.utils.EdgeToEdgeUtils
import com.itsaky.androidide.utils.getSystemBarInsets

/**
 * Compose-first, edge-to-edge base for IDE activities.
 *
 * This class merges the former `IDEActivity` and `EdgeToEdgeIDEActivity`. It is backed by
 * [BaseComposeIDEActivity], preserves the IDE application accessor and all inset callbacks, and
 * installs edge-to-edge before the Compose hierarchy is created. Existing XML activities remain
 * supported through [bindLayout] and are hosted with [AndroidView] while they migrate.
 */
abstract class IDEActivity : BaseComposeIDEActivity() {

  val app: IDEApplication
    get() = application as IDEApplication

  /** Whether edge-to-edge should be applied to the Activity window. */
  protected open var edgeToEdgeEnabled = true

  /** Apply decor padding for system bars while the device is in landscape orientation. */
  protected open var eteUpdateDecorViewPaddingInLandscape = true

  protected open val statusBarStyle = EdgeToEdgeUtils.DEFAULT_STATUS_BAR_STYLE!!
  protected open val navigationBarStyle = EdgeToEdgeUtils.DEFAULT_NAVIGATION_BAR_STYLE!!

  /** Original decor padding, restored whenever landscape padding is not in effect. */
  protected open var decorViewPadding: Rect? = null

  /** Latest system-bar insets, available to Compose and legacy subclasses. */
  protected open var systemBarInsets: Insets? = null

  override var enableSystemBarTheming: Boolean
    get() = false
    set(@Suppress("UNUSED_PARAMETER") value) {
      throw UnsupportedOperationException("Use edgeToEdgeEnabled and systemBarStyles instead")
    }

  /**
   * Transitional content mode. Override to `false` in a migrated screen and implement
   * [ComposeScreen]. Existing subclasses can continue overriding [bindLayout] without losing
   * their ViewBinding or fragment behavior.
   */
  protected open val useLegacyViewContent: Boolean = true

  @Composable
  final override fun ComposeContent() {
    if (useLegacyViewContent) {
      AndroidView(factory = { bindLayout() }, modifier = Modifier.fillMaxSize())
    } else {
      ComposeScreen()
    }
  }

  /** Compose root for migrated activities. */
  @Composable protected open fun ComposeScreen() = Unit

  /**
   * Legacy root factory. It is only read in [useLegacyViewContent] mode; Compose-only activities
   * should override [ComposeScreen] instead.
   */
  protected open fun bindLayout(): View =
      error("Override ComposeScreen() or bindLayout() in ${javaClass.name}")

  override fun onCreate(savedInstanceState: Bundle?) {
    if (edgeToEdgeEnabled) applyEdgeToEdge()
    super.onCreate(savedInstanceState)
  }

  @SuppressLint("WrongConstant")
  private fun applyEdgeToEdge() {
    window.apply {
      addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      @Suppress("DEPRECATION") clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    }
    EdgeToEdgeUtils.applyEdgeToEdge(this, statusBarStyle, navigationBarStyle)
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView, onApplyWindowInsetsListener)
    window.decorView.doOnAttach { onApplySystemBarInsets(getSystemBarInsets(it)) }
  }

  @SuppressLint("WrongConstant")
  private val onApplyWindowInsetsListener = OnApplyWindowInsetsListener { view, insets ->
    onApplyWindowInsets(insets)
    if (
        !eteUpdateDecorViewPaddingInLandscape ||
            view.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    ) {
      decorViewPadding?.let { padding ->
        view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
      }
      return@OnApplyWindowInsetsListener insets
    }

    if (decorViewPadding == null) {
      decorViewPadding =
          Rect(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bars = insets.getInsets(WindowInsets.Type.systemBars())
      view.setPadding(bars.left, 0, bars.right, bars.bottom)
    } else {
      @Suppress("DEPRECATION")
      view.setPadding(insets.stableInsetLeft, 0, insets.stableInsetRight, insets.stableInsetBottom)
    }
    insets
  }

  /** Called on every insets dispatch. Insets are deliberately not consumed. */
  @CallSuper
  protected open fun onApplyWindowInsets(insets: WindowInsetsCompat) {
    systemBarInsets = getSystemBarInsets(insets)
  }

  /** Called after the decor view attaches with the current system-bar insets. */
  protected open fun onApplySystemBarInsets(insets: Insets) = Unit
}
