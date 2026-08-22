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

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import javax.inject.Inject

/**
 * The single base activity for IDE screens.
 *
 * It combines Compose theme/lifecycle support from [BaseIDEComposeActivity] with AndroidX's
 * official edge-to-edge API. Insets are exposed to legacy View screens but never applied to the
 * decor view: each screen owns padding for the content that needs protection.
 */
abstract class IDEActivity : BaseIDEComposeActivity() {

  /** Application dependency supplied by Hilt instead of a cast from `application`. */
  @Inject lateinit var app: IDEApplication

  /** Last system-bar insets, retained for View-based screens during their Compose migration. */
  protected var systemBarInsets: Insets? = null
    private set

  override var enableSystemBarTheming: Boolean
    get() = false
    set(@Suppress("UNUSED_PARAMETER") value) = Unit

  override fun onCreate(savedInstanceState: Bundle?) {
    // AndroidX applies transparent system bars and contrast enforcement before View/Compose setup.
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    super.onCreate(savedInstanceState)
    installWindowInsetsListener()
  }

  private fun installWindowInsetsListener() {
    val decorView = window.decorView
    ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets ->
      onApplyWindowInsets(insets)
      insets
    }
    decorView.doOnAttach { view -> ViewCompat.requestApplyInsets(view) }
  }

  @CallSuper
  protected open fun onApplyWindowInsets(insets: WindowInsetsCompat) {
    systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    onApplySystemBarInsets(checkNotNull(systemBarInsets))
  }

  /** Called when system-bar insets change. Compose UI should use `WindowInsets` directly. */
  protected open fun onApplySystemBarInsets(insets: Insets) = Unit
}
