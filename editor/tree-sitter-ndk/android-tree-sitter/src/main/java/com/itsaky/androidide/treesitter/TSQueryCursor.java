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
import com.itsaky.androidide.treesitter.predicate.TSPredicateHandler;
import com.itsaky.androidide.treesitter.predicate.TSPredicateHandler.PredicateStep;
import com.itsaky.androidide.treesitter.predicate.TSPredicateHandler.Result;
import com.itsaky.androidide.treesitter.util.TSObjectFactoryProvider;
import dalvik.annotation.optimization.FastNative;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * @author Akash Yadav
 */
public class TSQueryCursor extends TSNativeObject implements Iterable<TSQueryMatch> {

  protected boolean isExecuted = false;
  private boolean allowChangedNodes = false;
  protected TSNode targetNode = null;
  protected TSQuery execQuery = null;
  protected final Set<TSPredicateHandler> predicateHandlers = new HashSet<>();

  protected TSQueryCursor() {
    this(Native.newCursor());
  }

  protected TSQueryCursor(long pointer) {
    super(pointer);
  }

  public static TSQueryCursor create(long pointer) {
    return TSObjectFactoryProvider.getFactory().createQueryCursor(pointer);
  }

  public static TSQueryCursor create() {
    return create(Native.newCursor());
  }

  /**
   * Whether the cursor should accept {@link TSNode}s whose {@link TSNode#hasChanges()} returns
   * true. This is set to <code>false</code> by default. Setting it to <code>true</code> is risky,
   * especially in cases when the node or the tree is accessed/edited from multiple threads. This
   * could result in
   * <code>SEGV_MAPERR</code> issues.
   *
   * @param allowChangedNodes Whether changed nodes should be allowed.
   */
  public void setAllowChangedNodes(boolean allowChangedNodes) {
    this.allowChangedNodes = allowChangedNodes;
  }

  /**
   * Whether the cursor accepts {@link TSNode} whose {@link TSNode#hasChanges()} returns true.
   *
   * @return Whether the cursor accepts {@link TSNode} whose {@link TSNode#hasChanges()} returns
   * true.
   * @see #setAllowChangedNodes(boolean)
   */
  public boolean isAllowChangedNodes() {
    return allowChangedNodes;
  }

  /**
   * Add the given predicate handler. Predicate handlers are applied to every query match while
   * iterating.
   *
   * @param handler The predicate handler to add.
   */
  public void addPredicateHandler(TSPredicateHandler handler) {
    if (handler == null) {
      return;
    }

    predicateHandlers.add(handler);
  }

  /**
   * Remove the given predicate handler.
   *
   * @param handler The predicate handler to remove.
   */
  public void removePredicateHandler(TSPredicateHandler handler) {
    if (handler == null) {
      return;
    }

    predicateHandlers.remove(handler);
  }

  /**
   * Start running the given query on the given node.
   */
  public void exec(TSQuery query, TSNode node) {
    Objects.requireNonNull(node, "TSNode cannot be null");
    checkAccess();
    if (query == null || !query.canAccess()) {
      throw new IllegalArgumentException("Cannot execute invalid query");
    }
    if (!node.canAccess() || !node.getTree().canAccess() ||
      (!isAllowChangedNodes() && node.hasChanges())) {
      String msg = "Cannot execute query on invalid node. node=" + node + " node.canAccess=" +
        node.canAccess() + " node.tree.canAccess=" + node.getTree().canAccess() +
        " node.hasChanges=" + node.hasChanges() + " isAllowChangedNodes=" + isAllowChangedNodes();

      throw new IllegalArgumentException(msg);
    }

    Native.exec(getNativeObject(), query.getNativeObject(), node);

    isExecuted = true;
    targetNode = node;
    execQuery = query;
  }

  /**
   * Start running the given query on the given node, with a progress callback for cancellation.
   *
   * <p>This is the Java binding of tree-sitter 0.27 {@code ts_query_cursor_exec_with_options}.
   * The progress callback is invoked periodically during iteration (via {@link #nextMatch()} or
   * {@link #nextCapture(int[])}), reporting the current byte offset. If the callback returns
   * {@code true}, the query is cancelled.
   *
   * <p>If {@code progressCallback} is {@code null}, this behaves identically to
   * {@link #exec(TSQuery, TSNode)}.
   *
   * @param query            the query to execute.
   * @param node             the root node to query on.
   * @param progressCallback the progress callback, or {@code null} for no callback.
   */
  public void execWithOptions(TSQuery query, TSNode node, TSQueryProgressCallback progressCallback) {
    Objects.requireNonNull(node, "TSNode cannot be null");
    checkAccess();
    if (query == null || !query.canAccess()) {
      throw new IllegalArgumentException("Cannot execute invalid query");
    }
    if (!node.canAccess() || !node.getTree().canAccess() ||
      (!isAllowChangedNodes() && node.hasChanges())) {
      String msg = "Cannot execute query on invalid node. node=" + node + " node.canAccess=" +
        node.canAccess() + " node.tree.canAccess=" + node.getTree().canAccess() +
        " node.hasChanges=" + node.hasChanges() + " isAllowChangedNodes=" + isAllowChangedNodes();

      throw new IllegalArgumentException(msg);
    }

    Native.execWithOptions(getNativeObject(), query.getNativeObject(), node, progressCallback);

    isExecuted = true;
    targetNode = node;
    execQuery = query;
  }

  /**
   * @noinspection NullableProblems
   */
  @Override
  public Iterator<TSQueryMatch> iterator() {

    return new Iterator<>() {

      private TSQueryMatch nextMatch = null;

      @Override
      public boolean hasNext() {
        boolean shouldFetchNextMatch = canAccess() // query cursor must be accessible
          && isExecuted // at least one query should have been executed
          && targetNode != null // query should have been executed on a non-null node

          // the target node's tree should not have been changed since query execution
          // if the user has explicitly opted to allow changed nodes, allow those changes
          && (isAllowChangedNodes() || !targetNode.hasChanges());
        nextMatch = shouldFetchNextMatch ? nextMatch() : null;
        return nextMatch != null;
      }

      @Override
      public TSQueryMatch next() {
        if (nextMatch == null) {
          throw new NoSuchElementException();
        }

        return nextMatch;
      }
    };
  }

  /**
   * Whether the maximum number of in-progress matches allowed by this query cursor has been
   * exceeded or not.
   *
   * <p>Query cursors have an optional maximum capacity for storing lists of in-progress captures.
   * If this capacity is exceeded, then the earliest-starting match will silently be dropped to make
   * room for further matches. This maximum capacity is optional — by default, query cursors allow
   * any number of pending matches, dynamically allocating new space for them as needed as the query
   * is executed.
   */
  public boolean didExceedMatchLimit() {
    checkAccess();
    return Native.exceededMatchLimit(getNativeObject());
  }

  /**
   * Get the maximum number of in-progress matches allowed by this query * cursor.
   *
   * @return The match limit.
   * @see #didExceedMatchLimit()
   */
  public int getMatchLimit() {
    checkAccess();
    return Native.getMatchLimit(getNativeObject());
  }

  /**
   * Set the maximum number of in-progress matches allowed by this query * cursor.
   *
   * @param newLimit The new match limit.
   * @see #didExceedMatchLimit()
   */
  public void setMatchLimit(int newLimit) {
    checkAccess();
    Native.setMatchLimit(getNativeObject(), newLimit);
  }

  /**
   * Set the range of bytes in which a query will be executed.
   *
   * <p>This is the Java binding of tree-sitter 0.27 {@code ts_query_cursor_set_byte_range}.
   *
   * @param start 起始字节偏移（包含）。
   * @param end   结束字节偏移（不包含）。
   * @return 范围是否合法（{@code start <= end} 时为 {@code true}）。
   */
  public boolean setByteRange(int start, int end) {
    checkAccess();
    return Native.setByteRange(getNativeObject(), start, end);
  }

  /**
   * Set the range of points in which a query will be executed.
   *
   * <p>This is the Java binding of tree-sitter 0.27 {@code ts_query_cursor_set_point_range}.
   *
   * @param start 起始 point（包含）。
   * @param end   结束 point（不包含）。
   * @return 范围是否合法。
   */
  public boolean setPointRange(TSPoint start, TSPoint end) {
    checkAccess();
    return Native.setPointRange(getNativeObject(), start, end);
  }

  public TSQueryMatch nextMatch() {
    checkAccess();
    checkExecuted("nextMatch");
    final var match = Native.nextMatch(getNativeObject());
    if (match != null) {
      applyPredicates(match);
    }
    return match;
  }

  private void applyPredicates(TSQueryMatch match) {
    if (match == null || execQuery == null) {
      return;
    }

    if (predicateHandlers.isEmpty()) {
      return;
    }

    final var predicates = execQuery.getPredicatesForPattern(match.getPatternIndex());
    final var steps = new ArrayList<PredicateStep>(predicates.length);

    for (final var predicate : predicates) {
      final PredicateStep step;
      switch (predicate.getType()) {
        case Capture:
          step = new PredicateStep(predicate.getType(),
            execQuery.getCaptureNameForId(predicate.getValueId()));
          break;
        case String:
          step = new PredicateStep(predicate.getType(),
            execQuery.getStringValueForId(predicate.getValueId()));
          break;
        default:
          step = new PredicateStep(predicate.getType(), "");
          break;
      }

      steps.add(step);
    }

    for (final var handler : predicateHandlers) {
      if (handler.handle(execQuery, match, steps) == Result.OK) {
        break;
      }
    }
  }

  public void removeMatch(int id) {
    checkAccess();
    checkExecuted("removeMatch");
    Native.removeMatch(getNativeObject(), id);
  }

  /**
   * 获取下一个 capture（用于高亮）。这是 v15 新增的 API。
   *
   * <p>调用方可通过传入长度为 1 的 {@code captureIndexOut} 数组接收 capture 索引。
   *
   * @param captureIndexOut 长度为 1 的 int 数组，用于输出 capture_index；可为 {@code null}。
   * @return 下一个 {@link TSQueryMatch}，若没有更多 capture 则返回 {@code null}。
   */
  public TSQueryMatch nextCapture(int[] captureIndexOut) {
    checkAccess();
    checkExecuted("nextCapture");
    return Native.nextCapture(getNativeObject(), captureIndexOut);
  }

  /**
   * 设置全包含式 byte 范围。只有完全落在 {@code [startByte, endByte)} 内的节点才会被查询。
   * 这是 v15 新增的 API。
   *
   * @param startByte 起始字节偏移（包含）。
   * @param endByte 结束字节偏移（不包含）。
   * @return 范围是否合法。
   */
  public boolean setContainingByteRange(int startByte, int endByte) {
    checkAccess();
    return Native.setContainingByteRange(getNativeObject(), startByte, endByte);
  }

  /**
   * 设置全包含式 point 范围。只有完全落在 {@code [start, end)} 内的节点才会被查询。
   * 这是 v15 新增的 API。
   *
   * @param start 起始 point（包含）。
   * @param end 结束 point（不包含）。
   * @return 范围是否合法。
   */
  public boolean setContainingPointRange(TSPoint start, TSPoint end) {
    checkAccess();
    return Native.setContainingPointRange(getNativeObject(), start, end);
  }

  /**
   * 限制 pattern 根节点搜索的起始深度。这是 v15 新增的 API。
   *
   * @param maxStartDepth 最大起始深度。
   */
  public void setMaxStartDepth(int maxStartDepth) {
    checkAccess();
    Native.setMaxStartDepth(getNativeObject(), maxStartDepth);
  }

  @Override
  public void close() {
    isExecuted = false;
    targetNode = null;
    // 释放 progress callback GlobalRef（如果在 execWithOptions 中设置过）
    if (canAccess()) {
      Native.releaseProgressCallback(getNativeObject());
    }
    super.close();
  }

  @Override
  protected void closeNativeObj() {
    Native.delete(getNativeObject());
  }

  protected void checkExecuted(String name) {
    if (!isExecuted) {
      throw new IllegalStateException(
        "TSQueryCursor.exec() must be called before accessing '" + name + "'");
    }
  }

  @GenerateNativeHeaders(fileName = "query_cursor")
  private static class Native {

    @FastNative
    static native long newCursor();

    @FastNative
    static native void delete(long cursor);

    @FastNative
    static native void exec(long cursor, long query, TSNode node);

    // tree-sitter 0.27 API：带 progress_callback 的 exec（用于查询取消）
    @FastNative
    static native void execWithOptions(long cursor, long query, TSNode node,
        TSQueryProgressCallback progressCallback);

    // 释放 execWithOptions 设置的 progress callback GlobalRef
    @FastNative
    static native void releaseProgressCallback(long cursor);

    @FastNative
    static native boolean exceededMatchLimit(long cursor);

    @FastNative
    static native void setMatchLimit(long cursor, int newLimit);

    @FastNative
    static native int getMatchLimit(long cursor);

    @FastNative
    static native boolean setByteRange(long cursor, int start, int end);

    @FastNative
    static native boolean setPointRange(long cursor, TSPoint start, TSPoint end);

    @FastNative
    static native TSQueryMatch nextMatch(long cursor);

    // v15 新增 API：获取下一个 capture（用于高亮）
    @FastNative
    static native TSQueryMatch nextCapture(long cursor, int[] captureIndexOut);

    // v15 新增 API：设置全包含式 byte 范围
    @FastNative
    static native boolean setContainingByteRange(long cursor, int startByte, int endByte);

    // v15 新增 API：设置全包含式 point 范围
    @FastNative
    static native boolean setContainingPointRange(long cursor, TSPoint start, TSPoint end);

    // v15 新增 API：限制 pattern 根节点搜索起始深度
    @FastNative
    static native void setMaxStartDepth(long cursor, int maxStartDepth);

    @FastNative
    static native void removeMatch(long cursor, int id);
  }
}
