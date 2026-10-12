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

import android.os.Bundle
import androidx.compose.runtime.Composable
import com.itsaky.androidide.activities.preferences.PreferencesScreen
import com.itsaky.androidide.app.IDEActivity
import com.itsaky.androidide.preferences.IDEPreferences as prefs
import com.itsaky.androidide.preferences.addRootPreferences

/**
 * 纯 Compose 版 PreferencesActivity。
 *
 * @author android_zero
 */
class PreferencesActivity : IDEActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 初始化首选项树结构
    (prefs.children as? MutableList)?.clear()
    prefs.addRootPreferences()
  }

  @Composable
  override fun ComposeContent() {
    PreferencesScreen(
        rootChildren = prefs.children,
        onBack = { finish() },
    )
  }
}