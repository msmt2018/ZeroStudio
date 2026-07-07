/*
 *  This file is part of android-tree-sitter.
 *
 *  android-tree-sitter library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  android-tree-sitter library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *  along with android-tree-sitter.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.treesitter;

/**
 * The type of a log message.
 *
 * <p>This is the Java binding of tree-sitter 0.27 {@code TSLogType}.
 *
 * <ul>
 *   <li>{@link #PARSE} — corresponds to {@code TSLogTypeParse} (value 0).</li>
 *   <li>{@link #LEX} — corresponds to {@code TSLogTypeLex} (value 1).</li>
 * </ul>
 */
public enum TSLogType {
  /** Parsing log. Maps to {@code TSLogTypeParse}. */
  PARSE,
  /** Lexing log. Maps to {@code TSLogTypeLex}. */
  LEX;

  /**
   * Get the ordinal value used by the C API.
   *
   * @return 0 for {@link #PARSE}, 1 for {@link #LEX}.
   */
  public int getValue() {
    return ordinal();
  }

  /**
   * Convert a C API integer value back to the enum constant.
   *
   * @param value the C API integer value (0 or 1).
   * @return the corresponding enum constant, or {@code null} if {@code value} is out of range.
   */
  public static TSLogType fromValue(int value) {
    TSLogType[] values = values();
    if (value < 0 || value >= values.length) {
      return null;
    }
    return values[value];
  }
}
