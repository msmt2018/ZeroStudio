package com.itsaky.androidide.actions.menu

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.menu.api.CodeSnippet
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.editor.utils.*
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

/**
 * @author android_zero Changes on 2025/11/13:
 * - Modified `jumpToLine` function to support both vertical (line) and horizontal (column) jumps.
 * - Updated the dialog to include two input fields for line and column numbers.
 * - Implemented logic to handle combined jumps (line then column) or single jumps (line or column
 *   only).
 * - Added validation for user input to ensure it's within the valid range of the document.
 * - Added a dynamic hint for the column input that updates based on the selected line's length.
 */
object EditorLineOperations {

  private const val PREFS_NAME = "EditorSettings"
  private const val KEY_READ_ONLY = "read_only_mode"

  /** 中文注释: 用于管理光标导航历史的单例对象，以实现“上一个/下一个位置”功能。 */
  val navigationHistory = NavigationHistory()

  @Suppress("UNUSED_PARAMETER")
  fun copyLine(editor: CodeEditor, context: Context): Boolean {
    val cursor = editor.cursor
    if (cursor.isSelected) {
      editor.copyText()
    } else {
      val i = cursor.leftLine
      editor.setSelectionRegion(i, 0, i, editor.text.getColumnCount(i))
      editor.copyText(false)
    }
    return true
  }

  @Suppress("UNUSED_PARAMETER")
  fun cutLine(editor: CodeEditor, context: Context): Boolean {
   val cursor = editor.cursor
    if (!cursor.isSelected) {
      editor.cutLine()
      return true
    }

    val startLine = cursor.leftLine
    var endLine = cursor.rightLine
    if (cursor.rightColumn == 0 && endLine > startLine) {
      endLine -= 1
    }

    val endSelectionLine: Int
    val endSelectionColumn: Int
    if (endLine + 1 < editor.lineCount) {
      endSelectionLine = endLine + 1
      endSelectionColumn = 0
    } else {
      endSelectionLine = endLine
      endSelectionColumn = editor.text.getColumnCount(endLine)
    }

    editor.setSelectionRegion(startLine, 0, endSelectionLine, endSelectionColumn)
    editor.cutText()
    return true
  }

  @Suppress("UNUSED_PARAMETER")
  fun deleteLine(editor: CodeEditor, context: Context): Boolean {
    val cursor = editor.cursor
    if (cursor.isSelected) {
      editor.deleteText()
    } else {
      val currentLine = cursor.leftLine
      val nextLine = currentLine + 1
      if (nextLine == editor.lineCount) {
        editor.setSelectionRegion(
            currentLine,
            0,
            currentLine,
            editor.text.getColumnCount(currentLine),
        )
      } else {
        editor.setSelectionRegion(currentLine, 0, nextLine, 0)
      }
      editor.deleteText()
    }
    return true
  }

  @Suppress("UNUSED_PARAMETER")
  fun emptyLine(editor: CodeEditor, context: Context): Boolean {
    val content = editor.text
    val cursor = editor.cursor
    val startLine = cursor.leftLine
    val endLine = if (cursor.isSelected) cursor.rightLine else startLine
    content.beginBatchEdit()
    try {
      for (line in endLine downTo startLine) {
        val lineEnd = content.getColumnCount(line)
        if (lineEnd > 0) {
          content.delete(line, 0, line, lineEnd)
        }
      }
    } finally {
      content.endBatchEdit()
    }
    editor.setSelection(startLine, 0)
    return true
  }

  @Suppress("UNUSED_PARAMETER")
  fun replaceLine(editor: CodeEditor, context: Context): Boolean {
    val content = editor.text
    val cursor = editor.cursor
    content.beginBatchEdit()
    try {
      if (cursor.isSelected) {
        val startLine = cursor.leftLine
        val endLine = cursor.rightLine
        val endCol = content.getColumnCount(endLine)
        content.delete(startLine, 0, endLine, endCol)
        editor.pasteText()
        editor.setSelection(startLine, 0)
      } else {
        val line = cursor.leftLine
        val lineLength = content.getColumnCount(line)
        content.delete(line, 0, line, lineLength)
        editor.pasteText()
        editor.setSelection(line, 0)
      }
    } finally {
      content.endBatchEdit()
    }
    return true
  }

  @Suppress("UNUSED_PARAMETER")
  fun duplicateLine(editor: CodeEditor, context: Context): Boolean {
    val content = editor.text
    val cursor = editor.cursor
    content.beginBatchEdit()
    try {
      if (cursor.isSelected) {
        val startLine = cursor.leftLine
        val endLine = cursor.rightLine
        val duplicateContent = StringBuilder("\n")
        for (i in startLine..endLine) {
          duplicateContent.append(content.getLineString(i)).append(if (i < endLine) "\n" else "")
        }
        content.insert(endLine, content.getColumnCount(endLine), duplicateContent.toString())
        val newStartLine = endLine + 1
        val newEndLine = endLine + 1 + (endLine - startLine)
        editor.setSelectionRegion(newStartLine, 0, newEndLine, content.getColumnCount(newEndLine))
      } else {
        val line = cursor.leftLine
        val column = cursor.leftColumn
        val lineContent = content.getLineString(line)
        content.insert(line, content.getColumnCount(line), "\n" + lineContent)
        editor.setSelection(line + 1, column)
      }
    } finally {
      content.endBatchEdit()
    }
    return true
  }

  @Suppress("UNUSED_PARAMETER")
  fun convertUpperLowerCase(editor: CodeEditor, context: Context, toUpper: Boolean): Boolean {
    val content = editor.text
    val cursor = editor.cursor
    if (!cursor.isSelected) {
      return false
    }
    content.beginBatchEdit()
    try {
      val startLine = cursor.leftLine
      val startCol = cursor.leftColumn
      val endLine = cursor.rightLine
      val endCol = cursor.rightColumn
      val selectedText = content.subContent(startLine, startCol, endLine, endCol).toString()
      val convertedText = if (toUpper) selectedText.uppercase() else selectedText.lowercase()
      content.replace(startLine, startCol, endLine, endCol, convertedText)
      editor.setSelectionRegion(startLine, startCol, endLine, startCol + convertedText.length)
    } finally {
      content.endBatchEdit()
    }
    return true
  }

  @Suppress("UNUSED_PARAMETER")
  fun increaseIndent(editor: CodeEditor, context: Context): Boolean {
    val content = editor.text
    val cursor = editor.cursor
    val tabWidth = editor.tabWidth
    val tabSpaces = " ".repeat(tabWidth)
    content.beginBatchEdit()
    try {
      if (cursor.isSelected) {
        val startLine = cursor.leftLine
        val endLine = cursor.rightLine
        for (line in startLine..endLine) {
          content.insert(line, 0, tabSpaces)
        }
      } else {
        val line = cursor.leftLine
        val column = cursor.leftColumn
        content.insert(line, column, tabSpaces)
        editor.setSelection(line, column + tabWidth)
      }
    } finally {
      content.endBatchEdit()
    }
    return true
  }

  @Suppress("UNUSED_PARAMETER")
  fun decreaseIndent(editor: CodeEditor, context: Context): Boolean {
    val content = editor.text
    val cursor = editor.cursor
    val tabWidth = editor.tabWidth
    content.beginBatchEdit()
    try {
      val startLine = if (cursor.isSelected) cursor.leftLine else cursor.leftLine
      val endLine = if (cursor.isSelected) cursor.rightLine else cursor.leftLine
      for (line in startLine..endLine) {
        val lineText = content.getLineString(line)
        var spacesToRemove = 0
        while (spacesToRemove < lineText.length && lineText[spacesToRemove] == ' ') {
          spacesToRemove++
        }
        if (spacesToRemove > 0) {
          val removeCount = if (spacesToRemove >= tabWidth) tabWidth else spacesToRemove
          content.delete(line, 0, line, removeCount)
        }
      }
    } finally {
      content.endBatchEdit()
    }
    return true
  }

  /**
   * 中文注释: 执行类似软键盘退格键(Backspace)的操作。 English annotation: Performs an operation similar to the soft
   * keyboard Backspace key.
   *
   * 1. 如果有选中内容：删除选中区域（standard delete selection）。
   * 2. 如果无选中内容：删除光标左侧的一个字符（standard backspace）。
   *
   * @author android_zero
   */
  @Suppress("UNUSED_PARAMETER")
  fun deleteContentOrBackspace(editor: CodeEditor, context: Context): Boolean {
    val cursor = editor.cursor
    val content = editor.text

    // 有选区，直接删除选区内容
    // 这会符合"不删除行本身，只删除内容并合并"的软键盘逻辑
    if (cursor.isSelected) {
      editor.deleteText()
      return true
    }

    // 无选区，执行退格 (Backspace) 逻辑
    val line = cursor.leftLine
    val column = cursor.leftColumn

    // 如果光标在文件最开头 (0, 0)，无法删除
    if (line == 0 && column == 0) {
      return false
    }

    content.beginBatchEdit()
    try {
      if (column > 0) {
        // 光标不在行首，删除前一个字符
        // Delete previous char on the same line
        content.delete(line, column - 1, line, column)
      } else {
        // 光标在行首，需要与上一行合并 (相当于删除上一行的换行符)
        // Merge with previous line
        val prevLine = line - 1
        val prevLineLen = content.getColumnCount(prevLine)
        content.delete(prevLine, prevLineLen, line, 0)
      }
    } finally {
      content.endBatchEdit()
    }
    return true
  }

  /**
   * 中文注释: 跳转到指定的行和列。 English annotation: Jump to a specific line and column.
   *
   * @author android_zero
   */
  fun jumpToLine(editor: CodeEditor, context: Context): Boolean {
    val view =
        LayoutInflater.from(context).inflate(R.layout.dialog_jump_to_line, null as ViewGroup?)

    val lineInputLayout = view.findViewById<TextInputLayout>(R.id.lineInputLayout)
    val lineEditText = view.findViewById<EditText>(R.id.lineEditText)
    val columnInputLayout = view.findViewById<TextInputLayout>(R.id.columnInputLayout)
    val columnEditText = view.findViewById<EditText>(R.id.columnEditText)

    val currentLine = editor.cursor.leftLine
    val maxLine = editor.lineCount
    val maxColumnOnCurrentLine = editor.text.getColumnCount(currentLine)

    lineInputLayout.hint = context.getString(R.string.jump_to_line_vertical_hint, maxLine)
    columnInputLayout.hint =
        context.getString(R.string.jump_to_line_horizontal_hint, maxColumnOnCurrentLine + 1)

    lineEditText.addTextChangedListener(
        object : TextWatcher {
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

          override fun afterTextChanged(s: Editable?) {
            val lineNum = s?.toString()?.toIntOrNull()
            val targetLine = lineNum?.let { it - 1 } ?: editor.cursor.leftLine
            if (targetLine in 0 until editor.lineCount) {
              val maxCol = editor.text.getColumnCount(targetLine)
              columnInputLayout.hint =
                  context.getString(R.string.jump_to_line_horizontal_hint, maxCol + 1)
            } else {
              columnInputLayout.hint =
                  context.getString(R.string.jump_to_line_horizontal_hint_default)
            }
          }
        }
    )

    val builder =
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.jump_to_line_title))
            .setView(view)
            .setPositiveButton(context.getString(R.string.ok_button), null)
            .setNegativeButton(context.getString(R.string.cancel_button), null)

    val dialog = builder.create()
    dialog.window?.setBackgroundDrawable(
        ContextCompat.getDrawable(context, R.drawable.rounded_dialog_background)
    )
    dialog.show()

    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
      lineInputLayout.error = null
      columnInputLayout.error = null

      val lineInputStr = lineEditText.text.toString()
      val columnInputStr = columnEditText.text.toString()

      val hasLineInput = lineInputStr.isNotBlank()
      val hasColumnInput = columnInputStr.isNotBlank()

      if (!hasLineInput && !hasColumnInput) {
        lineInputLayout.error = context.getString(R.string.enter_something_error)
        return@setOnClickListener
      }

      var finalLineNum: Int? = null
      if (hasLineInput) {
        try {
          val lineNum = lineInputStr.toInt()
          if (lineNum in 1..maxLine) {
            finalLineNum = lineNum
          } else {
            lineInputLayout.error = context.getString(R.string.value_out_of_range_error)
            return@setOnClickListener
          }
        } catch (e: NumberFormatException) {
          lineInputLayout.error = context.getString(R.string.invalid_number_error)
          return@setOnClickListener
        }
      }

      val lineForColumnContext = finalLineNum?.let { it - 1 } ?: editor.cursor.leftLine
      var finalColNum: Int? = null
      if (hasColumnInput) {
        try {
          val colNum = columnInputStr.toInt()
          val maxCol = editor.text.getColumnCount(lineForColumnContext)
          if (colNum in 1..(maxCol + 1)) {
            finalColNum = colNum
          } else {
            columnInputLayout.error =
                context.getString(R.string.value_out_of_range_error_with_max, maxCol + 1)
            return@setOnClickListener
          }
        } catch (e: NumberFormatException) {
          columnInputLayout.error = context.getString(R.string.invalid_number_error)
          return@setOnClickListener
        }
      }

      val targetLine = finalLineNum?.let { it - 1 } ?: editor.cursor.leftLine
      val targetColumn =
          finalColNum?.let { it - 1 } ?: (if (hasLineInput) 0 else editor.cursor.leftColumn)

      editor.setSelection(targetLine, targetColumn)
      editor.ensureSelectionVisible()
      dialog.dismiss()
    }
    return true
  }

  private sealed class CommentStyle {
    data class Line(val prefix: String) : CommentStyle()

    data class Block(val prefix: String, val suffix: String) : CommentStyle()
  }

  private fun getCommentStyle(file: File): CommentStyle? {
    return when (file.extension.lowercase()) {
      "c",
      "cpp",
      "h",
      "hpp",
      "cc",
      "cxx",
      "hh",
      "java",
      "kt",
      "kts",
      "groovy",
      "gvy",
      "gy",
      "gsh",
      "scala",
      "sc",
      "cs",
      "fs",
      "fsi",
      "fsx",
      "js",
      "mjs",
      "cjs",
      "jsx",
      "ts",
      "mts",
      "cts",
      "tsx",
      "php",
      "phtml",
      "go",
      "swift",
      "rs",
      "d",
      "vala",
      "cr",
      "dart",
      "zig",
      "glsl",
      "vert",
      "frag",
      "tesc",
      "tese",
      "geom",
      "comp",
      "adoc",
      "asciidoc",
      "pas",
      "p",
      "pp",
      "angelscript",
      "as",
      "ceylon" -> CommentStyle.Line("// ")
      "py",
      "pyw",
      "pyc",
      "pyd",
      "rpy",
      "rb",
      "rbw",
      "rake",
      "ru",
      "pl",
      "pm",
      "t",
      "pod",
      "sh",
      "bash",
      "zsh",
      "fish",
      "ksh",
      "csh",
      "ash",
      "ps1",
      "psm1",
      "psd1",
      "tcl",
      "el",
      "conf",
      "cfg",
      "ini",
      "desktop",
      "properties",
      "toml",
      "yml",
      "yaml",
      "tf",
      "tfvars",
      "hcl",
      "nginx.conf",
      "conf.d",
      "apache.conf",
      "httpd.conf",
      "prefs",
      "gitignore",
      "gitconfig",
      "gitattributes",
      "gitmodules",
      "dockerfile",
      "containerfile",
      "makefile",
      "mk",
      "mak",
      "cmake",
      "cmakelists.txt",
      "scons",
      "smali",
      "r",
      "rscript" -> CommentStyle.Line("# ")
      "sql",
      "ddl",
      "dml",
      "tsql",
      "pls",
      "pck",
      "pks",
      "pkb",
      "lua",
      "hs",
      "lhs",
      "ada",
      "adb",
      "ads" -> CommentStyle.Line("-- ")
      "lisp",
      "cl",
      "clisp",
      "lsp",
      "scm",
      "ss",
      "rkt",
      "asm",
      "s",
      "inc" -> CommentStyle.Line("; ")
      "vb",
      "vbs" -> CommentStyle.Line("' ")
      "m",
      "matlab" -> CommentStyle.Line("% ")
      "f",
      "f77",
      "f90",
      "f95",
      "for" -> CommentStyle.Line("! ")
      "prolog",
      "plg" -> CommentStyle.Line("% ")
      "erl",
      "hrl" -> CommentStyle.Line("% ")
      "vim",
      "vimrc",
      "gvimrc" -> CommentStyle.Line("\" ")
      "rst",
      "rest" -> CommentStyle.Line(".. ")
      "xml",
      "html",
      "xhtml",
      "htm",
      "svg",
      "astro",
      "axml",
      "xsl",
      "xslt",
      "xsd",
      "wsdl",
      "plist",
      "jsp",
      "asp",
      "aspx",
      "cshtml",
      "svelte",
      "md",
      "markdown",
      "mdx",
      "vue" -> CommentStyle.Block("<!-- ", " -->")
      "css",
      "scss",
      "sass",
      "less",
      "jsonc" -> CommentStyle.Block("/* ", " */")
      "ml",
      "mli" -> CommentStyle.Block("(* ", " *)")
      "mustache",
      "hbs" -> CommentStyle.Block("{{! ", " }}")
      "pug",
      "jade" -> CommentStyle.Line("//- ")
      "jinja",
      "jinja2",
      "j2",
      "twig" -> CommentStyle.Block("{# ", " #}")
      else -> null
    }
  }

  fun toggleComment(editor: CodeEditor, file: File): Boolean {
    val style = getCommentStyle(file) ?: return false
    editor.text.beginBatchEdit()
    try {
      when (style) {
        is CommentStyle.Line -> handleLineComment(editor, style.prefix)
        is CommentStyle.Block -> handleBlockComment(editor, style.prefix, style.suffix)
      }
    } finally {
      editor.text.endBatchEdit()
    }
    return true
  }

  private fun handleLineComment(editor: CodeEditor, prefix: String) {
    val cursor = editor.cursor
    val text = editor.text
    if (cursor.isSelected) {
      val startLine = cursor.leftLine
      val endLine = cursor.rightLine
      var allCommented = true
      for (line in startLine..endLine) {
        if (
            text.getLineString(line).isNotBlank() &&
                !text.getLineString(line).trimStart().startsWith(prefix)
        ) {
          allCommented = false
          break
        }
      }
      for (line in startLine..endLine) {
        if (text.getLineString(line).isBlank()) continue
        val firstCharPos = getFirstNonWhitespace(text.getLineString(line))
        if (allCommented) {
          if (text.getLineString(line).substring(firstCharPos).startsWith(prefix)) {
            text.delete(line, firstCharPos, line, firstCharPos + prefix.length)
          }
        } else {
          if (!text.getLineString(line).substring(firstCharPos).startsWith(prefix)) {
            text.insert(line, firstCharPos, prefix)
          }
        }
      }
      editor.setSelectionRegion(startLine, 0, endLine, text.getColumnCount(endLine))
    } else {
      val line = cursor.leftLine
      val lineStr = text.getLineString(line)
      val firstCharPos = getFirstNonWhitespace(lineStr)
      if (firstCharPos < lineStr.length) {
        if (lineStr.substring(firstCharPos).startsWith(prefix)) {
          text.delete(line, firstCharPos, line, firstCharPos + prefix.length)
        } else {
          text.insert(line, firstCharPos, prefix)
        }
      }
    }
  }

  private fun handleBlockComment(editor: CodeEditor, prefix: String, suffix: String) {
    val cursor = editor.cursor
    val text = editor.text
    if (!cursor.isSelected) {
      val line = cursor.leftLine
      editor.setSelectionRegion(line, 0, line, text.getColumnCount(line))
    }
    val start = cursor.left()
    val end = cursor.right()
    val selectedText = text.subContent(start.line, start.column, end.line, end.column).toString()
    val trimmedText = selectedText.trim()
    val trimmedPrefix = prefix.trim()
    val trimmedSuffix = suffix.trim()

    if (trimmedText.startsWith(trimmedPrefix) && trimmedText.endsWith(trimmedSuffix)) {
      val startIndexInSelection = selectedText.indexOf(trimmedPrefix)
      val endIndexInSelection = selectedText.lastIndexOf(trimmedSuffix)
      text.delete(
          end.line,
          start.column + endIndexInSelection,
          end.line,
          start.column + endIndexInSelection + trimmedSuffix.length,
      )
      text.delete(
          start.line,
          start.column + startIndexInSelection,
          start.line,
          start.column + startIndexInSelection + trimmedPrefix.length,
      )
    } else {
      text.insert(end.line, end.column, suffix)
      text.insert(start.line, start.column, prefix)
    }
  }

  fun toggleReadOnly(editor: CodeEditor, context: Context): Boolean {
    val newEditableState = !editor.isEditable
    editor.isEditable = newEditableState
    saveReadOnlyState(context, !newEditableState)
    return !newEditableState
  }

  fun applyReadOnlyState(editor: CodeEditor, context: Context) {
    editor.isEditable = !isReadOnly(context)
  }

  fun isReadOnly(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_READ_ONLY, false)
  }

  private fun saveReadOnlyState(context: Context, isReadOnly: Boolean) {
    getPrefs(context).edit().putBoolean(KEY_READ_ONLY, isReadOnly).apply()
  }

  private fun getFirstNonWhitespace(line: String): Int {
    for (i in line.indices) {
      if (!Character.isWhitespace(line[i])) return i
    }
    return line.length
  }

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }


  private val snippets by lazy {
    mapOf(
        "java" to
            listOf(
                CodeSnippet(
                    "sysout",
                    "System.out.println($0);",
                    "Prints to the standard output stream.",
                ),
                CodeSnippet(
                    "fori",
                    "for (int i = 0; i < $1; i++) {\n\t$0\n}",
                    "Creates a for loop.",
                ),
                CodeSnippet(
                    "psvm",
                    "public static void main(String[] args) {\n\t$0\n}",
                    "Main method declaration.",
                ),
                CodeSnippet(
                    "try",
                    "try {\n\t$0\n} catch (Exception e) {\n\te.printStackTrace();\n}",
                    "Try-catch block.",
                ),
                CodeSnippet("meth", "public void %s() {\n\t$0\n}", "Creates a public method."),
                CodeSnippet("cls", "public class %s {\n\t$0\n}", "Creates a public class."),
            ),
        "kt" to
            listOf(
                CodeSnippet("fun", "fun %s() {\n\t$0\n}", "Creates a function."),
                CodeSnippet(
                    "main",
                    "fun main(args: Array<String>) {\n\t$0\n}",
                    "Main function for command-line app.",
                ),
                CodeSnippet("comp", "companion object {\n\t$0\n}", "Creates a companion object."),
                CodeSnippet("for", "for (item in $1) {\n\t$0\n}", "Creates a for loop."),
            ),
        "xml" to
            listOf(
                CodeSnippet("comment", "<!-- $0 -->", "Inserts an XML comment."),
                CodeSnippet(
                    "LinearLayoutV",
                    "<LinearLayout\n\tandroid:layout_width=\"match_parent\"\n\tandroid:layout_height=\"wrap_content\"\n\tandroid:orientation=\"vertical\">\n\t$0\n</LinearLayout>",
                    "Vertical LinearLayout.",
                ),
                CodeSnippet(
                    "LinearLayoutH",
                    "<LinearLayout\n\tandroid:layout_width=\"match_parent\"\n\tandroid:layout_height=\"wrap_content\"\n\tandroid:orientation=\"horizontal\">\n\t$0\n</LinearLayout>",
                    "Horizontal LinearLayout.",
                ),
                CodeSnippet(
                    "TextView",
                    "<TextView\n\tandroid:layout_width=\"wrap_content\"\n\tandroid:layout_height=\"wrap_content\"\n\tandroid:text=\"$1\" />",
                    "A simple TextView.",
                ),
                CodeSnippet(
                    "Button",
                    "<Button\n\tandroid:layout_width=\"wrap_content\"\n\tandroid:layout_height=\"wrap_content\"\n\tandroid:text=\"$1\" />",
                    "A simple Button.",
                ),
            ),
        "sh" to
            listOf(
                CodeSnippet("if", "if [ $1 ]; then\n\t$0\nfi", "If statement."),
                CodeSnippet("for", "for i in $1; do\n\t$0\ndone", "For loop."),
                CodeSnippet("header", "#!/bin/bash\n\n$0", "Adds a shebang for bash."),
            ),
    )
  }

  fun showAndInsertSnippet(editor: CodeEditor, context: Context, file: File) {
    val lang = file.extension.lowercase()
    val langKey = if (lang == "gradle.kts") "kts" else lang
    val langSnippets = snippets[langKey] ?: snippets[lang] ?: return
    val dialogView =
        LayoutInflater.from(context)
            .inflate(R.layout.action_menu_dialog_insert_snippet, null as ViewGroup?)
    val spinner = dialogView.findViewById<Spinner>(R.id.spinner_snippets)
    val nameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.input_layout_name)
    val nameEditText = dialogView.findViewById<EditText>(R.id.edit_text_name)
    val descriptionText = dialogView.findViewById<TextView>(R.id.text_description)
    val snippetNames = langSnippets.map { it.name }
    val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, snippetNames)
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinner.adapter = adapter
    spinner.onItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
          override fun onItemSelected(
              parent: AdapterView<*>?,
              view: View?,
              position: Int,
              id: Long,
          ) {
            val selectedSnippet = langSnippets[position]
            descriptionText.text = selectedSnippet.description
            nameInputLayout.visibility =
                if (selectedSnippet.requiresNameInput) View.VISIBLE else View.GONE
          }

          override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    AlertDialog.Builder(context)
        .setTitle("Insert Code Snippet")
        .setView(dialogView as View)
        .setPositiveButton("Insert") { _, _ ->
          val selectedSnippet = langSnippets[spinner.selectedItemPosition]
          val name = if (selectedSnippet.requiresNameInput) nameEditText.text.toString() else ""
          var finalCode = selectedSnippet.template.replace("%s", name).replace(Regex("\\$\\d+"), "")
          val finalCursorOffset = finalCode.indexOf("$0")
          if (finalCursorOffset != -1) {
            finalCode = finalCode.replace("$0", "")
          }
          val startCursor = editor.cursor.left()
          editor.text.insert(startCursor.line, startCursor.column, finalCode)
          if (finalCursorOffset != -1) {
            val newCursorPos =
                editor.text.getCharPositionForByte(
                    startCursor.index * 2 + finalCursorOffset * 2
                ) // Byte offset
            editor.setSelection(newCursorPos.line, newCursorPos.column)
          }
        }
        .setNegativeButton("Cancel", null)
        .show()
  }

}

class NavigationHistory(private val capacity: Int = 100) {
  private val history = mutableListOf<CharPosition>()
  private var currentIndex = -1

  fun add(position: CharPosition) {
    if (
        history.isNotEmpty() &&
            currentIndex >= 0 &&
            currentIndex < history.size &&
            history[currentIndex] == position
    ) {
      return
    }
    if (currentIndex < history.size - 1) {
      history.subList(currentIndex + 1, history.size).clear()
    }
    history.add(position)
    currentIndex++
    if (history.size > capacity) {
      history.removeAt(0)
      currentIndex--
    }
  }

  fun back(): CharPosition? {
    if (canGoBack()) {
      currentIndex--
      return history[currentIndex]
    }
    return null
  }

  fun forward(): CharPosition? {
    if (canGoForward()) {
      currentIndex++
      return history[currentIndex]
    }
    return null
  }

  fun canGoBack(): Boolean = currentIndex > 0

  fun canGoForward(): Boolean = currentIndex < history.size - 1

  fun clear() {
    history.clear()
    currentIndex = -1
  }
}
