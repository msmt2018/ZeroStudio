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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * HTML 渲染器。
 *
 * <p>此类是 tree-sitter 0.27 {@code HtmlRenderer} 的 Java 等价实现。它将
 * {@link HighlightEvent} 事件流转换为 HTML 字符串，每个高亮区域用 {@code <span>} 标签包裹。
 *
 * <p>渲染规则：
 * <ul>
 *   <li>{@link HighlightEvent.HighlightStart} → 输出 {@code <span attrs>}（attrs 由
 *       {@code attributeCallback} 提供）。</li>
 *   <li>{@link HighlightEvent.HighlightEnd} → 输出 {@code </span>}。</li>
 *   <li>{@link HighlightEvent.Source} → 输出源码文本（HTML 转义后）。
 *       遇到换行符 {@code \n} 时，会关闭所有打开的 span、输出换行、记录行偏移、
 *       再重新打开 span，保证每行 HTML 自包含。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * HtmlRenderer renderer = new HtmlRenderer();
 * renderer.render(events, sourceCode, highlightIndex -> {
 *   String className = "ts-" + recognizedNames[highlightIndex];
 *   return ("class=\"" + className + "\"").getBytes(StandardCharsets.UTF_8);
 * });
 * String html = new String(renderer.getHtml(), StandardCharsets.UTF_8);
 * }</pre>
 */
public final class HtmlRenderer {

  private final ByteArrayOutputStream html = new ByteArrayOutputStream();
  private final List<Integer> lineOffsets = new ArrayList<>();
  private final List<Integer> highlightStack = new ArrayList<>();

  public HtmlRenderer() {
    lineOffsets.add(0);
  }

  /**
   * 渲染高亮事件流为 HTML。
   *
   * @param events            高亮事件迭代器。
   * @param source            源代码（UTF-8 字节）。
   * @param attributeCallback 回调函数，接收 highlight 索引，返回对应的 HTML 属性字符串字节。
   */
  public void render(Iterator<HighlightEvent> events, byte[] source,
      Function<Integer, byte[]> attributeCallback) {
    while (events.hasNext()) {
      HighlightEvent event = events.next();
      if (event instanceof HighlightEvent.Source s) {
        addText(source, s.start(), s.end(), attributeCallback);
      } else if (event instanceof HighlightEvent.HighlightStart h) {
        highlightStack.add(h.highlight());
        byte[] attrs = attributeCallback.apply(h.highlight());
        if (attrs != null && attrs.length > 0) {
          html.write('<');
          html.write('s');
          html.write('p');
          html.write('a');
          html.write('n');
          html.write(' ');
          html.write(attrs, 0, attrs.length);
          html.write('>');
        } else {
          writeString("<span>");
        }
      } else if (event instanceof HighlightEvent.HighlightEnd) {
        if (!highlightStack.isEmpty()) {
          highlightStack.remove(highlightStack.size() - 1);
        }
        writeString("</span>");
      }
    }
  }

  private void addText(byte[] source, int start, int end,
      Function<Integer, byte[]> attributeCallback) {
    for (int i = start; i < end; i++) {
      byte b = source[i];
      if (b == '\n') {
        // 关闭所有打开的 span
        for (int j = 0; j < highlightStack.size(); j++) {
          writeString("</span>");
        }
        html.write(b);
        lineOffsets.add(html.size());
        // 重新打开 span
        for (int h : highlightStack) {
          byte[] attrs = attributeCallback.apply(h);
          if (attrs != null && attrs.length > 0) {
            html.write('<');
            html.write('s');
            html.write('p');
            html.write('a');
            html.write('n');
            html.write(' ');
            html.write(attrs, 0, attrs.length);
            html.write('>');
          } else {
            writeString("<span>");
          }
        }
      } else if (b == '<') {
        writeString("&lt;");
      } else if (b == '>') {
        writeString("&gt;");
      } else if (b == '&') {
        writeString("&amp;");
      } else if (b == '\r') {
        // 延迟处理 \r，判断是否 CRLF
        // 简化：直接跳过 \r，由 \n 处理换行
      } else {
        html.write(b);
      }
    }
  }

  private void writeString(String s) {
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    html.write(bytes, 0, bytes.length);
  }

  /** 获取渲染后的 HTML 字节。 */
  public byte[] getHtml() {
    return html.toByteArray();
  }

  /** 获取每行的起始字节偏移（在 HTML 输出中）。 */
  public int[] getLineOffsets() {
    int[] result = new int[lineOffsets.size()];
    for (int i = 0; i < lineOffsets.size(); i++) {
      result[i] = lineOffsets.get(i);
    }
    return result;
  }

  /** 获取行数。 */
  public int getLineCount() {
    return lineOffsets.size();
  }

  /** 重置渲染器，清空已生成的内容。 */
  public void reset() {
    html.reset();
    lineOffsets.clear();
    lineOffsets.add(0);
    highlightStack.clear();
  }
}
