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

import com.itsaky.androidide.treesitter.TSNode;
import com.itsaky.androidide.treesitter.TSParser;
import com.itsaky.androidide.treesitter.TSQueryCapture;
import com.itsaky.androidide.treesitter.TSQueryCursor;
import com.itsaky.androidide.treesitter.TSQueryMatch;
import com.itsaky.androidide.treesitter.TSTree;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 语法高亮器。
 *
 * <p>此类是 tree-sitter 0.27 {@code Highlighter} 的 Java 等价实现。它使用 {@link TSParser}
 * 解析源代码，用 {@link TSQueryCursor} 按 capture 字节顺序遍历 query 匹配，
 * 生成 {@link HighlightEvent} 事件流。
 *
 * <p><strong>当前实现版本：</strong>
 * <ul>
 *   <li>支持 highlight capture 合并（同节点的多个 capture 取最后一个有效的）。</li>
 *   <li>支持 local scope/def/ref 追踪（引用继承定义的高亮类型）。</li>
 *   <li>支持 {@code (#is-not? local)} 属性谓词（跳过局部变量的特定高亮 pattern）。</li>
 *   <li>支持 {@code (#set! local.scope-inherits "false")} 属性设置。</li>
 *   <li>支持 {@code @local.definition-value} capture（使用值范围而非定义节点范围）。</li>
 *   <li><strong>不支持</strong>语言嵌入（injection）——这是后续增强项。</li>
 * </ul>
 *
 * <p>算法与上游 Rust {@code highlight.rs} 一致：
 * <ol>
 *   <li>按字节顺序遍历 capture，处理同一节点上的所有 capture（locals + highlight）。</li>
 *   <li>每个 capture 处理前，先弹出已结束的 scope 和 highlight end。</li>
 *   <li>locals capture 设置 {@code referenceHighlight} 和 {@code definitionHighlightDef}。</li>
 *   <li>highlight capture 使用 {@code referenceHighlight.or(currentHighlight)} 发射 HighlightStart。</li>
 *   <li>如果节点是 local definition，回写 highlight 到 LocalDef。</li>
 * </ol>
 *
 * <p><strong>非线程安全。</strong>每个线程应使用独立的 {@code Highlighter} 实例。
 *
 * <p>使用示例：
 * <pre>{@code
 * HighlightConfiguration config = HighlightConfiguration.create(
 *     language, "javascript", highlightsQuery, "", localsQuery);
 * config.configure(recognizedNames);
 * try (Highlighter highlighter = new Highlighter()) {
 *   Iterator<HighlightEvent> events = highlighter.highlight(
 *       config, sourceCode.getBytes(StandardCharsets.UTF_8));
 *   while (events.hasNext()) {
 *     HighlightEvent event = events.next();
 *     switch (event) {
 *       case HighlightEvent.Source s -> renderSource(sourceCode, s.start(), s.end());
 *       case HighlightEvent.HighlightStart h -> startHighlight(h.highlight());
 *       case HighlightEvent.HighlightEnd e -> endHighlight();
 *     }
 *   }
 * }
 * }</pre>
 */
public final class Highlighter implements AutoCloseable {

  /** 取消检查间隔（与上游一致）。 */
  private static final int CANCELLATION_CHECK_INTERVAL = 100;

  private final TSParser parser = TSParser.create();
  private final List<HighlightIterator> activeIterators = new ArrayList<>();

  public Highlighter() {
  }

  /**
   * 高亮源代码，返回 {@link HighlightEvent} 迭代器。
   *
   * <p>迭代器是惰性的——每次调用 {@link Iterator#next()} 才会处理下一个 capture。
   * 这允许调用方在处理过程中取消（通过停止迭代）。
   *
   * <p>返回的迭代器也实现了 {@link AutoCloseable}。如果提前终止迭代，调用方应
   * 通过 {@code ((AutoCloseable) iterator).close()} 释放底层资源（cursor/tree）。
   * {@link Highlighter#close()} 也会自动关闭所有活跃的迭代器。
   *
   * @param config     高亮配置（必须已调用 {@link HighlightConfiguration#configure}）。
   * @param sourceCode 源代码（UTF-8 字节）。
   * @return 高亮事件迭代器。
   */
  public Iterator<HighlightEvent> highlight(HighlightConfiguration config, byte[] sourceCode) {
    parser.setLanguage(config.getLanguage());
    parser.reset();

    // 使用 parseUtf8Bytes 而非 parseBytes：parseBytes 内部使用 UTF16String + TSInputEncodingUTF16LE，
    // 会导致 tree-sitter 报告 UTF-16LE 字节偏移（每个字符 2 字节），与 UTF-8 源码不匹配。
    // parseUtf8Bytes 直接使用 TSInputEncodingUTF8，报告的偏移量为 UTF-8 字节偏移。
    TSTree tree = parser.parseUtf8Bytes(sourceCode);
    if (tree == null) {
      // 解析失败，返回纯 Source 事件
      return singleSourceEvent(0, sourceCode.length);
    }

    TSQueryCursor cursor = TSQueryCursor.create();
    cursor.exec(config.getQuery(), tree.getRootNode());

    HighlightIterator iter = new HighlightIterator(config, cursor, tree, sourceCode);
    activeIterators.add(iter);
    return iter;
  }

  private static Iterator<HighlightEvent> singleSourceEvent(int start, int end) {
    List<HighlightEvent> events = new ArrayList<>(1);
    if (end > start) {
      events.add(new HighlightEvent.Source(start, end));
    }
    return events.iterator();
  }

  @Override
  public void close() {
    for (HighlightIterator iter : activeIterators) {
      iter.close();
    }
    activeIterators.clear();
    parser.close();
  }

  // ---- 高亮迭代器 ----

  /**
   * 惰性迭代器，遍历 capture 生成 HighlightEvent。
   *
   * <p>核心算法（与上游 Rust {@code highlight.rs} 一致，不含 injection）：
   * <ol>
   *   <li>用 {@link TSQueryCursor#nextCapture(int[])} 按字节顺序获取 capture。</li>
   *   <li>每个 capture 处理前，先弹出已结束的 scope（A1 修复）。</li>
   *   <li>处理同一节点上的所有 locals capture（scope/def/ref），设置
   *       {@code referenceHighlight} 和 {@code definitionHighlightDef}。</li>
   *   <li>处理同一节点上的第一个 highlight capture，使用
   *       {@code referenceHighlight.or(currentHighlight)} 发射 HighlightStart（A4 修复）。</li>
   *   <li>合并同节点上后续的 highlight capture（跳过 non-local-variable pattern）。</li>
   *   <li>在事件之间插入 Source 事件填充未着色的源码。</li>
   * </ol>
   */
  private static final class HighlightIterator implements Iterator<HighlightEvent>, AutoCloseable {

    private final HighlightConfiguration config;
    private final TSQueryCursor cursor;
    private final TSTree tree;
    private final byte[] source;

    // peek 缓存：nextCapture 返回的 (match, captureIndex)
    private TSQueryMatch peekedMatch;
    private int peekedCaptureIndex;
    private boolean hasPeeked = false;

    // 事件队列：当前待输出的事件
    private final Deque<HighlightEvent> eventQueue = new ArrayDeque<>();

    // 字节游标：已输出到的字节位置
    private int byteOffset = 0;

    // highlight end 栈：记录待结束的 highlight 的 end_byte
    private final Deque<Integer> highlightEndStack = new ArrayDeque<>();

    // scope 栈：局部变量追踪
    private final Deque<LocalScope> scopeStack = new ArrayDeque<>();

    private int iterCount = 0;
    private boolean finished = false;
    private boolean closed = false;

    HighlightIterator(HighlightConfiguration config, TSQueryCursor cursor,
        TSTree tree, byte[] source) {
      this.config = config;
      this.cursor = cursor;
      this.tree = tree;
      this.source = source;
      // 初始全局 scope（与上游 Rust 一致：inherits=false, range=0..usize::MAX）
      // 注：global scope 的 inherits 标志实际不影响行为（它始终是搜索的最后一个 scope），
      // 但与上游保持一致以避免混淆。
      scopeStack.push(new LocalScope(false, 0, Integer.MAX_VALUE, new ArrayList<>()));
    }

    @Override
    public boolean hasNext() {
      if (!eventQueue.isEmpty()) return true;
      if (finished) return false;
      fillEventQueue();
      return !eventQueue.isEmpty();
    }

    @Override
    public HighlightEvent next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return eventQueue.poll();
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      finished = true;
      if (cursor != null) {
        cursor.close();
      }
      if (tree != null) {
        tree.close();
      }
    }

    private void fillEventQueue() {
      while (eventQueue.isEmpty() && !finished) {
        iterCount++;
        if (iterCount % CANCELLATION_CHECK_INTERVAL == 0) {
          // 取消检查点（当前实现不支持迭代阶段取消，预留扩展）
        }

        // 先处理 highlight end 栈（按字节顺序）
        if (!highlightEndStack.isEmpty()) {
          int nextEnd = highlightEndStack.peek();
          // 检查是否有 capture 在 end 之前
          if (!hasPeeked) {
            peekNextCapture();
          }
          int nextCaptureStart = getPeekedCaptureStart();
          if (nextEnd <= nextCaptureStart) {
            // 先输出 Source 填充到 end 位置
            emitSource(nextEnd);
            highlightEndStack.pop();
            eventQueue.add(HighlightEvent.HighlightEnd.getInstance());
            continue;
          }
        }

        if (!hasPeeked) {
          peekNextCapture();
        }

        if (peekedMatch == null) {
          // capture 流耗尽，输出剩余的 Source 和所有 HighlightEnd
          finishRemaining();
          return;
        }

        // 处理 peeked capture（可能处理同一节点上的多个 capture）
        processCapture();
      }
    }

    private void peekNextCapture() {
      int[] captureIndexOut = new int[1];
      peekedMatch = cursor.nextCapture(captureIndexOut);
      peekedCaptureIndex = captureIndexOut[0];
      hasPeeked = true;
    }

    private int getPeekedCaptureStart() {
      if (peekedMatch == null) return Integer.MAX_VALUE;
      TSQueryCapture[] captures = peekedMatch.getCaptures();
      if (captures == null || peekedCaptureIndex < 0
          || peekedCaptureIndex >= captures.length) {
        return Integer.MAX_VALUE;
      }
      return captures[peekedCaptureIndex].getNode().getStartByte();
    }

    /**
     * 处理当前 peeked capture，以及同一节点上的所有后续 capture。
     *
     * <p>这与上游 Rust {@code highlight.rs} 的主循环逻辑一致：
     * <ol>
     *   <li>弹出已结束的 scope（A1 修复：对所有 capture 都执行，不仅限于 local.scope）。</li>
     *   <li>处理所有 locals capture（同一节点上），设置 referenceHighlight 和 definitionHighlightDef。</li>
     *   <li>如果没有 highlight capture（下一 capture 是不同节点），直接返回（不发射 highlight）。</li>
     *   <li>处理 highlight capture，使用 referenceHighlight.or(currentHighlight)（A4 修复）。</li>
     *   <li>合并同节点上后续的 highlight capture。</li>
     * </ol>
     */
    private void processCapture() {
      TSQueryCapture capture = getPeekedCapture();
      if (capture == null) {
        hasPeeked = false;
        return;
      }

      TSNode node = capture.getNode();
      int nodeStart = node.getStartByte();
      int nodeEnd = node.getEndByte();
      int captureIndex = capture.getIndex();
      TSQueryMatch currentMatch = peekedMatch;
      int patternIndex = currentMatch.getPatternIndex();

      // A1: 弹出已结束的 scope（对所有 capture 都执行）
      // Rust: while range.start > layer.scope_stack.last().range.end { pop }
      while (scopeStack.size() > 1 && scopeStack.peek().endByte < nodeStart) {
        scopeStack.pop();
      }

      int localsPatternIndex = config.getLocalsPatternIndex();
      int highlightsPatternIndex = config.getHighlightsPatternIndex();

      // injection 段 —— 当前版本不支持 injection，跳过
      if (patternIndex < localsPatternIndex) {
        hasPeeked = false;
        return;
      }

      // 每个节点的局部状态
      Integer referenceHighlight = null;
      LocalDef definitionHighlightDef = null;

      // 处理同一节点上的所有 locals capture
      while (patternIndex < highlightsPatternIndex) {
        int localScopeIdx = config.getLocalScopeCaptureIndex();
        int localDefIdx = config.getLocalDefCaptureIndex();
        int localRefIdx = config.getLocalRefCaptureIndex();
        int localDefValueIdx = config.getLocalDefValueCaptureIndex();

        if (captureIndex == localScopeIdx) {
          // A2: 读取 local.scope-inherits 属性
          boolean inherits = config.doesScopeInherit(patternIndex);
          scopeStack.push(new LocalScope(inherits, nodeStart, nodeEnd, new ArrayList<>()));
          definitionHighlightDef = null;
        } else if (captureIndex == localDefIdx) {
          // A3: 扫描当前 match 的 captures 查找 local.definition-value
          int valueEndByte = 0; // Rust fallback: 0..0
          if (localDefValueIdx >= 0) {
            TSQueryCapture[] matchCaptures = currentMatch.getCaptures();
            if (matchCaptures != null) {
              for (TSQueryCapture c : matchCaptures) {
                if (c.getIndex() == localDefValueIdx) {
                  valueEndByte = c.getNode().getEndByte();
                  break;
                }
              }
            }
          }
          String name = sliceString(nodeStart, nodeEnd);
          if (!scopeStack.isEmpty()) {
            LocalDef def = new LocalDef(name, valueEndByte);
            scopeStack.peek().localDefs.add(def);
            definitionHighlightDef = def;
          }
          referenceHighlight = null;
        } else if (captureIndex == localRefIdx && definitionHighlightDef == null) {
          // A4: 不立即发射 HighlightStart，存储 referenceHighlight
          String name = sliceString(nodeStart, nodeEnd);
          referenceHighlight = findLocalDefHighlight(name, nodeStart);
          definitionHighlightDef = null;
        }

        // 消费当前 capture，peek 下一个
        hasPeeked = false;
        capture = getPeekedCapture();
        if (capture == null) {
          // 没有更多 capture，此节点无 highlight capture
          return;
        }
        TSNode nextNode = capture.getNode();
        if (nextNode.getStartByte() != nodeStart || nextNode.getEndByte() != nodeEnd) {
          // 不同节点，此节点无 highlight capture，保留 peek 给下次 processCapture
          return;
        }
        // 同一节点，继续处理下一个 capture
        currentMatch = peekedMatch;
        captureIndex = capture.getIndex();
        patternIndex = currentMatch.getPatternIndex();
      }

      // 现在处于 highlight 段
      // 当前 peeked capture 是此节点的第一个 highlight capture
      int highlightIndex = config.getHighlightIndex(captureIndex);

      // 合并同节点上后续的 highlight capture
      // Rust 第二个 while 循环：peek next, if same node consume and update
      hasPeeked = false; // 消费第一个 highlight capture
      while (true) {
        TSQueryCapture nextCap = getPeekedCapture();
        if (nextCap == null) break;
        TSNode nextNode = nextCap.getNode();
        if (nextNode.getStartByte() != nodeStart || nextNode.getEndByte() != nodeEnd) break;

        int nextPattern = peekedMatch.getPatternIndex();
        // 消费此 capture
        hasPeeked = false;

        // 如果当前节点是 local def/ref，跳过 non-local-variable pattern
        if ((definitionHighlightDef != null || referenceHighlight != null)
            && config.isNonLocalVariablePattern(nextPattern)) {
          continue;
        }
        // 与上游 Rust 一致：更新 capture.index 为最后未跳过的 capture，
        // 然后 current_highlight = highlight_indices[capture.index]。
        // 即使该 capture 的 highlight 为 -1（None），也要更新（覆盖前一个有效值），
        // 因为 Rust 在循环结束后用最后未跳过的 capture 重新计算 current_highlight。
        captureIndex = nextCap.getIndex();
        highlightIndex = config.getHighlightIndex(captureIndex);
      }

      // A4: 使用 referenceHighlight.or(currentHighlight)
      int effectiveHighlight = referenceHighlight != null ? referenceHighlight : highlightIndex;

      // 回写局部变量定义的 highlight
      if (definitionHighlightDef != null && effectiveHighlight >= 0) {
        definitionHighlightDef.highlight = effectiveHighlight;
      }

      // 生成 HighlightStart 事件
      if (effectiveHighlight >= 0) {
        emitSource(nodeStart);
        highlightEndStack.push(nodeEnd);
        eventQueue.add(new HighlightEvent.HighlightStart(effectiveHighlight));
      }
    }

    /**
     * 获取当前 peeked capture。如果 hasPeeked 为 false，先 peek。
     * 返回 null 表示没有更多 capture。
     */
    private TSQueryCapture getPeekedCapture() {
      if (!hasPeeked) {
        peekNextCapture();
      }
      if (peekedMatch == null) return null;
      TSQueryCapture[] captures = peekedMatch.getCaptures();
      if (captures == null || peekedCaptureIndex < 0
          || peekedCaptureIndex >= captures.length) {
        return null;
      }
      return captures[peekedCaptureIndex];
    }

    /**
     * 在 scope 栈中查找同名 def 的 highlight。
     *
     * <p>与上游 Rust 一致：从栈顶向下搜索，每个 scope 内从后向前搜索 def。
     * 如果 scope.inherits 为 false 则停止搜索。
     *
     * @return def 的 highlight 值，如果 def 存在但 highlight 未设置则返回 null，
     *         如果 def 不存在也返回 null。
     */
    private Integer findLocalDefHighlight(String name, int refStart) {
      for (LocalScope scope : scopeStack) {
        // 从后向前搜索 def（与 Rust iter().rev() 一致）
        for (int i = scope.localDefs.size() - 1; i >= 0; i--) {
          LocalDef def = scope.localDefs.get(i);
          if (def.name.equals(name) && refStart >= def.valueEndByte) {
            return def.highlight >= 0 ? def.highlight : null;
          }
        }
        if (!scope.inherits) break;
      }
      return null;
    }

    private void emitSource(int upTo) {
      if (byteOffset < upTo) {
        eventQueue.add(new HighlightEvent.Source(byteOffset, upTo));
        byteOffset = upTo;
      }
    }

    private void finishRemaining() {
      // 输出剩余的 HighlightEnd
      while (!highlightEndStack.isEmpty()) {
        int end = highlightEndStack.peek();
        emitSource(end);
        highlightEndStack.pop();
        eventQueue.add(HighlightEvent.HighlightEnd.getInstance());
      }
      // 输出最后的 Source
      if (byteOffset < source.length) {
        eventQueue.add(new HighlightEvent.Source(byteOffset, source.length));
        byteOffset = source.length;
      }
      finished = true;
      // 清理资源
      close();
    }

    private String sliceString(int start, int end) {
      if (start < 0 || end > source.length || start > end) return "";
      return new String(source, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  // ---- 内部数据类 ----

  private static final class LocalScope {
    final boolean inherits;
    final int startByte;
    final int endByte;
    final List<LocalDef> localDefs;

    LocalScope(boolean inherits, int startByte, int endByte, List<LocalDef> localDefs) {
      this.inherits = inherits;
      this.startByte = startByte;
      this.endByte = endByte;
      this.localDefs = localDefs;
    }
  }

  private static final class LocalDef {
    final String name;
    final int valueEndByte;
    int highlight;  // -1 表示未设置（对应 Rust Option::None）

    LocalDef(String name, int valueEndByte) {
      this.name = name;
      this.valueEndByte = valueEndByte;
      this.highlight = -1;
    }
  }
}
