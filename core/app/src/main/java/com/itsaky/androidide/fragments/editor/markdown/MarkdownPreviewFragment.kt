package com.itsaky.androidide.fragments.editor.markdown

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import com.itsaky.androidide.ui.SymbolInputVisibilityManager
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Markdown 预览 fragment。
 *
 * 直接使用 `dev.jeziellago.compose.markdowntext.MarkdownText`（已被
 * `core/git` 模块 vendored 进来，puppygit 长期使用），不复用 WebView / nanohttpd /
 * 任何 bundled 资源文件。
 *
 * 公开接口（`newInstance(filePath)` / `newInstanceWithContent(content)` /
 * `SUPPORTED_EXTENSIONS` / `ARG_MARKDOWN_CONTENT`）与重构前一致，调用方
 * （`MarkdownPreviewAction` / `EditorFragmentTabManager`）零改动。
 *
 * @author android_zero
 */
class MarkdownPreviewFragment : Fragment() {

    companion object {
        /** 嵌入式 markdown 内容 key（inlineContent 模式用）。 */
        const val ARG_MARKDOWN_CONTENT = "markdown_content"

        /** 支持的 markdown 文件后缀。 */
        val SUPPORTED_EXTENSIONS = setOf(
            "md", "mdr", "markdown", "mdown", "mkd", "mkdn", "mdoc", "mdtext", "mdx"
        )

        /** 工厂：按文件路径创建 fragment（File 模式）。 */
        @JvmStatic
        fun newInstance(filePath: String): MarkdownPreviewFragment {
            return MarkdownPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(EditorFragmentTabManager.ARG_FILE_PATH, filePath)
                }
            }
        }

        /** 工厂：按 inline content 创建 fragment（无文件模式）。 */
        @JvmStatic
        fun newInstanceWithContent(content: String): MarkdownPreviewFragment {
            return MarkdownPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MARKDOWN_CONTENT, content)
                }
            }
        }
    }

    private var filePath: String? = null
    private var inlineContent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            filePath = args.getString(EditorFragmentTabManager.ARG_FILE_PATH)
            inlineContent = args.getString(ARG_MARKDOWN_CONTENT)
        }
        LOG.info(
            "MarkdownPreviewFragment.onCreate: filePath={}, hasInlineContent={}, hasArguments={}",
            filePath,
            !inlineContent.isNullOrBlank(),
            arguments != null,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                MarkdownPreviewScreen(filePath = filePath, inlineContent = inlineContent)
            }
        }
    }

    // === Bug 5.1: 进入 Markdown 预览时隐藏符号输入控件, 退出时恢复 ===
    // 与 ImagePreviewFragment 一致: 通过 SymbolInputVisibilityManager 把符号输入
    // 控件 + header 状态栏 + 分隔线全部 GONE, 只保留 EdgeSnapBubbleView 在屏幕
    // 可见区域. 上滑气泡时 EditorBottomSheet.drawerDragListener 会调用
    // showFromPreview() 恢复.

    override fun onResume() {
        super.onResume()
        SymbolInputVisibilityManager.hideForPreview()
    }

    override fun onPause() {
        super.onPause()
        SymbolInputVisibilityManager.showFromPreview()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            SymbolInputVisibilityManager.showFromPreview()
        } else {
            SymbolInputVisibilityManager.hideForPreview()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SymbolInputVisibilityManager.showFromPreview()
    }
}

/** 顶层 Composable，方便 preview / 测试单独调用。 */
@Composable
private fun MarkdownPreviewScreen(
    filePath: String?,
    inlineContent: String?,
) {
    // 用 produceState 在 IO 线程读文件，state 切换由 Compose 驱动
    val state by produceState<MarkdownUiState>(initialValue = MarkdownUiState.Loading) {
        val resolved = runCatching { loadContent(filePath, inlineContent) }
        value = resolved.fold(
            onSuccess = { MarkdownUiState.Loaded(it.text, it.baseDir) },
            onFailure = { MarkdownUiState.Error(it.message ?: it.javaClass.simpleName) }
        )
    }

    when (val s = state) {
        MarkdownUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is MarkdownUiState.Error -> Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = s.message, style = MaterialTheme.typography.bodyMedium)
        }

        is MarkdownUiState.Loaded -> MarkdownPreviewBody(text = s.text, baseDir = s.baseDir)
    }
}

@Composable
private fun MarkdownPreviewBody(text: String, baseDir: String) {
    val context = LocalContext.current
    // baseDir 变化时重建 coilStore（仅在切换文件时发生）
    val coilStore = remember(baseDir) {
        MarkdownImageSources(context.applicationContext, baseDir)
    }

    MarkdownText(
        markdown = text,
        modifier = Modifier.fillMaxSize().padding(12.dp),
        isTextSelectable = true,
        linkifyMask = Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS,
        enableSoftBreakAddsNewLine = true,
        // syntaxHighlightColor / syntaxHighlightTextColor 跟随 MaterialTheme，
        // 不显式设值，让 MarkwonTheme 用默认就好
        coilStore = coilStore,
        onLinkClicked = { link ->
            // 把 http(s) 链接交给系统默认浏览器；2a2 之后可接入 IDE 的 link handler
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            true
        },
    )
}

// region 私有类型 / 工具

private sealed class MarkdownUiState {
    data object Loading : MarkdownUiState()
    data class Loaded(val text: String, val baseDir: String) : MarkdownUiState()
    data class Error(val message: String) : MarkdownUiState()
}

private data class LoadedContent(val text: String, val baseDir: String)

/**
 * 在 IO 线程读 filePath / 取 inlineContent，返回 LoadedContent。
 *
 * baseDir 供 MarkdownImageSources 解析同目录相对路径用：
 *  - filePath 模式：filePath 的父目录
 *  - inlineContent 模式：空字符串（相对路径无法解析为预期文件）
 */
private suspend fun loadContent(filePath: String?, inlineContent: String?): LoadedContent =
    withContext(Dispatchers.IO) {
        when {
            !inlineContent.isNullOrBlank() -> LoadedContent(
                text = inlineContent,
                baseDir = "",
            )

            !filePath.isNullOrBlank() -> {
                val file = File(filePath)
                LoadedContent(
                    text = file.readText(Charsets.UTF_8),
                    baseDir = (file.parentFile?.absolutePath ?: ""),
                )
            }

            else -> error("No markdown file or inline content was provided.")
        }
    }

private val LOG = LoggerFactory.getLogger("MarkdownPreviewFragment")

// endregion
