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
 *   <li>回车符 {@code \r} 不直接输出。如果不是 CRLF 的一部分（即 {@code \r} 后
 *       不跟 {@code \n}），则可通过 {@link #setCarriageReturnHighlight} 配置的
 *       高亮样式在对应位置插入一个空的 {@code <span>} 标签。</li>
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
  private Integer carriageReturnHighlight = null;
  private Integer lastCarriageReturn = null;

  public HtmlRenderer() {
    lineOffsets.add(0);
  }

  /**
   * 设置对独立回车符（非 CRLF 的一部分）应用的高亮样式。
   *
   * @param highlight 高亮索引，或 {@code null} 表示不对回车符应用高亮。
   */
  public void setCarriageReturnHighlight(Integer highlight) {
    this.carriageReturnHighlight = highlight;
  }

  /**
   * 渲染高亮事件流为 HTML。
   *
   * <p>渲染结束后，HTML 末尾保证以 {@code \n} 结尾（与上游 Rust 一致），
   * 并且行偏移列表不会包含指向 HTML 末尾的尾部空行偏移。
   *
   * @param events            高亮事件迭代器。
   * @param source            源代码（UTF-8 字节）。
   * @param attributeCallback 回调函数，接收 highlight 索引，返回对应的 HTML 属性字符串字节。
   */
  public void render(Iterator<HighlightEvent> events, byte[] source,
      Function<Integer, byte[]> attributeCallback) {
    List<Integer> highlights = new ArrayList<>();
    while (events.hasNext()) {
      HighlightEvent event = events.next();
      if (event instanceof HighlightEvent.Source s) {
        addText(source, s.start(), s.end(), highlights, attributeCallback);
      } else if (event instanceof HighlightEvent.HighlightStart h) {
        highlights.add(h.highlight());
        startHighlight(h.highlight(), attributeCallback);
      } else if (event instanceof HighlightEvent.HighlightEnd) {
        if (!highlights.isEmpty()) {
          highlights.remove(highlights.size() - 1);
        }
        endHighlight();
      }
    }
    // 处理末尾未决的回车
    if (lastCarriageReturn != null) {
      int offset = lastCarriageReturn;
      lastCarriageReturn = null;
      addCarriageReturn(offset, attributeCallback);
    }
    // 保证 HTML 以 \n 结尾
    byte[] htmlBytes = html.toByteArray();
    if (htmlBytes.length == 0 || htmlBytes[htmlBytes.length - 1] != '\n') {
      html.write('\n');
    }
    // 若最后一个行偏移指向 HTML 末尾（即尾部空行），则移除
    int htmlLen = html.size();
    if (!lineOffsets.isEmpty() && lineOffsets.get(lineOffsets.size() - 1) == htmlLen) {
      lineOffsets.remove(lineOffsets.size() - 1);
    }
  }

  private void startHighlight(int h, Function<Integer, byte[]> attributeCallback) {
    // 与上游 Rust 一致：无条件输出 <span + attrs + >
    writeBytes(BYTE_SPAN_PREFIX);
    byte[] attrs = attributeCallback.apply(h);
    if (attrs != null && attrs.length > 0) {
      html.write(attrs, 0, attrs.length);
    }
    html.write('>');
  }

  private void endHighlight() {
    writeBytes(BYTE_SPAN_END);
  }

  private void addText(byte[] source, int start, int end, List<Integer> highlights,
      Function<Integer, byte[]> attributeCallback) {
    for (int i = start; i < end; i++) {
      byte b = source[i];
      // 不直接渲染回车符，但允许独立回车符（非 CRLF）通过 attribute callback 设置样式
      if (b == '\r') {
        lastCarriageReturn = html.size();
        continue;
      }
      if (lastCarriageReturn != null && b != '\n') {
        int offset = lastCarriageReturn;
        lastCarriageReturn = null;
        addCarriageReturn(offset, attributeCallback);
      }
      // 在行边界处，关闭并重新打开所有高亮标签
      if (b == '\n') {
        for (int j = 0; j < highlights.size(); j++) {
          endHighlight();
        }
        html.write(b);
        lineOffsets.add(html.size());
        for (int h : highlights) {
          startHighlight(h, attributeCallback);
        }
      } else if (b == '<') {
        writeBytes(BYTE_LT);
      } else if (b == '>') {
        writeBytes(BYTE_GT);
      } else if (b == '&') {
        writeBytes(BYTE_AMP);
      } else if (b == '\'') {
        writeBytes(BYTE_SQUOT);
      } else if (b == '"') {
        writeBytes(BYTE_DQUOT);
      } else {
        html.write(b);
      }
    }
  }

  /**
   * 在之前记录的回车偏移处插入一个空的 {@code <span>} 标签（如果配置了回车高亮）。
   *
   * <p>与上游 Rust 一致：将 html 在 offset 处分裂，插入 {@code <span attrs></span>}，
   * 再追加剩余部分。
   */
  private void addCarriageReturn(int offset, Function<Integer, byte[]> attributeCallback) {
    if (carriageReturnHighlight == null) {
      return;
    }
    byte[] current = html.toByteArray();
    html.reset();
    html.write(current, 0, offset);
    writeBytes(BYTE_SPAN_PREFIX);
    byte[] attrs = attributeCallback.apply(carriageReturnHighlight);
    if (attrs != null && attrs.length > 0) {
      html.write(attrs, 0, attrs.length);
    }
    writeBytes(BYTE_SPAN_END);
    html.write(current, offset, current.length - offset);
  }

  private void writeBytes(byte[] bytes) {
    html.write(bytes, 0, bytes.length);
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

  /**
   * 按行偏移切分 HTML 并返回每行的字符串。
   *
   * @return 行字符串列表。
   */
  public List<String> lines() {
    List<String> result = new ArrayList<>(lineOffsets.size());
    byte[] htmlBytes = html.toByteArray();
    for (int i = 0; i < lineOffsets.size(); i++) {
      int lineStart = lineOffsets.get(i);
      int lineEnd = (i + 1 == lineOffsets.size()) ? htmlBytes.length : lineOffsets.get(i + 1);
      result.add(new String(htmlBytes, lineStart, lineEnd - lineStart, StandardCharsets.UTF_8));
    }
    return result;
  }

  /** 重置渲染器，清空已生成的内容。保留 {@link #setCarriageReturnHighlight} 的配置。 */
  public void reset() {
    html.reset();
    lineOffsets.clear();
    lineOffsets.add(0);
    lastCarriageReturn = null;
  }

  // ---- HTML 转义常量 ----

  private static final byte[] BYTE_SPAN_PREFIX = "<span ".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BYTE_SPAN_END = "</span>".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BYTE_LT = "&lt;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BYTE_GT = "&gt;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BYTE_AMP = "&amp;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BYTE_SQUOT = "&#39;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BYTE_DQUOT = "&quot;".getBytes(StandardCharsets.UTF_8);
}
