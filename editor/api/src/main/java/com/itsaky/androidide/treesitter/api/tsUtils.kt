/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.treesitter.api

import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.TSQuery
import com.itsaky.androidide.treesitter.TSQueryCursor
import com.itsaky.androidide.treesitter.TSQueryMatch
import com.itsaky.androidide.treesitter.TSQueryProgressCallback
import com.itsaky.androidide.treesitter.TSTree
import org.slf4j.LoggerFactory

@PublishedApi internal val log = LoggerFactory.getLogger("TsUtilsKt")

/**
 * Safely create a query cursor and execute the given [TSQuery]. The given [action] will be called
 * for every [TSQueryMatch]. If the tree, cursor, tree or the tree's node is closed or edited while
 * the cursor is querying the node, the [onClosedOrEdited] will be called.
 *
 * This method does not close the [TSQueryCursor] instance.
 */
inline fun <ResultT> TSQueryCursor.safeExecQueryCursor(
    query: TSQuery,
    tree: TSTree?,
    recycleNodeAfterUse: Boolean = true,
    crossinline matchCondition: (TSQueryMatch?) -> Boolean = { true },
    crossinline whileTrue: (TSQueryMatch?) -> Boolean = { true },
    crossinline onClosedOrEdited: () -> Unit = {},
    noinline cancelChecker: (() -> Boolean)? = null,
    matchLimit: Int = -1,
    crossinline onExceededMatchLimit: () -> Unit = {},
    debugName: String = "",
    debugLogging: Boolean = false,
    crossinline action: (TSQueryMatch) -> ResultT,
): ResultT? {

  if (tree == null || !tree.canAccess()) {
    if (debugLogging) {
      log.debug(
          "$debugName: Cannot execute query, tree is null or not accessible",
          "tree=$tree",
          "tree.canAccess=${tree?.canAccess()}",
      )
    }
    return null
  }

  val rootNode = tree.rootNode
  if (!rootNode.canAccess() || rootNode.hasChanges()) {
    if (debugLogging) {
      log.debug(
          "$debugName, Cannot execute query, tree's root node is not accessible or has been edited",
          "rootNode=$rootNode",
          "rootNode.canAccess=${rootNode.canAccess()}",
          "rootNode.hasChanges=${rootNode.canAccess() && rootNode.hasChanges()}",
      )
    }
    return null
  }

  return safeExecQueryCursor(
      query = query,
      node = rootNode,
      recycleNodeAfterUse = recycleNodeAfterUse,
      matchCondition = {
        val result = tree.canAccess() && matchCondition(it)
        if (!result && debugLogging) {
          log.debug("$debugName: tree.canAccess=${tree.canAccess()}")
        }
        result
      },
      whileTrue = whileTrue,
      onClosedOrEdited = onClosedOrEdited,
      cancelChecker = cancelChecker,
      matchLimit = matchLimit,
      onExceededMatchLimit = onExceededMatchLimit,
      debugName = debugName,
      debugLogging = debugLogging,
      action = action,
  )
}

/**
 * Safely create a query cursor and execute the given [TSQuery]. The given [action] will be called
 * for every [TSQueryMatch]. If the tree, cursor or the node is closed or edited while the cursor is
 * querying the node, the [onClosedOrEdited] will be called.
 *
 * This method does not close the [TSQueryCursor] instance.
 */
inline fun <ResultT> TSQueryCursor.safeExecQueryCursor(
    query: TSQuery,
    node: TSNode,
    recycleNodeAfterUse: Boolean = true,
    crossinline matchCondition: (TSQueryMatch?) -> Boolean = { true },
    crossinline whileTrue: (TSQueryMatch?) -> Boolean = { true },
    crossinline onClosedOrEdited: () -> Unit = {},
    noinline cancelChecker: (() -> Boolean)? = null,
    matchLimit: Int = -1,
    crossinline onExceededMatchLimit: () -> Unit = {},
    debugName: String = "",
    debugLogging: Boolean = false,
    crossinline action: (TSQueryMatch) -> ResultT,
): ResultT? {

  return doSafeExecQueryCursor(
      query = query,
      node = node,
      recycleNodeAfterUse = recycleNodeAfterUse,
      matchCondition = { match ->
        match != null &&
            canAccess() &&
            node.canAccess() &&
            !node.hasChanges() &&
            matchCondition(match)
      },
      whileTrue = whileTrue,
      onClosedOrEdited = onClosedOrEdited,
      cancelChecker = cancelChecker,
      matchLimit = matchLimit,
      onExceededMatchLimit = onExceededMatchLimit,
      debugName = debugName,
      debugLogging = debugLogging,
      action = action,
  )
}

@PublishedApi
internal inline fun <ResultT> TSQueryCursor.doSafeExecQueryCursor(
    query: TSQuery,
    node: TSNode,
    recycleNodeAfterUse: Boolean = true,
    crossinline matchCondition: (TSQueryMatch?) -> Boolean,
    crossinline whileTrue: (TSQueryMatch?) -> Boolean,
    crossinline onClosedOrEdited: () -> Unit,
    noinline cancelChecker: (() -> Boolean)? = null,
    matchLimit: Int = -1,
    crossinline onExceededMatchLimit: () -> Unit = {},
    debugName: String = "",
    debugLogging: Boolean = false,
    crossinline action: (TSQueryMatch) -> ResultT,
): ResultT? {

  if (!query.canAccess()) {
    if (debugLogging) {
      log.debug("$debugName: Cannot execute query, query is not accessible")
    }
    return null
  }

  if (!node.canAccess() || node.hasChanges()) {
    if (debugLogging) {
      log.debug(
          "$debugName: Cannot execute query, node is not accessible or has been edited",
          "node.canAccess=${node.canAccess()}",
          "node.hasChanges=${node.canAccess() && node.hasChanges()}",
      )
    }
    return null
  }

  // 升级：当调用方提供 cancelChecker 时，使用 tree-sitter 0.27 的 execWithOptions
  // (ts_query_cursor_exec_with_options) 注册进度回调，使单次 nextMatch() 内部也可被取消。
  // 这解决了全树查询（如代码块分析）在超大文件上单次迭代无法中断导致的 ANR 风险。
  // cancelChecker 为 null 时退化为普通 exec，保持原有行为与性能（避免逐行高亮路径的 JNI GlobalRef 开销）。
  if (cancelChecker != null) {
    execWithOptions(query, node, TSQueryProgressCallback { cancelChecker() })
  } else {
    exec(query, node)
  }
  // 升级：接入 tree-sitter 0.27 的 setMatchLimit，为全树查询设置 pending match 上限，
  // 防止病态/超大文件导致内存无界增长。配合 didExceedMatchLimit 在循环后诊断是否超限。
  // matchLimit <= 0 时不设置（保持默认无限制），逐行高亮路径不传入。
  if (matchLimit > 0) {
    setMatchLimit(matchLimit)
  }
  var match = nextMatch()
  while (matchCondition(match) && whileTrue(match)) {

    val result = action(match)

    if (!matchCondition(match)) {
      if (debugLogging) {
        log.debug(
            "$debugName: Cannot proceed with query operation.",
            "cursor.canAccess=${canAccess()}",
            "query.canAccess=${query.canAccess()}",
            "node.canAccess=${node.canAccess()}",
            "node.hasChanges=${node.canAccess() && node.hasChanges()}",
        )
      }
      onClosedOrEdited()
      break
    }

    (match as? TreeSitterQueryMatch?)?.recycle()

    // if the action does not produce any output and simply returns Unit (void)
    // then ignore the result and continue with the capture
    if (result != Unit && result != null) {
      return result
    }

    match = nextMatch()
  }

  // 升级：循环结束后检查是否因 match limit 超限而静默丢弃了 match，通知调用方。
  if (matchLimit > 0 && didExceedMatchLimit()) {
    onExceededMatchLimit()
  }

  if (recycleNodeAfterUse && node is TreeSitterNode && !node.isRecycled) {
    node.recycle()
  }

  return null
}
