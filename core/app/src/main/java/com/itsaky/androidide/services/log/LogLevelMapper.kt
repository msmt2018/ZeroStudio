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
package com.itsaky.androidide.services.log

import android.util.Log
import com.itsaky.androidide.utils.ILogger

/**
 * Maps a level value from the `ide-log-plugin` AAR (see
 * `com.zerostudio.logplugin.api.LogLevel`) to the IDE's internal
 * [ILogger.Level] enum.
 */
internal object LogLevelMapper {

  fun toAndroidLevel(pluginLevel: Int): ILogger.Level {
    return when (pluginLevel) {
      2 -> ILogger.Level.VERBOSE
      3 -> ILogger.Level.DEBUG
      4 -> ILogger.Level.INFO
      5 -> ILogger.Level.WARN
      6 -> ILogger.Level.ERROR
      7 -> ILogger.Level.ERROR
      // ANR / crash / native / perf are reported as ERROR so that the IDE
      // surfaces them prominently.
      100, 101, 102, 103, 104 -> ILogger.Level.ERROR
      else -> ILogger.Level.INFO
    }
  }

  fun toAndroidPriority(pluginLevel: Int): Int {
    return when (pluginLevel) {
      2 -> Log.VERBOSE
      3 -> Log.DEBUG
      4 -> Log.INFO
      5 -> Log.WARN
      6 -> Log.ERROR
      7 -> Log.ASSERT
      else -> Log.INFO
    }
  }
}
