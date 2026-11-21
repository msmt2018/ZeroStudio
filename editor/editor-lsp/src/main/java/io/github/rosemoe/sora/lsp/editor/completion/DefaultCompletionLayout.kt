package io.github.rosemoe.sora.lsp.editor.completion

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import io.github.rosemoe.sora.lang.completion.CompletionItemKind as SoraItemKind
import io.github.rosemoe.sora.lang.completion.SimpleCompletionIconDrawer
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Job
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemTag
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind

class DefaultLspCompletionLayout : CompletionLayout {

  private lateinit var window: LspCompletionWindow
  private lateinit var root: LinearLayout
  private lateinit var listView: ListView
  private lateinit var docContainer: LinearLayout
  private lateinit var docScrollView: ScrollView
  private lateinit var docTextView: TextView
  private lateinit var docProgressBar: ProgressBar

  private val adapter = LspCompletionAdapter()

  // UI 配色
  private var textColorNormal = 0
  private var textColorDetail = 0
  private var highlightColor = 0
  private var selectedItemBgColor = 0
  private var codeTypeface: Typeface = Typeface.MONOSPACE
  private var dpUnit = 1f
  private var renderJob: Job? = null

  override fun attach(window: LspCompletionWindow) {
    this.window = window
    this.dpUnit = window.editor.dpUnit
  }

  override fun createView(inflater: LayoutInflater): View {
    val context = inflater.context
    root = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    // 补全列表
    listView =
        ListView(context).apply {
          dividerHeight = 0
          isVerticalScrollBarEnabled = false
          overScrollMode = View.OVER_SCROLL_NEVER
          adapter = this@DefaultLspCompletionLayout.adapter
          layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.2f)
          setOnItemClickListener { _, _, position, _ -> window.applySelection(position) }
        }

    // 详细文档容器
    docContainer =
        LinearLayout(context).apply {
          orientation = LinearLayout.VERTICAL
          layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.8f)
          visibility = View.GONE
          setPadding(
              (8 * dpUnit).toInt(),
              (8 * dpUnit).toInt(),
              (8 * dpUnit).toInt(),
              (8 * dpUnit).toInt(),
          )
        }

    docProgressBar =
        ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
          visibility = View.GONE
          layoutParams =
              LinearLayout.LayoutParams(
                      ViewGroup.LayoutParams.WRAP_CONTENT,
                      ViewGroup.LayoutParams.WRAP_CONTENT,
                  )
                  .apply { gravity = Gravity.CENTER }
        }

    docScrollView =
        ScrollView(context).apply {
          layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
          isVerticalScrollBarEnabled = false
        }

    docTextView =
        TextView(context).apply {
          setTextIsSelectable(false)
          movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }

    docScrollView.addView(docTextView)
    docContainer.addView(docProgressBar)
    docContainer.addView(docScrollView)

    root.addView(listView)
    // 简单分割线
    root.addView(
        View(context).apply {
          layoutParams =
              LinearLayout.LayoutParams((1 * dpUnit).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
          setBackgroundColor(Color.LTGRAY)
        }
    )
    root.addView(docContainer)

    return root
  }

  override fun applyColorScheme(colorScheme: EditorColorScheme, typeface: Typeface) {
    codeTypeface = typeface
    textColorNormal = colorScheme.getColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY)
    textColorDetail = colorScheme.getColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY)
    highlightColor = colorScheme.getColor(EditorColorScheme.COMPLETION_WND_TEXT_MATCHED)
    selectedItemBgColor = colorScheme.getColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT)

    val bgColor = colorScheme.getColor(EditorColorScheme.COMPLETION_WND_BACKGROUND)
    val borderColor = colorScheme.getColor(EditorColorScheme.COMPLETION_WND_CORNER)

    root.background =
        GradientDrawable().apply {
          cornerRadius = 8 * dpUnit
          setColor(bgColor)
          setStroke((1 * dpUnit).toInt(), borderColor)
        }
    docContainer.setBackgroundColor(colorScheme.getColor(EditorColorScheme.HOVER_BACKGROUND))
    docTextView.setTextColor(colorScheme.getColor(EditorColorScheme.HOVER_TEXT_NORMAL))

    adapter.notifyDataSetChanged()
  }

  override fun setCompletionItems(items: List<CompletionItem>) {
    adapter.items = items
    adapter.notifyDataSetChanged()
  }

  override fun select(index: Int) {
    if (index in 0 until adapter.count) {
      adapter.selectedIndex = index
      adapter.notifyDataSetChanged()
      listView.setSelectionFromTop(index, listView.height / 2)
    }
  }

  override fun setDocumentationLoading(isLoading: Boolean) {
    docProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    if (isLoading) docContainer.visibility = View.VISIBLE
  }

  // 核心：处理 MarkupContent 与 Markdown 渲染
  override fun showDocumentation(item: CompletionItem) {
    val doc =
        item.documentation
            ?: run {
              docContainer.visibility = View.GONE
              return
            }

    docContainer.visibility = View.VISIBLE
    renderJob?.cancel()

    // 区分 Markdown 和 PlainText
    val markdownText =
        if (doc.isLeft) {
          doc.left // Plain string
        } else {
          val markup: MarkupContent = doc.right
          if (markup.kind == MarkupKind.PLAINTEXT) {
            // 如果是纯文本，将其包裹为 Markdown 的代码块或直接显示
            markup.value
          } else {
            markup.value // Markdown 格式
          }
        }

    if (markdownText.isBlank()) {
      docContainer.visibility = View.GONE
      return
    }

    renderJob = window.launchRender {
      docTextView.text =
          SimpleMarkdownRenderer.renderAsync(
              markdown = markdownText,
              boldColor = highlightColor,
              inlineCodeColor = highlightColor,
              codeTypeface = codeTypeface,
              linkColor = highlightColor,
          )
      docScrollView.post { docScrollView.scrollTo(0, 0) }
    }
  }

  override fun onTextSizeChanged(oldSize: Float, newSize: Float) {
    docTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize)
    adapter.notifyDataSetChanged()
  }

  // --- 适配器：严格根据 LSP4J 解析 ---
  private inner class LspCompletionAdapter : BaseAdapter() {
    var items: List<CompletionItem> = emptyList()
    var selectedIndex = -1

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): CompletionItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val view = convertView as? LinearLayout ?: createItemView(parent.context)
      val item = getItem(position)

      view.setBackgroundColor(
          if (position == selectedIndex) selectedItemBgColor else Color.TRANSPARENT
      )

      val iconView = view.getChildAt(0) as ImageView
      val labelView = view.getChildAt(1) as TextView
      val detailView = view.getChildAt(2) as TextView

      // 1. LSP 图标映射 (将 LSP4J Kind 映射到 sora-editor 图标)
      val soraKind = mapLspKindToSora(item.kind)
      iconView.setImageDrawable(SimpleCompletionIconDrawer.draw(soraKind, circle = false))

      // 2. 标签与废弃(Deprecated)处理
      val isDeprecated =
          item.tags?.contains(CompletionItemTag.Deprecated) == true || item.deprecated == true
      // 注：此处 item.label 可能在外部（FuzzyScorer）被替换为 SpannableString 以显示匹配高亮
      val labelText = SpannableString(item.label)
      if (isDeprecated) {
        labelText.setSpan(
            StrikethroughSpan(),
            0,
            labelText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      }
      labelView.text = labelText
      labelView.alpha = if (isDeprecated) 0.6f else 1.0f

      // 3. LSP 3.17+ LabelDetails 支持 (IDEA 风格：方法签名靠左，返回类型靠右)
      // 由于使用了权分布，这里简单将 Detail 组合： (String param)  : void
      val detailStr = buildString {
        if (item.labelDetails?.detail != null) append(item.labelDetails.detail)
        if (item.labelDetails?.description != null) append("   ${item.labelDetails.description}")
        if (isEmpty() && item.detail != null) append(item.detail)
      }
      detailView.text = detailStr
      detailView.visibility = if (detailStr.isEmpty()) View.GONE else View.VISIBLE

      return view
    }

    private fun mapLspKindToSora(kind: CompletionItemKind?): SoraItemKind {
      if (kind == null) return SoraItemKind.Text
      // 根据 LSP4J 枚举转换为 Sora 内置枚举
      return when (kind) {
        CompletionItemKind.Method -> SoraItemKind.Method
        CompletionItemKind.Function -> SoraItemKind.Function
        CompletionItemKind.Constructor -> SoraItemKind.Constructor
        CompletionItemKind.Field -> SoraItemKind.Field
        CompletionItemKind.Variable -> SoraItemKind.Variable
        CompletionItemKind.Class -> SoraItemKind.Class
        CompletionItemKind.Interface -> SoraItemKind.Interface
        CompletionItemKind.Module -> SoraItemKind.Module
        CompletionItemKind.Property -> SoraItemKind.Property
        CompletionItemKind.Enum -> SoraItemKind.Enum
        CompletionItemKind.Keyword -> SoraItemKind.Keyword
        CompletionItemKind.Snippet -> SoraItemKind.Snippet
        CompletionItemKind.Color -> SoraItemKind.Color
        CompletionItemKind.File -> SoraItemKind.File
        CompletionItemKind.Reference -> SoraItemKind.Reference
        CompletionItemKind.Folder -> SoraItemKind.Folder
        CompletionItemKind.EnumMember -> SoraItemKind.EnumMember
        CompletionItemKind.Constant -> SoraItemKind.Constant
        CompletionItemKind.Struct -> SoraItemKind.Struct
        CompletionItemKind.Event -> SoraItemKind.Event
        CompletionItemKind.Operator -> SoraItemKind.Operator
        CompletionItemKind.TypeParameter -> SoraItemKind.TypeParameter
        else -> SoraItemKind.Text
      }
    }

    private fun createItemView(context: Context): LinearLayout {
      val padding = (6 * dpUnit).toInt()
      val textSize = window.editor.textSizePx

      return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(padding, padding, padding, padding)

        addView(
            ImageView(context).apply {
              layoutParams =
                  LinearLayout.LayoutParams((16 * dpUnit).toInt(), (16 * dpUnit).toInt()).apply {
                    marginEnd = (6 * dpUnit).toInt()
                  }
            }
        )

        addView(
            TextView(context).apply {
              typeface = codeTypeface
              setTextColor(textColorNormal)
              setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
              maxLines = 1
            }
        )

        addView(
            TextView(context).apply {
              typeface = codeTypeface
              setTextColor(textColorDetail)
              setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * 0.85f)
              maxLines = 1
              gravity = Gravity.END
              layoutParams =
                  LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (8 * dpUnit).toInt()
                  }
            }
        )
      }
    }
  }
}
