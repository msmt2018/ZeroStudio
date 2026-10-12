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

package com.itsaky.androidide.activities

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.itsaky.androidide.app.IDEActivity
import com.itsaky.androidide.repository.sdkmanager.ui.SdkManagerScreen
import com.itsaky.androidide.repository.sdkmanager.viewmodel.SdkManagerViewModel

/**
 * 纯 Compose 版 SdkManagerActivity。
 * 彻底消除 bindLayout、supportFragmentManager 以及中间中转的 SdkHostFragment。
 *
 * @author android_zero
 */
class SdkManagerActivity : IDEActivity() {

  private val sdkManagerViewModel by viewModels<SdkManagerViewModel>()

  @Composable
  override fun ComposeContent() {
    SdkManagerScreen(
        onClose = { finish() },
        viewModel = sdkManagerViewModel,
    )
  }
}