/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.activities

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.itsaky.androidide.activities.contributors.ContributorsScreen
import com.itsaky.androidide.app.IDEActivity
import com.itsaky.androidide.viewmodel.ContributorsViewModel

/**
 * 纯 Compose 版 ContributorsActivity。
 *
 * @author android_zero
 */
class ContributorsActivity : IDEActivity() {

  private val viewModel by viewModels<ContributorsViewModel>()

  @Composable
  override fun ComposeContent() {
    ContributorsScreen(
        viewModel = viewModel,
        onBack = { finish() },
    )
  }
}