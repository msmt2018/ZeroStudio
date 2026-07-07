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
 * <p><strong>当前实现版本（基础版）：</strong>
 * <ul>
 *   <li>支持 highlight capture 合并（同节点的多个 capture 取最具体的）。</li>
 *   <li>支持 local scope/def/ref 追踪（引用继承定义的高亮类型）。</li>
 *   <li>支持 {@code (#not-local?)} 谓词（跳过局部变量的特定高亮 pattern）。</li>
 *   <li><strong>不支持</strong>语言嵌入（injection）——这是后续增强项。</li>
 * </ul>
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

  public Highlighter() {
  }

  /**
   * 高亮源代码，返回 {@link HighlightEvent} 迭代器。
   *
   * <p>迭代器是惰性的——每次调用 {@link Iterator#next()} 才会处理下一个 capture。
   * 这允许调用方在处理过程中取消（通过停止迭代）。
   *
   * @param config     高亮配置（必须已调用 {@link HighlightConfiguration#configure}）。
   * @param sourceCode 源代码（UTF-8 字节）。
   * @return 高亮事件迭代器。
   */
  public Iterator<HighlightEvent> highlight(HighlightConfiguration config, byte[] sourceCode) {
    parser.setLanguage(config.getLanguage());
    parser.reset();

    TSTree tree = parser.parseBytes(sourceCode);
    if (tree == null) {
      // 解析失败，返回纯 Source 事件
      return singleSourceEvent(0, sourceCode.length);
    }

    TSQueryCursor cursor = TSQueryCursor.create();
    cursor.exec(config.getQuery(), tree.getRootNode());

    return new HighlightIterator(config, cursor, tree, sourceCode);
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
    parser.close();
  }

  // ---- 高亮迭代器 ----

  /**
   * 惰性迭代器，遍历 capture 生成 HighlightEvent。
   *
   * <p>核心算法（简化版，不含 injection）：
   * <ol>
   *   <li>用 {@link TSQueryCursor#nextCapture(int[])} 按字节顺序获取 capture。</li>
   *   <li>根据 pattern_index 判断是 injection/locals/highlight 段。</li>
   *   <li>locals 段：更新 scope 栈和 local def/ref。</li>
   *   <li>highlight 段：查找 capture 对应的 highlight 索引，生成 HighlightStart/End。</li>
   *   <li>在事件之间插入 Source 事件填充未着色的源码。</li>
   * </ol>
   */
  private static final class HighlightIterator implements Iterator<HighlightEvent> {

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

    HighlightIterator(HighlightConfiguration config, TSQueryCursor cursor,
        TSTree tree, byte[] source) {
      this.config = config;
      this.cursor = cursor;
      this.tree = tree;
      this.source = source;
      // 初始全局 scope
      scopeStack.push(new LocalScope(true, 0, source.length, new ArrayList<>()));
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
          int nextCaptureStart = hasPeeked ? getPeekedCaptureStart() : Integer.MAX_VALUE;
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

        // 处理 peeked capture
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

    private void processCapture() {
      TSQueryCapture[] captures = peekedMatch.getCaptures();
      if (captures == null || peekedCaptureIndex < 0
          || peekedCaptureIndex >= captures.length) {
        hasPeeked = false;
        return;
      }

      TSQueryCapture capture = captures[peekedCaptureIndex];
      TSNode node = capture.getNode();
      int captureIndex = capture.getIndex();
      int patternIndex = peekedMatch.getPatternIndex();
      int captureStart = node.getStartByte();
      int captureEnd = node.getEndByte();

      // 消费 peeked
      hasPeeked = false;

      int localsPatternIndex = config.getLocalsPatternIndex();
      int highlightsPatternIndex = config.getHighlightsPatternIndex();

      if (patternIndex < localsPatternIndex) {
        // injection 段 —— 当前版本不支持 injection，跳过
        return;
      }

      if (patternIndex < highlightsPatternIndex) {
        // locals 段：处理 scope/def/ref
        handleLocalsCapture(captureIndex, node, patternIndex);
        return;
      }

      // highlight 段
      handleHighlightCapture(captureIndex, node, patternIndex, captureStart, captureEnd);
    }

    private void handleLocalsCapture(int captureIndex, TSNode node, int patternIndex) {
      int localScopeIndex = config.getLocalScopeCaptureIndex();
      int localDefIndex = config.getLocalDefCaptureIndex();
      int localRefIndex = config.getLocalRefCaptureIndex();

      if (captureIndex == localScopeIndex) {
        // 弹出已结束的 scope
        while (scopeStack.size() > 1 && scopeStack.peek().endByte < node.getStartByte()) {
          scopeStack.pop();
        }
        scopeStack.push(new LocalScope(true, node.getStartByte(), node.getEndByte(),
            new ArrayList<>()));
      } else if (captureIndex == localDefIndex) {
        String name = sliceString(node.getStartByte(), node.getEndByte());
        if (!scopeStack.isEmpty()) {
          // 查找是否有对应的 definition-value capture
          LocalDef def = new LocalDef(name, node.getStartByte(), node.getEndByte(), -1);
          scopeStack.peek().localDefs.add(def);
        }
      } else if (captureIndex == localRefIndex) {
        // 引用：在 scope 栈中查找同名 def
        String name = sliceString(node.getStartByte(), node.getEndByte());
        Integer refHighlight = findLocalDefHighlight(name, node.getStartByte());
        if (refHighlight != null && refHighlight >= 0) {
          emitSource(node.getStartByte());
          highlightEndStack.push(node.getEndByte());
          eventQueue.add(new HighlightEvent.HighlightStart(refHighlight));
        }
      }
      // local.definition-value 不单独处理，在后续 highlight capture 中回写
    }

    private Integer findLocalDefHighlight(String name, int refStart) {
      for (LocalScope scope : scopeStack) {
        for (LocalDef def : scope.localDefs) {
          if (def.name.equals(name) && refStart >= def.valueEndByte) {
            return def.highlight;
          }
        }
        if (!scope.inherits) break;
      }
      return null;
    }

    private void handleHighlightCapture(int captureIndex, TSNode node, int patternIndex,
        int captureStart, int captureEnd) {
      // 合并同节点的后续 highlight capture（取更具体的）
      int highlightIndex = config.getHighlightIndex(captureIndex);
      if (highlightIndex < 0) {
        return;
      }

      // 检查是否是局部变量且 pattern 标记为 non-local
      if (config.isNonLocalVariablePattern(patternIndex)) {
        String name = sliceString(captureStart, captureEnd);
        if (isLocalVariable(name)) {
          return;
        }
      }

      // peek 下一个 capture，如果同节点则合并
      while (true) {
        if (!hasPeeked) {
          peekNextCapture();
        }
        if (peekedMatch == null) break;
        TSQueryCapture[] captures = peekedMatch.getCaptures();
        if (captures == null || peekedCaptureIndex < 0
            || peekedCaptureIndex >= captures.length) break;
        TSQueryCapture nextCapture = captures[peekedCaptureIndex];
        TSNode nextNode = nextCapture.getNode();
        if (nextNode.getStartByte() != captureStart || nextNode.getEndByte() != captureEnd) {
          break;
        }
        // 同节点，检查是否更具体
        int nextHighlight = config.getHighlightIndex(nextCapture.getIndex());
        if (nextHighlight >= 0) {
          highlightIndex = nextHighlight;
        }
        hasPeeked = false;
      }

      // 回写局部变量定义的 highlight
      if (config.getLocalDefCaptureIndex() >= 0) {
        String name = sliceString(captureStart, captureEnd);
        writeBackLocalDefHighlight(name, highlightIndex);
      }

      // 生成 HighlightStart 事件
      emitSource(captureStart);
      highlightEndStack.push(captureEnd);
      eventQueue.add(new HighlightEvent.HighlightStart(highlightIndex));
    }

    private boolean isLocalVariable(String name) {
      for (LocalScope scope : scopeStack) {
        for (LocalDef def : scope.localDefs) {
          if (def.name.equals(name)) return true;
        }
        if (!scope.inherits) break;
      }
      return false;
    }

    private void writeBackLocalDefHighlight(String name, int highlight) {
      for (LocalScope scope : scopeStack) {
        for (LocalDef def : scope.localDefs) {
          if (def.name.equals(name) && def.highlight < 0) {
            def.highlight = highlight;
            return;
          }
        }
        if (!scope.inherits) break;
      }
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
      cursor.close();
      tree.close();
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
    final int defStartByte;
    final int valueEndByte;
    int highlight;  // -1 表示未设置

    LocalDef(String name, int defStartByte, int valueEndByte, int highlight) {
      this.name = name;
      this.defStartByte = defStartByte;
      this.valueEndByte = valueEndByte;
      this.highlight = highlight;
    }
  }
}
