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

package io.github.rosemoe.sora.editor.ts

import com.itsaky.androidide.treesitter.TSInputEdit
import com.itsaky.androidide.treesitter.TSQueryCursor
import com.itsaky.androidide.treesitter.TSTree
import com.itsaky.androidide.treesitter.api.TreeSitterInputEdit
import com.itsaky.androidide.treesitter.api.TreeSitterQueryCapture
import com.itsaky.androidide.treesitter.api.safeExecQueryCursor
import com.itsaky.androidide.treesitter.string.UTF16String
import io.github.rosemoe.sora.data.ObjectAllocator
import io.github.rosemoe.sora.editor.ts.spans.TsSpanFactory
import io.github.rosemoe.sora.lang.analysis.StyleReceiver
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.text.ContentReference
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/** @author Akash Yadav */
class TsAnalyzeWorker(
    private val analyzer: TsAnalyzeManager,
    private val languageSpec: TsLanguageSpec,
    @Volatile internal var theme: TsTheme,
    private val styles: Styles,
    private val reference: ContentReference,
    private val spanFactory: TsSpanFactory,
) {

  companion object {

    private val log = LoggerFactory.getLogger(TsAnalyzeWorker::class.java)
  }

  var stylesReceiver: StyleReceiver? = null

  @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
  private val analyzerContext = newSingleThreadContext("TsAnalyzeWorkerContext")

  private val analyzerScope = CoroutineScope(analyzerContext)
  private val messageChannel = LinkedBlockingQueue<Message<*>>()
  private var analyzerJob: Job? = null

  private var isInitialized = false
  // volatile：确保 stop()/destroy() 在其他线程的修改对工作线程可见，
  // 避免 doMod/updateStyles 在 document.close() 后继续访问已释放的 native 资源
  @Volatile private var isDestroyed = false

  val document = TsTextDocument(languageSpec.language)

  internal val tree: TSTree?
    get() = document.tree

  internal val text: UTF16String
    get() = document.text

  internal fun init(init: Init) {
    if (isDestroyed) {
      log.warn("Received Init after TsAnalyzeWorker has been destroyed. Ignoring...")
      return
    }

    messageChannel.offer(init)
  }

  internal fun onMod(mod: Mod) {
    if (isDestroyed) {
      log.warn("Received Mod after TsAnalyzeWorker has been destroyed. Ignoring...")
      return
    }

    messageChannel.offer(mod)
  }

  fun stop() {
    log.debug("Stopping TsAnalyzeWorker...")
    isDestroyed = true

    // 非阻塞取消解析：仅设置 native 取消标志，不等待解析结束。
    // 原代码调用 requestCancellationAndWaitIfParsing() 在主线程同步等待，
    // 最坏可阻塞 5 秒（PARSE_TIMEOUT_MICROS），直接导致 ANR。
    document.requestCancellationAsyncIfParsing()

    messageChannel.clear()

    // 投递毒丸消息唤醒工作线程的 LinkedBlockingQueue.take()。
    // take() 是 JDK 阻塞调用，协程取消无法中断它；不投递消息则工作线程
    // 永远阻塞在 take()，导致 runBlocking{join()} 永久死锁 → ANR。
    messageChannel.offer(Init(TextInit("", -1)))

    analyzerJob?.cancel(CancellationException("Requested to be stopped"))
    analyzerScope.cancel(CancellationException("Requested to be stopped"))

    // 关闭上下文（关闭底层单线程 ExecutorService，中断工作线程）。
    // 必须在 join 之前关闭：线程中断使 take() 抛出 InterruptedException，
    // 工作线程协程随即失败结束，join() 才能快速返回。
    analyzerContext.close()

    // 等待工作线程结束，确保不会在 document.close() 后继续访问 native 资源
    // (use-after-free)。毒丸 + 线程中断使工作线程快速退出；
    // withTimeout 作为安全网，防止意外情况下永久阻塞主线程。
    if (analyzerJob != null) {
      runBlocking {
        try {
          withTimeout(500) {
            analyzerJob?.join()
          }
        } catch (e: Exception) {
          // 超时或取消，忽略：工作线程会被 analyzerContext.close() 的中断最终终止
        }
      }
    }

    document.close()
  }

  fun start() {
    check(!isDestroyed) { "TsAnalyeWorker has already been destroyed" }

    analyzerJob =
        analyzerScope
            .launch {
              while (!isDestroyed && isActive) {
                processNextMessage()
              }
            }
            .also { job ->
              job.invokeOnCompletion { error ->
                if (error != null && error !is CancellationException) {
                  log.error("Analyzer job failed", error)
                } else {
                  log.info("Analyzer job completed")
                }
              }
            }
  }

  private fun processNextMessage() {
    val message = messageChannel.take()
    if (isDestroyed) {
      return
    }

    try {
      when (message) {
        is Init -> doInit(message)
        is Mod -> doMod(message)
      }
    } catch (err: Throwable) {
      val langName = languageSpec.language.name
      val msgType = message.javaClass.simpleName
      val msgTypeSuffix =
          if (message is Mod) {
            "[start=${message.data.start}, end=${message.data.end}, type=${if (message.data.changedText == null) "delete" else "insert"}]"
          } else ""
      val pendingMsgs = messageChannel.size
      log.error(
          "AnalyzeWorker[lang={}, message={}{}], pendingMsgs={}] crashed",
          langName,
          msgType,
          msgTypeSuffix,
          pendingMsgs,
          err,
      )
    }
  }

  private fun doInit(init: Init) {
    document.requestCancellationAndWaitIfParsing()

    check(!isInitialized) { "'Init' must be the first message to TsAnalyzeWorker" }

    document.doInit(init.data)
    document.reparse()

    // 大文件兜底：首次解析因 30s 超时返回 null 时，使用 timeout=0（无超时）重试一次。
    // 这确保超大文件（几十万行+）最终能被完整解析并获得高亮，而不是静默放弃。
    // 30s 上限仍作为快速失败保护：常规文件不会触发重试，避免无谓等待。
    // stop() 可通过 requestCancellationAsync() 中断重试中的 native 解析。
    if (tree == null && !isDestroyed) {
      log.warn("doInit: first parse returned null (timeout?), retrying with no timeout for large file")
      document.parser.setTimeout(0L)
      try {
        document.reparse()
      } finally {
        document.parser.setTimeout(TsTextDocument.PARSE_TIMEOUT_MICROS)
      }
    }

    updateStyles()

    isInitialized = true
  }

  private fun doMod(mod: Mod) {

    check(isInitialized) { "'Init' must be the first message to TsAnalyzeWorker" }

    val textMod = mod.data
    val edit = textMod.edit

    val oldTree = tree
    if (oldTree == null) {
      // 之前的全量解析失败（超时后重试也失败），无法做增量解析。
      // 直接应用文本修改后重新全量解析，让编辑器有机会恢复高亮。
      document.doMod(textMod)
      (edit as? TreeSitterInputEdit?)?.recycle()

      if (isDestroyed) {
        return
      }

      document.reparse()
      updateStyles()
      return
    }

    oldTree.edit(edit)

    document.doMod(textMod)

    (edit as? TreeSitterInputEdit?)?.recycle()

    document.requestCancellationAndWaitIfParsing()

    if (isDestroyed) {
      return
    }

    document.reparse(oldTree)

    oldTree.close()
    updateStyles()
  }

  private fun updateStyles() {
    if (isDestroyed || messageChannel.isNotEmpty() || tree?.canAccess() != true) {
      // analyzer stopped or
      // more message need to be processed
      return
    }

    val tree = tree!!
    val scopedVariables =
        TsScopedVariables(tree, text, languageSpec, cancelChecker = { !isDestroyed }, matchLimit = 1_000_000)
    val oldTree = (styles.spans as? LineSpansGenerator?)?.tree
    val copied = tree.copy()

    styles.spans =
        LineSpansGenerator(
            copied,
            reference.lineCount,
            reference.reference,
            theme,
            languageSpec,
            scopedVariables,
            spanFactory,
        )

    val oldBlocks = styles.blocks
    updateCodeBlocks()
    oldBlocks?.also { ObjectAllocator.recycleBlockLines(it) }

    // 修复：原实现 stylesReceiver?.setStyles(...) { oldTree?.close() } 在 stylesReceiver 为 null
    // 时回调不会执行，导致旧 tree（已被新 styles.spans 替换、不再被持有）泄漏 native 资源。
    // 显式处理 null 分支，确保 oldTree 在任意情况下都被关闭。
    val receiver = stylesReceiver
    if (receiver != null) {
      receiver.setStyles(analyzer, styles) { oldTree?.close() }
      receiver.updateBracketProvider(analyzer, TsBracketPairs(copied, languageSpec))
    } else {
      oldTree?.close()
    }
  }

  private fun updateCodeBlocks() {
    if (
        languageSpec.blocksQuery.patternCount == 0 ||
            !languageSpec.blocksQuery.canAccess() ||
            tree?.canAccess() != true
    ) {
      return
    }

    val blocks = mutableListOf<CodeBlock>()
    // 一次性获取所有 capture 名称并缓存，避免在循环内对每个 capture 都做 JNI 调用
    val captureNames = languageSpec.blocksQuery.getCaptureNames()
    TSQueryCursor.create().use { cursor ->
      cursor.safeExecQueryCursor(
          query = languageSpec.blocksQuery,
          tree = tree,
          recycleNodeAfterUse = true,
          matchCondition = { !isDestroyed },
          onClosedOrEdited = { blocks.clear() },
          // 升级：注册 0.27 进度回调，使单次 nextMatch() 内部也能响应 isDestroyed 取消，
          // 避免超大文件上全树 blocks 查询单次迭代耗时过长导致 ANR。
          cancelChecker = { !isDestroyed },
          // 升级：接入 0.27 setMatchLimit，为全树 blocks 查询设置 pending match 上限，
          // 防止病态文件内存无界增长；超限时告警（代码块可能不全）。
          // 与 TsScopedVariables 保持一致的 1_000_000 上限，确保大文件（数万行+）
          // 的代码块折叠不会被默认限制静默丢弃。
          matchLimit = 1_000_000,
          onExceededMatchLimit = {
            log.warn("updateCodeBlocks: blocks query exceeded match limit, code blocks may be incomplete")
          },
          debugName = "TsAnalyzeManager.updateCodeBlocks()",
      ) { match ->
        if (!languageSpec.blocksPredicator.doPredicate(languageSpec.predicates, text, match)) {
          return@safeExecQueryCursor
        }

        match.captures.forEach { capture ->
          val block = ObjectAllocator.obtainBlockLine()
          var node = capture.node
          val start = node.startPoint

          block.startLine = start.row
          block.startColumn = start.column / 2

          val end =
              if (captureNames[capture.index].endsWith(".marked")) {
                // Goto last terminal element
                while (node.childCount > 0) {
                  node = node.getChild(node.childCount - 1)
                }
                node.startPoint
              } else {
                node.endPoint
              }
          block.endLine = end.row
          block.endColumn = end.column / 2
          if (block.endLine - block.startLine > 1) {
            blocks.add(block)
          }

          (capture as? TreeSitterQueryCapture?)?.recycle()
        }
      }
    }

    val distinct = blocks.asSequence().distinct().toMutableList()
    styles.blocks = distinct
    styles.finishBuilding()
  }
}

internal interface Message<T> {

  val data: T
}

internal data class Init(override val data: TextInit) : Message<TextInit>

internal data class Mod(override val data: TextMod) : Message<TextMod>

internal data class TextInit(val text: String, val contentVersion: Long)

internal data class TextMod(
    val start: Int,
    val end: Int,
    val edit: TSInputEdit,
    val changedText: String?,
    val contentVersion: Long,
)
