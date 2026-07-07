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

package com.itsaky.androidide.treesitter.highlight;

/**
 * 语法高亮事件。
 *
 * <p>对应 tree-sitter 0.27 的 {@code HighlightEvent} 枚举。高亮器在遍历语法树时会产生
 * 一个有序的事件流，调用方根据事件类型渲染高亮输出。
 *
 * <p>事件流按字节顺序交错出现，形成"开标签→源码→关标签"的序列：
 * <pre>
 *   HighlightStart(function) @ byte 0
 *   Source [0, 10)           — "function foo"
 *   HighlightEnd             @ byte 10
 *   Source [10, 11)          — " "
 *   HighlightStart(variable) @ byte 11
 *   Source [11, 15)          — "bar"
 *   HighlightEnd             @ byte 15
 * </pre>
 *
 * <p>这是一个 sealed 接口，有三个实现：
 * <ul>
 *   <li>{@link Source} — 一段未着色的源代码字节范围。</li>
 *   <li>{@link HighlightStart} — 开始一个高亮区域。</li>
 *   <li>{@link HighlightEnd} — 结束当前高亮区域。</li>
 * </ul>
 */
public sealed interface HighlightEvent {

  /** 一段源代码字节范围 [start, end)。 */
  record Source(int start, int end) implements HighlightEvent {
    public Source {
      if (start < 0 || end < start) {
        throw new IllegalArgumentException("Invalid source range: [" + start + ", " + end + ")");
      }
    }
  }

  /** 开始一个高亮区域。{@code highlight} 是高亮类型索引，对应配置中的 recognized names。 */
  record HighlightStart(int highlight) implements HighlightEvent {
    public HighlightStart {
      if (highlight < 0) {
        throw new IllegalArgumentException("highlight index cannot be negative: " + highlight);
      }
    }
  }

  /** 结束当前高亮区域。单例，使用 {@link #INSTANCE}。 */
  final class HighlightEnd implements HighlightEvent {
    private static final HighlightEnd INSTANCE = new HighlightEnd();

    private HighlightEnd() { }

    public static HighlightEnd getInstance() {
      return INSTANCE;
    }

    @Override
    public String toString() {
      return "HighlightEnd";
    }
  }
}
