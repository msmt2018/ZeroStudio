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

package com.itsaky.androidide.preferences.internal

/**
 * Preferences for the code editor.
 *
 * @author Akash Yadav
 * @author android_zero
 */
@Suppress("MemberVisibilityCanBePrivate")
object EditorPreferences {

  const val COMPLETIONS_MATCH_LOWER = "idepref_editor_completions_matchLower"

  const val FLAG_WS_LEADING = "idepref_editor_wsLeading"
  const val FLAG_WS_TRAILING = "idepref_editor_wsTrailing"
  const val FLAG_WS_INNER = "idepref_editor_wsInner"
  const val FLAG_WS_EMPTY_LINE = "idepref_editor_wsEmptyLine"
  const val FLAG_LINE_BREAK = "idepref_editor_lineBreak"
  const val FONT_SIZE = "idepref_editor_fontSize"
  const val PRINTABLE_CHARS = "idepref_editor_nonPrintableFlags"
  const val TAB_SIZE = "idepref_editor_tabSize"
  // REMOVED AUTO_SAVE from here as we are adding a more detailed configuration
  const val FONT_LIGATURES = "idepref_editor_fontLigatures"
  const val FLAG_PASSWORD = "idepref_editor_flagPassword"
  const val WORD_WRAP = "idepref_editor_word_wrap"
  const val USE_MAGNIFER = "idepref_editor_use_magnifier"
  const val USE_ICU = "idepref_editor_useIcu"
  const val USE_SOFT_TAB = "idepref_editor_useSoftTab"
  const val USE_CUSTOM_FONT = "idepref_editor_useCustomFont"
  const val DELETE_EMPTY_LINES = "idepref_editor_deleteEmptyLines"
  const val DELETE_TABS_ON_BACKSPACE = "idepref_editor_deleteTab"
  const val STICKY_SCROLL_ENABLED = "idepref_editor_stickyScrollEnabled"
  const val PIN_LINE_NUMBERS = "idepref_editor_pinLineNumbers"

  const val COLOR_SCHEME = "idepref_editor_colorScheme"
  const val DEFAULT_COLOR_SCHEME = "default"

  // 光标与选择
  const val SMOOTH_CURSOR_MOVEMENT = "idepref_editor_smoothCursorMovement"
  const val CURSOR_IME_VISIBLE_SCROLL = "idepref_editor_cursorImeVisibleScroll"
  const val CURSOR_VISIBLE_AREA_SCROLL = "idepref_editor_cursorVisibleAreaScroll"
  const val CURSOR_IME_SCROLL_POSITION = "idepref_editor_cursorImeScrollPosition"
  const val CURSOR_VISIBLE_AREA_SCROLL_POSITION = "idepref_editor_cursorVisibleAreaScrollPosition"
  const val CURSOR_SCROLL_POSITION_TOP = "top"
  const val CURSOR_SCROLL_POSITION_CENTER = "center"
  const val CURSOR_SCROLL_POSITION_BOTTOM = "bottom"

  const val CURSOR_STYLE = "idepref_editor_cursorStyle"
  const val CLEAR_LOGCAT_BEFORE_RUN = "idepref_run_clearLogcatBeforeRun"

  // Keys for Auto-Save feature
  const val AUTO_COMPLETE_ON_TYPE = "idepref_editor_autoCompleteOnType"
  const val AUTO_SAVE_ENABLED = "idepref_editor_autoSaveEnabled"
  const val AUTO_SAVE_DELAY_VALUE = "idepref_editor_autoSaveDelayValue"
  const val AUTO_SAVE_DELAY_UNIT = "idepref_editor_autoSaveDelayUnit"
  const val AUTO_SAVE_ON_FOCUS_LOSS = "idepref_editor_autoSaveOnFocusLoss"
  const val AUTO_SAVE_BEFORE_BUILD = "idepref_editor_autoSaveBeforeBuild"

  var completionsMatchLower: Boolean
    get() = prefManager.getBoolean(COMPLETIONS_MATCH_LOWER, false)
    set(value) {
      prefManager.putBoolean(COMPLETIONS_MATCH_LOWER, value)
    }

  var drawLeadingWs: Boolean
    get() = prefManager.getBoolean(FLAG_WS_LEADING, false)
    set(value) {
      prefManager.putBoolean(FLAG_WS_LEADING, value)
    }

  var drawTrailingWs: Boolean
    get() = prefManager.getBoolean(FLAG_WS_TRAILING, false)
    set(value) {
      prefManager.putBoolean(FLAG_WS_TRAILING, value)
    }

  var drawInnerWs: Boolean
    get() = prefManager.getBoolean(FLAG_WS_INNER, false)
    set(value) {
      prefManager.putBoolean(FLAG_WS_INNER, value)
    }

  var drawEmptyLineWs: Boolean
    get() = prefManager.getBoolean(FLAG_WS_EMPTY_LINE, false)
    set(value) {
      prefManager.putBoolean(FLAG_WS_EMPTY_LINE, value)
    }

  var drawLineBreak: Boolean
    get() = prefManager.getBoolean(FLAG_LINE_BREAK, false)
    set(value) {
      prefManager.putBoolean(FLAG_LINE_BREAK, value)
    }

  var fontSize: Float
    get() = prefManager.getFloat(FONT_SIZE, 14f)
    set(value) {
      prefManager.putFloat(FONT_SIZE, value)
    }

  var tabSize: Int
    get() = prefManager.getInt(TAB_SIZE, 4)
    set(value) {
      prefManager.putInt(TAB_SIZE, value)
    }

  var fontLigatures: Boolean
    get() = prefManager.getBoolean(FONT_LIGATURES, true)
    set(value) {
      prefManager.putBoolean(FONT_LIGATURES, value)
    }

  var visiblePasswordFlag: Boolean
    get() = prefManager.getBoolean(FLAG_PASSWORD, true)
    set(value) {
      prefManager.putBoolean(FLAG_PASSWORD, value)
    }

  var wordwrap: Boolean
    get() = prefManager.getBoolean(WORD_WRAP, false)
    set(value) {
      prefManager.putBoolean(WORD_WRAP, value)
    }

  var useMagnifier: Boolean
    get() = prefManager.getBoolean(USE_MAGNIFER, true)
    set(value) {
      prefManager.putBoolean(USE_MAGNIFER, value)
    }

  var useIcu: Boolean
    get() = prefManager.getBoolean(USE_ICU, false)
    set(value) {
      prefManager.putBoolean(USE_ICU, value)
    }

  var useSoftTab: Boolean
    get() = prefManager.getBoolean(USE_SOFT_TAB, true)
    set(value) {
      prefManager.putBoolean(USE_SOFT_TAB, value)
    }

  var useCustomFont: Boolean
    get() = prefManager.getBoolean(USE_CUSTOM_FONT, false)
    set(value) {
      prefManager.putBoolean(USE_CUSTOM_FONT, value)
    }

  var colorScheme: String
    get() = prefManager.getString(COLOR_SCHEME, DEFAULT_COLOR_SCHEME)
    set(value) {
      prefManager.putString(COLOR_SCHEME, value)
    }

  var deleteEmptyLines: Boolean
    get() = prefManager.getBoolean(DELETE_EMPTY_LINES, true)
    set(value) {
      prefManager.putBoolean(DELETE_EMPTY_LINES, value)
    }

  var deleteTabsOnBackspace: Boolean
    get() = prefManager.getBoolean(DELETE_TABS_ON_BACKSPACE, true)
    set(value) {
      prefManager.putBoolean(DELETE_TABS_ON_BACKSPACE, value)
    }

  var stickyScrollEnabled: Boolean
    get() = prefManager.getBoolean(STICKY_SCROLL_ENABLED, false)
    set(value) {
      prefManager.putBoolean(STICKY_SCROLL_ENABLED, value)
    }

  var pinLineNumbers: Boolean
    get() = prefManager.getBoolean(PIN_LINE_NUMBERS, true)
    set(value) = prefManager.putBoolean(PIN_LINE_NUMBERS, value)

  var smoothCursorMovement: Boolean
    get() = prefManager.getBoolean(SMOOTH_CURSOR_MOVEMENT, false)
    set(value) {
      prefManager.putBoolean(SMOOTH_CURSOR_MOVEMENT, value)
    }

  var cursorImeVisibleScroll: Boolean
    get() = prefManager.getBoolean(CURSOR_IME_VISIBLE_SCROLL, true)
    set(value) {
      prefManager.putBoolean(CURSOR_IME_VISIBLE_SCROLL, value)
    }

  var cursorVisibleAreaScroll: Boolean
    get() = prefManager.getBoolean(CURSOR_VISIBLE_AREA_SCROLL, false)
    set(value) {
      prefManager.putBoolean(CURSOR_VISIBLE_AREA_SCROLL, value)
    }

  var cursorImeScrollPosition: String
    get() = prefManager.getString(CURSOR_IME_SCROLL_POSITION, CURSOR_SCROLL_POSITION_BOTTOM)
    set(value) {
      prefManager.putString(CURSOR_IME_SCROLL_POSITION, value)
    }

  var cursorVisibleAreaScrollPosition: String
    get() =
        prefManager.getString(CURSOR_VISIBLE_AREA_SCROLL_POSITION, CURSOR_SCROLL_POSITION_CENTER)
    set(value) {
      prefManager.putString(CURSOR_VISIBLE_AREA_SCROLL_POSITION, value)
    }

  // Properties for Auto-Save feature
  var autoSaveEnabled: Boolean
    get() = prefManager.getBoolean(AUTO_SAVE_ENABLED, true) // Default to open Auto-Save feature
    set(value) {
      prefManager.putBoolean(AUTO_SAVE_ENABLED, value)
    }

  var autoSaveDelayValue: Long
    get() = prefManager.getLong(AUTO_SAVE_DELAY_VALUE, 5) // Default to 5
    set(value) {
      prefManager.putLong(AUTO_SAVE_DELAY_VALUE, value)
    }

  var autoSaveDelayUnit: String
    get() = prefManager.getString(AUTO_SAVE_DELAY_UNIT, "秒") // Default to "Seconds"
    set(value) {
      prefManager.putString(AUTO_SAVE_DELAY_UNIT, value)
    }

  var autoSaveOnFocusLoss: Boolean
    get() = prefManager.getBoolean(AUTO_SAVE_ON_FOCUS_LOSS, false) // Default to false
    set(value) {
      prefManager.putBoolean(AUTO_SAVE_ON_FOCUS_LOSS, value)
    }

  var autoSaveBeforeBuild: Boolean
    get() = prefManager.getBoolean(AUTO_SAVE_BEFORE_BUILD, true) // Default to true
    set(value) {
      prefManager.putBoolean(AUTO_SAVE_BEFORE_BUILD, value)
    }

  var cursorStyle: String
    get() = prefManager.getString(CURSOR_STYLE, "Block") // Default to Block
    set(value) {
      prefManager.putString(CURSOR_STYLE, value)
    }

  var autoCompleteOnType: Boolean
    get() = prefManager.getBoolean(AUTO_COMPLETE_ON_TYPE, true) // Default to true
    set(value) {
      prefManager.putBoolean(AUTO_COMPLETE_ON_TYPE, value)
    }
}
