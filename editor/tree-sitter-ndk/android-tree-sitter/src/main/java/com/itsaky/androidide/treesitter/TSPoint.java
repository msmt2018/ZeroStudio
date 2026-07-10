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

import com.itsaky.androidide.treesitter.util.TSObjectFactoryProvider;
import java.util.Objects;

/**
 * TSPoint
 */
public class TSPoint {

  protected int row, column;

  protected TSPoint() {
  }

  protected TSPoint(int row, int column) {
    this.row = row;
    this.column = column;
  }

  public int getRow() {
    return row;
  }

  public int getColumn() {
    return column;
  }

  public static TSPoint create(int row, int column) {
    return TSObjectFactoryProvider.getFactory().createPoint(row, column);
  }

  /**
   * 根据编辑操作更新此 point，使其与编辑后的源代码保持同步。
   *
   * <p>这是 tree-sitter 0.27 {@code ts_point_edit} 的包装。此方法会原地修改此 point，
   * 并返回更新后的字节偏移量。
   *
   * @param currentByte 此 point 当前对应的字节偏移量。
   * @param edit 编辑操作。
   * @return 更新后的字节偏移量。
   */
  public int edit(int currentByte, TSInputEdit edit) {
    return TSUtils.pointEdit(this, currentByte, edit);
  }

  @Override
  public String toString() {
    return "TSPoint(Row: " + this.row + ", Column: " + this.column + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TSPoint)) {
      return false;
    }
    TSPoint tsPoint = (TSPoint) o;
    return row == tsPoint.row && column == tsPoint.column;
  }

  @Override
  public int hashCode() {
    return Objects.hash(row, column);
  }
}