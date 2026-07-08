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
import com.itsaky.androidide.treesitter.TSPoint;
import com.itsaky.androidide.treesitter.TSQuery;
import com.itsaky.androidide.treesitter.TSQueryCapture;
import com.itsaky.androidide.treesitter.TSQueryCursor;
import com.itsaky.androidide.treesitter.TSQueryMatch;
import com.itsaky.androidide.treesitter.TSRange;
import com.itsaky.androidide.treesitter.TSTree;
import com.itsaky.androidide.treesitter.TSTreeCursor;
import java.nio.charset.StandardCharsets;
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
 *   <li>支持 {@code #set! local.scope-inherits "false"} 属性设置。</li>
 *   <li>支持 {@code @local.definition-value} capture（使用值范围而非定义节点范围）。</li>
 *   <li>支持语言嵌入（injection）：通过 {@code injectionCallback} 回调为嵌入语言
 *       （如 HTML 中的 JS/CSS）创建独立的高亮层，支持
 *       {@code #set! injection.language/self/parent/include-children/combined}。</li>
 * </ul>
 *
 * <p>算法与上游 Rust {@code highlight.rs} 一致：
 * <ol>
 *   <li>使用多层级（multi-layer）架构：每层对应一种语言/配置，独立解析与遍历。</li>
 *   <li>层按 sort_key（字节偏移，end 先于 start，深度大的优先）排序。</li>
 *   <li>每层内按字节顺序遍历 capture，处理 injection/locals/highlight。</li>
 *   <li>遇到 injection pattern 时，调用 {@code injection_for_match} 提取语言名和内容节点，
 *       通过 {@code injection_callback} 获取目标语言的配置，递归创建新层。</li>
 *   <li>combined injections 在层初始化时预先处理（合并多段不连续内容）。</li>
 * </ol>
 *
 * <p><strong>非线程安全。</strong>每个线程应使用独立的 {@code Highlighter} 实例。
 *
 * <p>使用示例：
 * <pre>{@code
 * HighlightConfiguration config = HighlightConfiguration.create(
 *     language, "javascript", highlightsQuery, "", localsQuery);
 * config.configure(recognizedNames);
 * // injectionCallback: 语言名 -> 配置（用于嵌入语言高亮）
 * java.util.function.Function<String, HighlightConfiguration> injectionCallback =
 *     name -> loadConfigForLanguage(name);
 * try (Highlighter highlighter = new Highlighter()) {
 *   Iterator<HighlightEvent> events = highlighter.highlight(
 *       config, sourceCode.getBytes(StandardCharsets.UTF_8), injectionCallback);
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
  private final List<HighlightIterLayer> allLayers = new ArrayList<>();

  public Highlighter() {
  }

  /**
   * 高亮源代码，返回 {@link HighlightEvent} 迭代器（不支持 injection）。
   *
   * <p>等价于 {@code highlight(config, sourceCode, null)}。
   *
   * @param config     高亮配置（必须已调用 {@link HighlightConfiguration#configure}）。
   * @param sourceCode 源代码（UTF-8 字节）。
   * @return 高亮事件迭代器。
   */
  public Iterator<HighlightEvent> highlight(HighlightConfiguration config, byte[] sourceCode) {
    return highlight(config, sourceCode, null);
  }

  /**
   * 高亮源代码，返回 {@link HighlightEvent} 迭代器（支持 injection）。
   *
   * <p>迭代器是惰性的——每次调用 {@link Iterator#next()} 才会处理下一个 capture。
   * 这允许调用方在处理过程中取消（通过停止迭代）。
   *
   * <p>返回的迭代器也实现了 {@link AutoCloseable}。如果提前终止迭代，调用方应
   * 通过 {@code ((AutoCloseable) iterator).close()} 释放底层资源（cursor/tree）。
   * {@link Highlighter#close()} 也会自动关闭所有活跃的迭代器。
   *
   * @param config            高亮配置（必须已调用 {@link HighlightConfiguration#configure}）。
   * @param sourceCode        源代码（UTF-8 字节）。
   * @param injectionCallback 语言名 -> 高亮配置的回调，用于嵌入语言高亮。
   *                          可为 null（不支持 injection）。
   * @return 高亮事件迭代器。
   */
  public Iterator<HighlightEvent> highlight(HighlightConfiguration config, byte[] sourceCode,
      InjectionCallback injectionCallback) {
    TSRange initialRange = TSRange.create(0, Integer.MAX_VALUE,
        TSPoint.create(0, 0), TSPoint.create(Integer.MAX_VALUE, Integer.MAX_VALUE));
    List<HighlightIterLayer> layers = HighlightIterLayer.newLayer(
        sourceCode, null, this, parser, config, 0,
        new TSRange[]{initialRange}, injectionCallback, allLayers);
    if (layers.isEmpty()) {
      return singleSourceEvent(0, sourceCode.length);
    }

    HighlightIter iter = new HighlightIter(sourceCode, config.getLanguageName(),
        layers, injectionCallback, allLayers);
    iter.sortLayers();
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
    for (HighlightIterLayer layer : allLayers) {
      layer.close();
    }
    allLayers.clear();
    parser.close();
  }

  /** 语言注入回调：给定语言名，返回该语言的高亮配置，null 表示不支持。 */
  @FunctionalInterface
  public interface InjectionCallback {
    HighlightConfiguration getConfig(String languageName);
  }

  // ---- 主迭代器（多层级） ----

  /**
   * 主高亮迭代器，管理多个 {@link HighlightIterLayer}，按字节顺序合并输出事件。
   *
   * <p>算法与上游 Rust {@code HighlightIter} 一致：
   * <ol>
   *   <li>layers[0] 始终是 sort_key 最小的层（最早的高亮边界）。</li>
   *   <li>每次从 layers[0] 取一个事件，然后重新排序（sort_layers）。</li>
   *   <li>层耗尽后从列表移除。</li>
   *   <li>遇到 injection 时插入新层（insert_layer）。</li>
   * </ol>
   */
  private static final class HighlightIter implements Iterator<HighlightEvent>, AutoCloseable {

    private final byte[] source;
    private final String languageName;
    private final List<HighlightIterLayer> layers;
    private final InjectionCallback injectionCallback;
    private final List<HighlightIterLayer> allLayers; // 用于 close() 时的全局清理

    private final Deque<HighlightEvent> eventQueue = new ArrayDeque<>();
    private int byteOffset = 0;
    private int iterCount = 0;
    private boolean finished = false;
    private boolean closed = false;

    // 记录上一个高亮范围，用于跳过被更深 layer 覆盖的重复高亮
    private int lastHighlightStart = -1;
    private int lastHighlightEnd = -1;
    private int lastHighlightDepth = -1;

    HighlightIter(byte[] source, String languageName, List<HighlightIterLayer> layers,
        InjectionCallback injectionCallback, List<HighlightIterLayer> allLayers) {
      this.source = source;
      this.languageName = languageName;
      this.layers = layers;
      this.injectionCallback = injectionCallback;
      this.allLayers = allLayers;
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
      // 关闭所有活跃的层（释放 cursor/tree 资源）
      for (HighlightIterLayer layer : layers) {
        layer.close();
      }
      layers.clear();
    }

    private void fillEventQueue() {
      while (eventQueue.isEmpty() && !finished) {
        iterCount++;
        if (iterCount % CANCELLATION_CHECK_INTERVAL == 0) {
          // 取消检查点（当前实现不支持迭代阶段取消，预留扩展）
        }

        if (layers.isEmpty()) {
          // 所有层耗尽，输出剩余 Source
          if (byteOffset < source.length) {
            eventQueue.add(new HighlightEvent.Source(byteOffset, source.length));
            byteOffset = source.length;
          }
          finished = true;
          return;
        }

        HighlightIterLayer layer = layers.get(0);
        HighlightEvent ev = layer.nextEvent(this);
        if (ev != null) {
          eventQueue.add(ev);
        }
        // 层耗尽则移除
        if (layer.isExhausted()) {
          layers.remove(0);
          // 不从 allLayers 移除（close 时统一清理）
        }
        sortLayers();
      }
    }

    /** 按 sort_key 排序 layers（保持 layers[0] 为最小 sort_key）。 */
    void sortLayers() {
      // 移除已耗尽的层
      layers.removeIf(HighlightIterLayer::isExhausted);
      // 简单选择排序：找到最小 sort_key 的层放到最前
      // （层数通常很少，O(n^2) 可接受）
      for (int i = 0; i < layers.size(); i++) {
        long bestKey = layers.get(i).sortKeyOrMax();
        int bestIdx = i;
        for (int j = i + 1; j < layers.size(); j++) {
          long key = layers.get(j).sortKeyOrMax();
          if (key < bestKey) {
            bestKey = key;
            bestIdx = j;
          }
        }
        if (bestIdx != i) {
          HighlightIterLayer tmp = layers.get(i);
          layers.set(i, layers.get(bestIdx));
          layers.set(bestIdx, tmp);
        }
      }
    }

    /** 插入新层（用于 injection），保持排序。 */
    void insertLayer(HighlightIterLayer layer) {
      long key = layer.sortKeyOrMax();
      int i = 0;
      while (i < layers.size()) {
        if (layers.get(i).sortKeyOrMax() > key) {
          layers.add(i, layer);
          return;
        }
        i++;
      }
      layers.add(layer);
    }

    /** 发射 Source 事件填充到 offset，并更新 byteOffset。 */
    void emitSource(int offset) {
      if (byteOffset < offset) {
        eventQueue.add(new HighlightEvent.Source(byteOffset, offset));
        byteOffset = offset;
      }
    }

    int getByteOffset() { return byteOffset; }

    void setByteOffset(int offset) { this.byteOffset = offset; }

    InjectionCallback getInjectionCallback() { return injectionCallback; }

    String getLanguageName() { return languageName; }

    List<HighlightIterLayer> getLayers() { return layers; }

    int getLastHighlightStart() { return lastHighlightStart; }
    int getLastHighlightEnd() { return lastHighlightEnd; }
    int getLastHighlightDepth() { return lastHighlightDepth; }
    void setLastHighlight(int start, int end, int depth) {
      this.lastHighlightStart = start;
      this.lastHighlightEnd = end;
      this.lastHighlightDepth = depth;
    }
  }

  // ---- 单层迭代器 ----

  /**
   * 单个高亮层，对应一种语言/配置。
   *
   * <p>每层有独立的 cursor/tree/scope_stack/highlight_end_stack。
   * 层的 sort_key 决定其在 {@link HighlightIter} 中的优先级。
   *
   * <p>核心算法（与上游 Rust {@code HighlightIterLayer} + 主循环一致）：
   * <ol>
   *   <li>用 {@link TSQueryCursor#nextCapture(int[])} 按字节顺序获取 capture。</li>
   *   <li>弹出已结束的 scope（A1 修复：对所有 capture 都执行）。</li>
   *   <li>处理 injection pattern（pattern_index < locals_pattern_index）。</li>
   *   <li>处理 locals capture（scope/def/ref），设置 referenceHighlight 和 definitionHighlightDef。</li>
   *   <li>处理 highlight capture，使用 referenceHighlight.or(currentHighlight)（A4 修复）。</li>
   *   <li>合并同节点上后续的 highlight capture。</li>
   * </ol>
   */
  private static final class HighlightIterLayer implements AutoCloseable {

    private final HighlightConfiguration config;
    private final TSQueryCursor cursor;
    private final TSTree tree;
    private final byte[] source;
    private final int depth;
    private final TSRange[] ranges;
    private final List<HighlightIterLayer> allLayers;
    private final TSParser parser;

    // peek 缓存
    private TSQueryMatch peekedMatch;
    private int peekedCaptureIndex;
    private boolean hasPeeked = false;

    // highlight end 栈
    private final Deque<Integer> highlightEndStack = new ArrayDeque<>();
    // scope 栈
    private final Deque<LocalScope> scopeStack = new ArrayDeque<>();

    private boolean exhausted = false;
    private boolean closed = false;

    private HighlightIterLayer(HighlightConfiguration config, TSQueryCursor cursor,
        TSTree tree, byte[] source, int depth, TSRange[] ranges,
        List<HighlightIterLayer> allLayers, TSParser parser) {
      this.config = config;
      this.cursor = cursor;
      this.tree = tree;
      this.source = source;
      this.depth = depth;
      this.ranges = ranges;
      this.allLayers = allLayers;
      this.parser = parser;
      // 初始全局 scope（与上游 Rust 一致：inherits=false, range=0..usize::MAX）
      scopeStack.push(new LocalScope(false, 0, Integer.MAX_VALUE, new ArrayList<>()));
    }

    /**
     * 创建新的高亮层（对应上游 Rust {@code HighlightIterLayer::new}）。
     *
     * <p>解析源码，设置 included_ranges，处理 combined injections，执行 query。
     *
     * @param source             源码（UTF-8）
     * @param parentName         父语言名（用于 injection.parent），可为 null
     * @param highlighter        所属 Highlighter（当前未使用，预留）
     * @param parser             解析器（共享）
     * @param config             高亮配置
     * @param depth              层深度（根层为 0）
     * @param ranges             解析范围
     * @param injectionCallback  injection 回调
     * @param allLayers          全局层列表（用于 close 清理）
     * @return 创建的层列表（可能多个，因 combined injections 会递归创建子层）
     */
    static List<HighlightIterLayer> newLayer(byte[] source, String parentName,
        Highlighter highlighter, TSParser parser, HighlightConfiguration config,
        int depth, TSRange[] ranges, InjectionCallback injectionCallback,
        List<HighlightIterLayer> allLayers) {
      List<HighlightIterLayer> result = new ArrayList<>();
      List<Object[]> queue = new ArrayList<>(); // [config, depth, ranges]

      HighlightConfiguration currentConfig = config;
      int currentDepth = depth;
      TSRange[] currentRanges = ranges;

      while (true) {
        parser.setIncludedRanges(currentRanges);
        parser.setLanguage(currentConfig.getLanguage());
        parser.reset();

        // 使用 parseUtf8Bytes 确保偏移量为 UTF-8 字节偏移
        TSTree tree = parser.parseUtf8Bytes(source);
        if (tree == null) {
          // 解析失败，跳过此层
          if (queue.isEmpty()) break;
          Object[] next = queue.remove(0);
          currentConfig = (HighlightConfiguration) next[0];
          currentDepth = (int) next[1];
          currentRanges = (TSRange[]) next[2];
          continue;
        }

        TSQueryCursor cursor = TSQueryCursor.create();
        cursor.exec(currentConfig.getQuery(), tree.getRootNode());

        // 处理 combined injections（上游 Rust: Process combined injections）
        TSQuery combinedQuery = currentConfig.getCombinedInjectionsQuery();
        if (combinedQuery != null) {
          // 收集每个 pattern 的 (language_name, content_nodes, include_children)
          int patternCount = combinedQuery.getPatternCount();
          // 用 Object[] 存 [String langName, List<TSNode> contentNodes, Boolean includeChildren]
          List<Object[]> injectionsByPattern = new ArrayList<>(patternCount);
          for (int i = 0; i < patternCount; i++) {
            injectionsByPattern.add(new Object[]{null, new ArrayList<TSNode>(), Boolean.FALSE});
          }

          TSQueryCursor combinedCursor = TSQueryCursor.create();
          combinedCursor.exec(combinedQuery, tree.getRootNode());
          TSQueryMatch m;
          while ((m = combinedCursor.nextMatch()) != null) {
            int patternIndex = m.getPatternIndex();
            if (patternIndex < 0 || patternIndex >= patternCount) continue;
            Object[] entry = injectionsByPattern.get(patternIndex);
            TSQueryCapture[] captures = m.getCaptures();
            if (captures == null) continue;
            // 调用 injection_for_match 提取 (language_name, content_node, include_children)
            InjectionForMatchResult ifm = injectionForMatch(
                currentConfig, parentName, currentConfig.getQuery(), m, source);
            if (ifm.languageName != null) {
              entry[0] = ifm.languageName;
            }
            if (ifm.contentNode != null) {
              @SuppressWarnings("unchecked")
              List<TSNode> contentNodes = (List<TSNode>) entry[1];
              contentNodes.add(ifm.contentNode);
            }
            entry[2] = ifm.includeChildren;
          }
          combinedCursor.close();

          // 为每个有 language_name 和 content_nodes 的 combined injection 创建子层
          for (Object[] entry : injectionsByPattern) {
            String langName = (String) entry[0];
            @SuppressWarnings("unchecked")
            List<TSNode> contentNodes = (List<TSNode>) entry[1];
            boolean includesChildren = (Boolean) entry[2];
            if (langName != null && !contentNodes.isEmpty() && injectionCallback != null) {
              HighlightConfiguration nextConfig = injectionCallback.getConfig(langName);
              if (nextConfig != null) {
                TSNode[] nodeArr = contentNodes.toArray(new TSNode[0]);
                TSRange[] intersected = intersectRanges(currentRanges, nodeArr, includesChildren);
                if (intersected.length > 0) {
                  queue.add(new Object[]{nextConfig, currentDepth + 1, intersected});
                }
              }
            }
          }
        }

        HighlightIterLayer layer = new HighlightIterLayer(currentConfig, cursor, tree,
            source, currentDepth, currentRanges, allLayers, parser);
        result.add(layer);
        allLayers.add(layer);

        if (queue.isEmpty()) break;
        Object[] next = queue.remove(0);
        currentConfig = (HighlightConfiguration) next[0];
        currentDepth = (int) next[1];
        currentRanges = (TSRange[]) next[2];
      }

      return result;
    }

    /**
     * 计算此层的 sort_key。
     *
     * <p>与上游 Rust 一致：取下一个 capture 的 start_byte 或 highlight_end_stack 的 end_byte，
     * 较小者优先。end 事件（false）先于 start 事件（true）。深度大的优先。
     * 返回一个 long 编码：(offset << 2) | (isStart ? 0 : 1) | (depth 高位)
     * 实际上上游用 (offset, is_start, depth) 三元组比较，is_start=false(end) 排在前。
     *
     * @return sort_key，或 null 表示层已耗尽。
     */
    private long sortKey() {
      int nextStart = Integer.MAX_VALUE;
      if (!hasPeeked) {
        peekNextCapture();
      }
      if (peekedMatch != null) {
        TSQueryCapture[] captures = peekedMatch.getCaptures();
        if (captures != null && peekedCaptureIndex >= 0
            && peekedCaptureIndex < captures.length) {
          nextStart = captures[peekedCaptureIndex].getNode().getStartByte();
        }
      }
      int nextEnd = highlightEndStack.isEmpty() ? Integer.MAX_VALUE : highlightEndStack.peek();

      if (nextStart == Integer.MAX_VALUE && nextEnd == Integer.MAX_VALUE) {
        return Long.MAX_VALUE; // 耗尽
      }

      // 编码：offset 在高位，end(0) 优先于 start(1)，深度大的优先（用负数）
      int offset;
      int isStartFlag;
      if (nextStart < nextEnd) {
        offset = nextStart;
        isStartFlag = 1; // start
      } else {
        offset = nextEnd;
        isStartFlag = 0; // end 优先
      }
      // 深度越大越优先（用 -depth，这样大深度排在前）
      // 编码: (offset << 16) | (isStartFlag << 8) | (depth & 0xff)
      // 但 offset 可能很大，用 long
      // 排序：offset 升序，isStart 升序(end=0 先)，depth 降序(大深度先)
      // 用负 depth 实现降序：sortKey = (offset << 16) | (isStartFlag << 8) | (255 - depth)
      // 但 depth 可能 > 255，这里简化处理
      return ((long) offset << 16) | ((long) isStartFlag << 8) | (255 - Math.min(depth, 255));
    }

    long sortKeyOrMax() {
      if (exhausted) return Long.MAX_VALUE;
      long key = sortKey();
      return key == Long.MAX_VALUE ? Long.MAX_VALUE : key;
    }

    boolean isExhausted() {
      // 必须在 peek 之后才能判断是否耗尽。
      // 新层初始 hasPeeked=false，不应被判为耗尽（否则会在 sortLayers 中被误删）。
      // 只有 peek 后 peekedMatch==null 且 highlight_end_stack 为空才表示真正耗尽。
      return exhausted
          || (hasPeeked && peekedMatch == null && highlightEndStack.isEmpty());
    }

    /**
     * 获取下一个事件。
     *
     * @param iter 主迭代器（用于访问 injectionCallback、byteOffset、lastHighlight 等）
     * @return 下一个事件，或 null 表示此层本次无事件产出（需继续轮询）。
     */
    HighlightEvent nextEvent(HighlightIter iter) {
      while (true) {
        // 先处理 highlight end 栈
        if (!highlightEndStack.isEmpty()) {
          int nextEnd = highlightEndStack.peek();
          if (!hasPeeked) peekNextCapture();
          int nextCaptureStart = getPeekedCaptureStart();
          if (nextEnd <= nextCaptureStart) {
            iter.emitSource(nextEnd);
            highlightEndStack.pop();
            return HighlightEvent.HighlightEnd.getInstance();
          }
        }

        if (!hasPeeked) peekNextCapture();
        if (peekedMatch == null) {
          // capture 流耗尽
          if (!highlightEndStack.isEmpty()) {
            int end = highlightEndStack.pop();
            iter.emitSource(end);
            return HighlightEvent.HighlightEnd.getInstance();
          }
          // 完全耗尽
          exhausted = true;
          return null;
        }

        // 处理当前 peeked capture
        HighlightEvent ev = processCapture(iter);
        if (ev != null) {
          return ev;
        }
        // processCapture 返回 null 表示处理了 injection/locals 但无 highlight 事件，继续循环
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

    private TSQueryCapture getPeekedCapture() {
      if (!hasPeeked) peekNextCapture();
      if (peekedMatch == null) return null;
      TSQueryCapture[] captures = peekedMatch.getCaptures();
      if (captures == null || peekedCaptureIndex < 0
          || peekedCaptureIndex >= captures.length) {
        return null;
      }
      return captures[peekedCaptureIndex];
    }

    /**
     * 处理当前 peeked capture。
     *
     * @return HighlightStart 事件，或 null（处理了 injection/locals/无 highlight）。
     */
    private HighlightEvent processCapture(HighlightIter iter) {
      TSQueryCapture capture = getPeekedCapture();
      if (capture == null) {
        hasPeeked = false;
        return null;
      }

      TSNode node = capture.getNode();
      int nodeStart = node.getStartByte();
      int nodeEnd = node.getEndByte();
      int captureIndex = capture.getIndex();
      TSQueryMatch currentMatch = peekedMatch;
      int patternIndex = currentMatch.getPatternIndex();

      // A1: 弹出已结束的 scope
      while (scopeStack.size() > 1 && scopeStack.peek().endByte < nodeStart) {
        scopeStack.pop();
      }

      int localsPatternIndex = config.getLocalsPatternIndex();
      int highlightsPatternIndex = config.getHighlightsPatternIndex();

      // injection 段（patternIndex < localsPatternIndex）
      if (patternIndex < localsPatternIndex) {
        // 处理 injection：提取语言名和内容节点
        InjectionForMatchResult ifm = injectionForMatch(
            config, iter.getLanguageName(), config.getQuery(), currentMatch, source);

        // 显式移除此 match（上游: match.remove()），避免其其它 capture 残留
        cursor.removeMatch(currentMatch.getId());
        hasPeeked = false;

        // 如果找到语言和内容节点，创建新层
        if (ifm.languageName != null && ifm.contentNode != null
            && iter.getInjectionCallback() != null) {
          HighlightConfiguration nextConfig = iter.getInjectionCallback().getConfig(ifm.languageName);
          if (nextConfig != null) {
            // 获取当前层的 ranges（取 layers[0] 的 ranges）
            TSRange[] parentRanges = this.ranges;
            TSRange[] intersected = intersectRanges(parentRanges,
                new TSNode[]{ifm.contentNode}, ifm.includeChildren);
            if (intersected.length > 0) {
              // 递归创建新层（共享同一个 parser）
              List<HighlightIterLayer> newLayers = newLayer(
                  source, iter.getLanguageName(), null, this.parser,
                  nextConfig, this.depth + 1, intersected,
                  iter.getInjectionCallback(), allLayers);
              for (HighlightIterLayer l : newLayers) {
                iter.insertLayer(l);
              }
            }
          }
        }
        return null; // injection 不产生 highlight 事件
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
          boolean inherits = config.doesScopeInherit(patternIndex);
          scopeStack.push(new LocalScope(inherits, nodeStart, nodeEnd, new ArrayList<>()));
          definitionHighlightDef = null;
        } else if (captureIndex == localDefIdx) {
          int valueEndByte = 0;
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
          String name = sliceString(nodeStart, nodeEnd);
          referenceHighlight = findLocalDefHighlight(name, nodeStart);
          definitionHighlightDef = null;
        }

        // 消费当前 capture，peek 下一个
        hasPeeked = false;
        capture = getPeekedCapture();
        if (capture == null) {
          return null;
        }
        TSNode nextNode = capture.getNode();
        if (nextNode.getStartByte() != nodeStart || nextNode.getEndByte() != nodeEnd) {
          return null;
        }
        currentMatch = peekedMatch;
        captureIndex = capture.getIndex();
        patternIndex = currentMatch.getPatternIndex();
      }

      // highlight 段
      int highlightIndex = config.getHighlightIndex(captureIndex);

      hasPeeked = false; // 消费第一个 highlight capture
      while (true) {
        TSQueryCapture nextCap = getPeekedCapture();
        if (nextCap == null) break;
        TSNode nextNode = nextCap.getNode();
        if (nextNode.getStartByte() != nodeStart || nextNode.getEndByte() != nodeEnd) break;

        int nextPattern = peekedMatch.getPatternIndex();
        hasPeeked = false;

        if ((definitionHighlightDef != null || referenceHighlight != null)
            && config.isNonLocalVariablePattern(nextPattern)) {
          continue;
        }
        captureIndex = nextCap.getIndex();
        highlightIndex = config.getHighlightIndex(captureIndex);
      }

      int effectiveHighlight = referenceHighlight != null ? referenceHighlight : highlightIndex;

      if (definitionHighlightDef != null && effectiveHighlight >= 0) {
        definitionHighlightDef.highlight = effectiveHighlight;
      }

      // 检查是否被更深 layer 覆盖（上游: last_highlight_range 检查）
      if (nodeStart == iter.getLastHighlightStart()
          && nodeEnd == iter.getLastHighlightEnd()
          && this.depth < iter.getLastHighlightDepth()) {
        return null; // 被更深层覆盖，跳过
      }

      if (effectiveHighlight >= 0) {
        iter.emitSource(nodeStart);
        highlightEndStack.push(nodeEnd);
        iter.setLastHighlight(nodeStart, nodeEnd, this.depth);
        return new HighlightEvent.HighlightStart(effectiveHighlight);
      }
      return null;
    }

    private Integer findLocalDefHighlight(String name, int refStart) {
      for (LocalScope scope : scopeStack) {
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

    private String sliceString(int start, int end) {
      if (start < 0 || end > source.length || start > end) return "";
      return new String(source, start, end - start, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      if (cursor != null) cursor.close();
      if (tree != null) tree.close();
    }
  }

  // ---- injection 处理 ----

  /** injection_for_match 的结果。 */
  private static final class InjectionForMatchResult {
    final String languageName;
    final TSNode contentNode;
    final boolean includeChildren;

    InjectionForMatchResult(String languageName, TSNode contentNode, boolean includeChildren) {
      this.languageName = languageName;
      this.contentNode = contentNode;
      this.includeChildren = includeChildren;
    }
  }

  /**
   * 从 injection match 中提取语言名、内容节点、include_children。
   *
   * <p>与上游 Rust {@code injection_for_match} 一致：
   * <ol>
   *   <li>遍历 captures，匹配 {@code @injection.language} 和 {@code @injection.content}。</li>
   *   <li>语言名优先取 capture 节点文本，其次取 {@code #set! injection.language} 属性。</li>
   *   <li>{@code #set! injection.self} → 语言名为当前层语言名。</li>
   *   <li>{@code #set! injection.parent} → 语言名为父层语言名。</li>
   *   <li>{@code #set! injection.include-children} → include_children=true。</li>
   * </ol>
   */
  private static InjectionForMatchResult injectionForMatch(
      HighlightConfiguration config, String parentName, TSQuery query,
      TSQueryMatch queryMatch, byte[] source) {
    int contentCaptureIndex = config.getInjectionContentCaptureIndex();
    int languageCaptureIndex = config.getInjectionLanguageCaptureIndex();

    String languageName = null;
    TSNode contentNode = null;

    TSQueryCapture[] captures = queryMatch.getCaptures();
    if (captures != null) {
      for (TSQueryCapture capture : captures) {
        int index = capture.getIndex();
        if (index == languageCaptureIndex) {
          TSNode n = capture.getNode();
          int s = n.getStartByte();
          int e = n.getEndByte();
          if (s >= 0 && e <= source.length && s <= e) {
            languageName = new String(source, s, e - s, StandardCharsets.UTF_8);
          }
        } else if (index == contentCaptureIndex) {
          contentNode = capture.getNode();
        }
      }
    }

    boolean includeChildren = false;
    HighlightConfiguration.InjectionProps props = config.getInjectionProps(queryMatch.getPatternIndex());
    if (props != null) {
      // injection.language（仅当 capture 未提供语言名时）
      if (languageName == null && props.language != null) {
        languageName = props.language;
      }
      // injection.self
      if (languageName == null && props.self) {
        languageName = config.getLanguageName();
      }
      // injection.parent
      if (languageName == null && props.parent) {
        languageName = parentName;
      }
      // injection.include-children
      includeChildren = props.includeChildren;
    }

    return new InjectionForMatchResult(languageName, contentNode, includeChildren);
  }

  /**
   * 计算 injection 的解析范围。
   *
   * <p>与上游 Rust {@code intersect_ranges} 一致：
   * <ul>
   *   <li>取 content 节点的范围，与 parent_ranges 求交集。</li>
   *   <li>如果 include_children=false，排除 content 节点的子节点范围
   *       （只保留 content 节点自身的内容，不含嵌套结构）。</li>
   *   <li>结果范围必须在 parent_ranges 内。</li>
   * </ul>
   *
   * @param parentRanges    父层的解析范围
   * @param nodes           injection content 节点列表
   * @param includeChildren 是否包含子节点
   * @return 交集后的范围列表（已排序，不重叠）
   */
  private static TSRange[] intersectRanges(TSRange[] parentRanges, TSNode[] nodes,
      boolean includeChildren) {
    if (nodes.length == 0 || parentRanges.length == 0) {
      return new TSRange[0];
    }

    List<TSRange> result = new ArrayList<>();

    for (TSNode node : nodes) {
      // preceding_range 跟踪当前 content 节点内已处理部分的结束位置
      int precedingEndByte = node.getStartByte();
      TSPoint precedingEndPoint = node.getStartPoint();

      // 收集要排除的范围（子节点范围 + following_range）
      List<int[]> excludedRanges = new ArrayList<>(); // [startByte, endByte]
      List<TSPoint[]> excludedPoints = new ArrayList<>(); // [startPoint, endPoint]

      if (!includeChildren) {
        // 遍历子节点，排除每个子节点的范围
        TSTreeCursor childCursor = node.walk();
        if (childCursor.gotoFirstChild()) {
          do {
            TSNode child = childCursor.getCurrentNode();
            excludedRanges.add(new int[]{child.getStartByte(), child.getEndByte()});
            excludedPoints.add(new TSPoint[]{child.getStartPoint(), child.getEndPoint()});
          } while (childCursor.gotoNextSibling());
        }
        childCursor.close();
      }

      // following_range: content 节点结束后的范围（到文档末尾）
      int followingStartByte = node.getEndByte();
      TSPoint followingStartPoint = node.getEndPoint();

      // 对每个 excluded_range（或最后的 following_range），计算 [precedingEnd, excluded.start) 范围
      for (int i = 0; i <= excludedRanges.size(); i++) {
        int rangeStartByte;
        TSPoint rangeStartPoint;
        int rangeEndByte;
        TSPoint rangeEndPoint;

        if (i < excludedRanges.size()) {
          rangeStartByte = precedingEndByte;
          rangeStartPoint = precedingEndPoint;
          rangeEndByte = excludedRanges.get(i)[0];
          rangeEndPoint = excludedPoints.get(i)[0];
          // 更新 preceding 为 excluded 的结束
          precedingEndByte = excludedRanges.get(i)[1];
          precedingEndPoint = excludedPoints.get(i)[1];
        } else {
          // 最后的 following_range
          rangeStartByte = precedingEndByte;
          rangeStartPoint = precedingEndPoint;
          rangeEndByte = Integer.MAX_VALUE;
          rangeEndPoint = TSPoint.create(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        // 跳过空范围
        if (rangeEndByte <= rangeStartByte) continue;

        // 与 parent_ranges 求交集
        // 简化实现：遍历 parent_ranges 找重叠部分
        // （上游 Rust 用迭代器优化，这里简化为线性搜索）
        for (TSRange pr : parentRanges) {
          // 无重叠
          if (rangeEndByte <= pr.getStartByte() || rangeStartByte >= pr.getEndByte()) {
            continue;
          }
          // 求交集
          int interStartByte = Math.max(rangeStartByte, pr.getStartByte());
          int interEndByte = Math.min(rangeEndByte, pr.getEndByte());
          if (interStartByte >= interEndByte) continue;

          TSPoint interStartPoint = (rangeStartByte > pr.getStartByte())
              ? rangeStartPoint : pr.getStartPoint();
          TSPoint interEndPoint = (rangeEndByte < pr.getEndByte())
              ? rangeEndPoint : pr.getEndPoint();

          result.add(TSRange.create(interStartByte, interEndByte,
              interStartPoint, interEndPoint));
        }
      }
    }

    if (result.isEmpty()) {
      return new TSRange[0];
    }
    // 合并相邻范围（简化：直接返回，tree-sitter 接受重叠范围但建议不重叠）
    result.sort((a, b) -> Integer.compare(a.getStartByte(), b.getStartByte()));
    // 合并相邻
    List<TSRange> merged = new ArrayList<>();
    TSRange current = result.get(0);
    for (int i = 1; i < result.size(); i++) {
      TSRange next = result.get(i);
      if (next.getStartByte() <= current.getEndByte()) {
        // 重叠或相邻，合并
        int endByte = Math.max(current.getEndByte(), next.getEndByte());
        TSPoint endPoint = (next.getEndByte() > current.getEndByte())
            ? next.getEndPoint() : current.getEndPoint();
        current = TSRange.create(current.getStartByte(), endByte,
            current.getStartPoint(), endPoint);
      } else {
        merged.add(current);
        current = next;
      }
    }
    merged.add(current);

    return merged.toArray(new TSRange[0]);
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
    int highlight;  // -1 表示未设置

    LocalDef(String name, int valueEndByte) {
      this.name = name;
      this.valueEndByte = valueEndByte;
      this.highlight = -1;
    }
  }
}
