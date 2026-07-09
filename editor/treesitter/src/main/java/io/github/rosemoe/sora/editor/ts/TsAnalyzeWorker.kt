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

/**
 * tree-sitter 高亮分析 Worker。
 *
 * 架构设计（viewport-first + 增量更新）：
 *
 * **doInit（文件打开）—— 两阶段解析：**
 * 1. Phase 1 (viewport-first): 通过 `setIncludedRanges` 只解析前 [TsTextDocument.VIEWPORT_LINES] 行。
 *    无论文件总行数是多少（几百到几十亿行），此阶段始终在毫秒级完成。立即渲染高亮，
 *    用户零等待看到可见区域的语法着色。
 * 2. Phase 2 (full parse): 重置 included ranges，全量解析整个文件（有 30s 超时保护）。
 *    成功后构建 scoped variables + code blocks，替换为完整高亮。超时则保留 Phase 1 的
 *    viewport 高亮（视口外为纯文本），远优于"完全无高亮"。
 *
 * **doMod（文本修改）—— 增量更新：**
 * 1. `reparse(oldTree)` 增量解析——tree-sitter 只重新解析受影响区域，O(变化区域) 而非 O(文件)。
 * 2. 创建新的 `LineSpansGenerator`，但**复用** `TsScopedVariables`（不重建 O(file) 全树 locals 查询）。
 *    scoped variables 内的偏移在编辑后会陈旧，但只影响变量引用（localsReferenceIndices）的解析，
 *    不影响关键字/字符串/类型/注释等直接 capture 的高亮（这些直接来自 tree 查询，始终正确）。
 *    scoped variables 仅在首次（用户暂停输入时）按需构建一次，之后长期复用。
 * 3. 通过 `setStyles(action)` 全量推送——old tree 在 action 回调中于主线程关闭，
 *    与渲染线程同步，避免 use-after-free（TSNativeObject 无 finalizer，必须显式关闭）。
 * 4. code blocks 是 O(file) 全树查询，仅在用户暂停输入时（channel 为空）更新。
 *
 * 每次按键的代价从原来的 O(file)（重建 scoped variables + code blocks）降为
 * O(变化区域 + 可见行数)，与文件总大小无关，无论文件多大都能流畅编辑。
 *
 * @author Akash Yadav
 */
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

  /**
   * 当前 scoped variables。在 [updateStylesFull] 中构建，在 [doMod] 增量路径中复用（不重建）。
   * 修改后可能偏移陈旧，但只影响变量引用解析（localsReferenceIndices），不影响关键字、
   * 字符串、类型等直接 capture 的高亮。这是 O(1) 增量更新 vs O(file) 全量重建的合理权衡。
   */
  private var scopedVariables: TsScopedVariables? = null

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

  /**
   * 文件打开：两阶段解析。
   *
   * Phase 1 (viewport-first): 解析前 [TsTextDocument.VIEWPORT_LINES] 行，毫秒级完成，
   * 立即渲染高亮。无论文件是几百行还是几十亿行，用户都能零等待看到可见区域的语法着色。
   *
   * Phase 2 (full parse): 全量解析整个文件（30s 超时保护）。成功后构建 scoped variables
   * + code blocks，升级为完整高亮。超时则保留 Phase 1 的 viewport 高亮。
   */
  private fun doInit(init: Init) {
    document.requestCancellationAndWaitIfParsing()

    check(!isInitialized) { "'Init' must be the first message to TsAnalyzeWorker" }

    document.doInit(init.data)

    val totalLines = reference.lineCount
    val viewportLines = minOf(TsTextDocument.VIEWPORT_LINES, totalLines)

    // ── Phase 1: Viewport-first parse ──
    // 通过 setIncludedRanges 限制解析范围到前 viewportLines 行。
    // 解析工作量 = O(viewportLines)，与文件总大小无关。
    // endByte/endRow 通过 ContentReference 的 O(1) 索引器计算，不做逐字符扫描。
    val (viewportEndByte, viewportEndRow) = computeViewportEnd(viewportLines, totalLines)
    document.reparseViewport(viewportEndByte, viewportEndRow)

    if (tree?.canAccess() == true && !isDestroyed) {
      // 立即渲染：viewport tree + 无 scoped variables + 无 code blocks。
      // 可见区域获得完整高亮（关键字/字符串/类型等），视口外为纯文本。
      updateStylesViewport()
    }

    isInitialized = true

    // ── Phase 2: Full parse ──
    // 仅当文件超过 viewport 行数时才需要全量解析。
    // 全量解析在工作线程执行（不阻塞 UI），用户可同时编辑（消息排队）。
    if (!isDestroyed && totalLines > viewportLines) {
      val viewportTree = tree // 保存 viewport tree 引用（Phase 2 会覆盖 document.tree）
      document.resetIncludedRanges()
      document.reparse() // 全量解析（30s 超时保护）

      // 无论成功或超时，viewport tree 都不再需要（generator 有自己的 copy）
      viewportTree?.close()

      if (tree != null && !isDestroyed && tree?.canAccess() == true) {
        // 全量解析成功：升级为完整高亮（scoped variables + code blocks + brackets）
        updateStylesFull()
      } else if (tree == null) {
        // 全量解析超时：保留 Phase 1 的 viewport 高亮（generator 持有 viewport tree copy）。
        // document.tree 为 null，doMod 会通过 viewport reparse 恢复。
        log.warn(
            "doInit: full parse timed out ({} lines), staying with viewport highlighting",
            totalLines)
      }
    } else if (tree?.canAccess() == true && !isDestroyed) {
      // 文件不超过 viewport 行数：Phase 1 已解析全文，直接做完整更新
      updateStylesFull()
    }
  }

  /**
   * 通过 `ContentReference.getCharPosition()` 的 O(1) 索引器计算 viewport 末尾的字节偏移和行号。
   *
   * 这取代了原来在 `UTF16String` 上逐字符扫描换行符的 O(charLen) 做法——对百万行文件，
   * 逐字符扫描需要数百万次 JNI `charAt` 调用，而索引器查询是 O(1)。
   *
   * @param viewportLines 期望解析的行数（已 clamp 到 [0, totalLines]）。
   * @param totalLines 文件总行数。
   * @return (endByte, endRow)：endByte 是 viewport 末尾的 UTF-16 字节偏移（charIndex * 2），
   *         endRow 是 viewport 末尾行号（0-based, exclusive）。
   */
  private fun computeViewportEnd(viewportLines: Int, totalLines: Int): Pair<Int, Int> {
    if (viewportLines >= totalLines) {
      // 文件不超过 viewport 行数：解析整个文件。
      // text.length 与 Content 的 char 长度一致（doMod 同步更新两者）。
      return Pair(text.length * 2, totalLines)
    }
    // getCharPosition(viewportLines, 0).index 给出第 viewportLines 行起始的 char 偏移，
    // 即第 (viewportLines - 1) 行末尾（含换行符）的位置——正好是 viewport 的 endByte/2。
    val endCharIndex = reference.getCharPosition(viewportLines, 0).index
    return Pair(endCharIndex * 2, viewportLines)
  }

  /**
   * 文本修改：增量更新。
   *
   * 1. `reparse(oldTree)` 增量解析——tree-sitter 只重新解析受影响区域，O(变化区域)。
   * 2. 创建新 `LineSpansGenerator`（线程安全：generator 引用原子替换），但**复用** scopedVariables。
   * 3. old tree 通过 `setStyles(action)` 在主线程关闭（与渲染线程同步，避免 use-after-free）。
   * 4. code blocks 仅在用户暂停输入时（channel 为空）更新。
   *
   * 每次按键代价：O(变化区域 + 可见行数)，与文件总大小无关。
   */
  private fun doMod(mod: Mod) {

    check(isInitialized) { "'Init' must be the first message to TsAnalyzeWorker" }

    val textMod = mod.data
    val edit = textMod.edit

    val oldTree = tree
    if (oldTree == null) {
      // document.tree 为 null（全量解析超时或尚未解析完成）。
      // 无 old tree 可做增量解析，改为 viewport reparse（毫秒级）。
      document.doMod(textMod)
      (edit as? TreeSitterInputEdit?)?.recycle()

      if (isDestroyed) {
        return
      }

      val totalLines = reference.lineCount
      val viewportLines = minOf(TsTextDocument.VIEWPORT_LINES, totalLines)
      val (endByte, endRow) = computeViewportEnd(viewportLines, totalLines)
      document.reparseViewport(endByte, endRow)

      if (tree?.canAccess() == true && !isDestroyed) {
        updateStylesIncremental()
      }
      return
    }

    // 正常增量路径：tree-sitter 增量解析，O(变化区域)。
    oldTree.edit(edit)

    document.doMod(textMod)

    (edit as? TreeSitterInputEdit?)?.recycle()

    document.requestCancellationAndWaitIfParsing()

    if (isDestroyed) {
      return
    }

    // 必须重置 included ranges：reparseViewport 可能设置了 viewport 范围的 included ranges，
    // 增量解析 reparse(oldTree) 必须覆盖整个文件，否则新增内容在 viewport 外不被解析。
    document.resetIncludedRanges()

    document.reparse(oldTree)

    oldTree.close()

    // 增量解析可能因超时返回 null（极罕见，仅超大文件 + 复杂编辑）。
    // 回退到 viewport reparse，确保用户至少看到可见区域的高亮。
    if (tree == null && !isDestroyed) {
      log.warn("doMod: incremental parse returned null, falling back to viewport parse")
      val totalLines = reference.lineCount
      val viewportLines = minOf(TsTextDocument.VIEWPORT_LINES, totalLines)
      val (endByte, endRow) = computeViewportEnd(viewportLines, totalLines)
      document.reparseViewport(endByte, endRow)
    }

    updateStylesIncremental()
  }

  /**
   * Phase 1 渲染：viewport tree，无 scoped variables，无 code blocks。
   *
   * 创建新的 `LineSpansGenerator`，scopedVariables = null。
   * 可见区域的关键字/字符串/类型等直接 capture 获得高亮；
   * 变量引用（localsReferenceIndices）因 scopedVariables 为 null 跳过解析，使用 fallback 颜色。
   */
  private fun updateStylesViewport() {
    if (isDestroyed || tree?.canAccess() != true) {
      return
    }

    val tree = tree!!
    scopedVariables = null

    val oldTree = (styles.spans as? LineSpansGenerator?)?.tree
    val copied = tree.copy()

    styles.spans =
        LineSpansGenerator(
            copied,
            reference.lineCount,
            reference.reference,
            theme,
            languageSpec,
            null, // scopedVariables = null：viewport 阶段不做变量引用解析
            spanFactory,
        )

    val receiver = stylesReceiver
    if (receiver != null) {
      receiver.setStyles(analyzer, styles) { oldTree?.close() }
    } else {
      oldTree?.close()
    }
  }

  /**
   * 完整渲染：full tree + scoped variables + code blocks + brackets。
   *
   * 构建新的 `TsScopedVariables`（O(file) 全树 locals 查询）和 code blocks（O(file) 全树 blocks 查询）。
   * 仅在文件打开（Phase 2 成功）或文件不超过 viewport 时调用，不在每次按键时调用。
   *
   * 检查 `messageChannel.isNotEmpty()`：如果有待处理消息，跳过重量级工作（下一个 doMod 会处理）。
   */
  private fun updateStylesFull() {
    if (isDestroyed || messageChannel.isNotEmpty() || tree?.canAccess() != true) {
      // analyzer stopped or
      // more message need to be processed
      return
    }

    val tree = tree!!
    scopedVariables =
        TsScopedVariables(
            tree, text, languageSpec, cancelChecker = { !isDestroyed }, matchLimit = 1_000_000)

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

  /**
   * 增量渲染：创建新 `LineSpansGenerator`（原子替换，线程安全），复用 scopedVariables。
   *
   * 1. **复用 scopedVariables**：不重建 O(file) 全树 locals 查询。
   *    若 scopedVariables 尚为 null（Phase 2 被跳过/超时，或仍处于 viewport 阶段），
   *    且用户已暂停输入，则按需构建一次（O(file)，仅在暂停时，不阻塞活跃输入）。
   *    构建后长期复用；编辑后内部偏移会陈旧，但只影响变量引用解析，
   *    不影响关键字/字符串/类型/注释等直接 capture 的高亮（始终来自 tree 查询，正确）。
   * 2. 创建新 generator，old tree 通过 `setStyles(action)` 在主线程关闭
   *    （与渲染线程同步，避免 use-after-free；TSNativeObject 无 finalizer，必须显式关闭）。
   * 3. `TsBracketPairs` 惰性查询（`setByteRange` 只查光标位置），创建代价低。
   * 4. code blocks 是 O(file) 全树查询，仅在用户暂停输入时（`messageChannel.isEmpty()`）更新。
   *
   * 每次按键代价：O(tree.copy + 可见行重录)，与文件总大小无关。
   */
  private fun updateStylesIncremental() {
    if (isDestroyed || tree?.canAccess() != true) {
      return
    }

    val tree = tree!!

    // scopedVariables 首次按需构建：仅在为 null 且用户暂停输入时执行（O(file)）。
    // 构建后长期复用，不在每次按键重建。编辑后偏移陈旧只影响变量引用解析，不影响直接 capture。
    if (scopedVariables == null && messageChannel.isEmpty() && !isDestroyed) {
      scopedVariables =
          TsScopedVariables(
              tree, text, languageSpec, cancelChecker = { !isDestroyed }, matchLimit = 1_000_000)
    }
    val sv = scopedVariables

    val oldTree = (styles.spans as? LineSpansGenerator?)?.tree
    val copied = tree.copy()

    styles.spans =
        LineSpansGenerator(
            copied,
            reference.lineCount,
            reference.reference,
            theme,
            languageSpec,
            sv,
            spanFactory,
        )

    val receiver = stylesReceiver
    if (receiver != null) {
      // setStyles 全量推送：oldTree 通过 action 在主线程关闭（与渲染线程同步，避免 use-after-free）。
      // 渲染层 RenderNodeHolder 在下一次 onDraw 按可见行重新录制，代价 = O(可见行数)，与文件总大小无关。
      receiver.setStyles(analyzer, styles) { oldTree?.close() }
      receiver.updateBracketProvider(analyzer, TsBracketPairs(copied, languageSpec))
    } else {
      oldTree?.close()
    }

    // code blocks 是 O(file) 全树查询，仅在用户暂停输入时（无待处理消息）更新，
    // 避免每次按键都做 O(file) 工作。
    if (messageChannel.isEmpty() && !isDestroyed) {
      val oldBlocks = styles.blocks
      updateCodeBlocks()
      oldBlocks?.also { ObjectAllocator.recycleBlockLines(it) }
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
