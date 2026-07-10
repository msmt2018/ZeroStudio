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

/**
 * 查询进度回调。
 *
 * <p>这是 tree-sitter 0.27 {@code TSQueryCursorOptions.progress_callback} 的 Java 绑定。
 * 在通过 {@link TSQueryCursor#execWithOptions(TSQuery, TSNode, TSQueryProgressCallback)}
 * 启动查询后，迭代匹配（{@link TSQueryCursor#nextMatch()} 或 {@link TSQueryCursor#nextCapture(int[])}）
 * 时，tree-sitter 会周期性地调用此回调，报告当前处理到的字节偏移。
 *
 * <p>如果回调返回 {@code true}，查询将被取消，后续的 {@code nextMatch}/{@code nextCapture}
 * 调用将不再返回新的匹配。
 *
 * <p>这是一个函数式接口，可用 lambda 实现：
 * <pre>{@code
 * cursor.execWithOptions(query, rootNode, currentByteOffset -> {
 *   if (Thread.currentThread().isInterrupted()) {
 *     return true; // 请求取消
 *   }
 *   updateProgress(currentByteOffset);
 *   return false; // 继续查询
 * });
 * }</pre>
 *
 * <p><strong>线程说明：</strong> 回调在执行迭代的线程上被调用（即调用
 * {@code nextMatch}/{@code nextCapture} 的线程）。
 *
 * <p><strong>生命周期：</strong> native 层会持有此回调的 GlobalRef，直到：
 * <ul>
 *   <li>再次调用 {@code exec} 或 {@code execWithOptions}（旧的回调被释放）</li>
 *   <li>cursor 被 {@link TSQueryCursor#close() close()}</li>
 * </ul>
 */
@FunctionalInterface
public interface TSQueryProgressCallback {

  /**
   * 由 tree-sitter 周期性调用，报告当前查询进度。
   *
   * @param currentByteOffset 当前处理到的字节偏移。
   * @return {@code true} 请求取消查询；{@code false} 继续查询。
   */
  boolean shouldCancel(int currentByteOffset);
}
