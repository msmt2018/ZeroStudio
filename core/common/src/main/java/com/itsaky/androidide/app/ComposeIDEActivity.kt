package com.itsaky.androidide.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

/**
 * Base activity for screens that are implemented with Jetpack Compose.
 *
 * This keeps the shared IDE lifecycle from [BaseIDEActivity] (theme handling, EventBus
 * registration, system-bar theming and [activityScope]) while replacing XML inflation with a
 * single Compose content entry point. Editor screens should keep lifecycle/event code in their
 * activity and move UI structure into `ui/screen` composables.
 */
abstract class ComposeIDEActivity : BaseIDEActivity() {

  final override fun bindLayout(): ComposeView =
    error("ComposeIDEActivity uses setContent() instead of bindLayout().")

  @CallSuper
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreateWithoutBinding(savedInstanceState)
    setContent { Content() }
  }

  @Composable protected abstract fun Content()
}
