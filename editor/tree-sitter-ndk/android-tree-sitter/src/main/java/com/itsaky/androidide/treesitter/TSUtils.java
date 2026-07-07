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

import com.itsaky.androidide.treesitter.annotations.GenerateNativeHeaders;
import dalvik.annotation.optimization.FastNative;
import java.util.Objects;

/**
 * tree-sitter 工具函数集合。
 *
 * <p>包含 tree-sitter 0.27 的全局工具函数的 Java 绑定：
 * <ul>
 *   <li>{@link #pointEdit(TSPoint, int, TSInputEdit)} — 编辑后更新 point</li>
 *   <li>{@link #rangeEdit(TSRange, TSInputEdit)} — 编辑后更新 range</li>
 * </ul>
 *
 * @author Akash Yadav
 */
public class TSUtils {

  private TSUtils() {
    // 工具类，不可实例化
  }

  /**
   * 根据编辑操作更新 point，使其与编辑后的源代码保持同步。
   *
   * <p>这是 tree-sitter 0.27 {@code ts_point_edit} 的包装。此方法会原地修改传入的 point，
   * 并返回更新后的字节偏移量。
   *
   * @param point        要更新的 point。
   * @param currentByte  此 point 当前对应的字节偏移量。
   * @param edit         编辑操作。
   * @return 更新后的字节偏移量。
   */
  public static int pointEdit(TSPoint point, int currentByte, TSInputEdit edit) {
    Objects.requireNonNull(point, "point cannot be null");
    Objects.requireNonNull(edit, "edit cannot be null");
    return Native.pointEdit(point, currentByte, edit);
  }

  /**
   * 根据编辑操作更新 range，使其与编辑后的源代码保持同步。
   *
   * <p>这是 tree-sitter 0.27 {@code ts_range_edit} 的包装。此方法会原地修改传入的 range。
   *
   * @param range 要更新的 range。
   * @param edit  编辑操作。
   */
  public static void rangeEdit(TSRange range, TSInputEdit edit) {
    Objects.requireNonNull(range, "range cannot be null");
    Objects.requireNonNull(edit, "edit cannot be null");
    Native.rangeEdit(range, edit);
  }

  @GenerateNativeHeaders(fileName = "utils")
  private static class Native {

    @FastNative
    static native int pointEdit(TSPoint point, int currentByte, TSInputEdit edit);

    @FastNative
    static native void rangeEdit(TSRange range, TSInputEdit edit);
  }
}
