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

package com.itsaky.androidide.editor.utils

import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.TextUtils
import io.github.rosemoe.sora.util.IntPair

/** Returns true if the given line is blank. */
fun Content.isBlankLine(line: Int): Boolean {
  return getLine(line).trim { it.isWhitespace() || it == '\r' }.isEmpty()
}

/** Returns true if the given line is not blank. */
fun Content.isNonBlankLine(line: Int): Boolean {
  return !isBlankLine(line)
}

/**
 * Returns the index of the previous non-blank line.
 *
 * @return The index of the previous non-blank line or -1 if not found.
 */
fun Content.previousNonBlankLine(line: Int): Int {
  for (i in line - 1 downTo 0) {
    if (isNonBlankLine(i)) {
      return i
    }
  }

  return -1
}

/**
 * Returns the index of the next non-blank line.
 *
 * @return The index of the next non-blank line or -1 if not found.
 */
fun Content.nextNonBlankLine(line: Int): Int {
  for (i in line + 1 until length) {
    if (isNonBlankLine(i)) {
      return i
    }
  }

  return -1
}

