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

package com.itsaky.androidide.treesitter.tags;

/**
 * 标签提取过程中可能发生的错误。
 *
 * <p>对应 tree-sitter 0.27 {@code TSTagsError} 枚举。
 */
public enum TagsError {
  /** 操作成功。 */
  OK,
  /** 未知的 scope 名称。 */
  UNKNOWN_SCOPE,
  /** 操作超时（被取消）。 */
  TIMEOUT,
  /** 无效的语言。 */
  INVALID_LANGUAGE,
  /** 无效的 UTF-8 输入。 */
  INVALID_UTF8,
  /** 无效的正则表达式。 */
  INVALID_REGEX,
  /** 无效的 query。 */
  INVALID_QUERY,
  /** 无效的 capture 名称。 */
  INVALID_CAPTURE;

  /**
   * 将 C API 的整数值转换为枚举常量。
   *
   * @param value C API 的整数值。
   * @return 对应的枚举常量，如果值越界则返回 {@code null}。
   */
  public static TagsError fromValue(int value) {
    TagsError[] values = values();
    if (value < 0 || value >= values.length) {
      return null;
    }
    return values[value];
  }
}
