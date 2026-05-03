package com.itsaky.androidide.actions.code.jumpsymbol

import android.content.Context
import android.zero.studio.symbol.SymbolInfo
import android.zero.studio.symbol.SymbolType
import com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguageProvider
import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.TSParser
import com.itsaky.androidide.treesitter.string.UTF16String
import com.itsaky.androidide.treesitter.string.UTF16StringFactory
import java.io.File

object TreeSitterSymbolResolver {

  fun parseSymbols(context: Context, file: File, code: String): List<SymbolInfo> {
    val extension = file.extension.lowercase()
    val languageImpl = TreeSitterLanguageProvider.forType(extension, context) ?: return emptyList()

    val parser = TSParser.create()
    parser.language = languageImpl.languageSpec.language

    // 使用 'use' 块确保资源被正确释放
    return parser.use { p ->
      val content: UTF16String = UTF16StringFactory.newString(code)

      // 假设 UTF16String 也需要管理资源
      // 如果它实现了 AutoCloseable
      // content.use { utf16Content ->

      p.parseString(null, content)?.use { tree ->
        val symbols = ArrayList<SymbolInfo>()
        tree.rootNode?.let { root -> traverseTree(root, symbols, code, extension) }
        symbols
      } ?: emptyList()

      // } // end of content.use
    }
  }

  private fun traverseTree(
      node: TSNode,
      list: MutableList<SymbolInfo>,
      sourceCode: String,
      lang: String,
  ) {
    // 在访问子节点之前，先处理当前节点
    // 这确保了在当前函数作用域内，`node` 是有效的
    val symbol =
        when (lang) {
          "java" -> processJavaNode(node, sourceCode)
          "kt",
          "kts" -> processKotlinNode(node, sourceCode)
          else -> null
        }

    symbol?.let { list.add(it) }

    // 递归遍历子节点。获取的 child 只在下次循环前有效。
    for (i in 0 until node.childCount) {
      val child = node.getChild(i)
      // 确保 child 是有效的
      if (child != null && child.canAccess()) {
        traverseTree(child, list, sourceCode, lang)
      }
    }
  }

  // --- JAVA 处理逻辑 ---
  private fun processJavaNode(node: TSNode, source: String): SymbolInfo? {
    val type = node.type
    val startLine = node.startPoint.row

    return when (type) {
      "package_declaration" -> {
        val name = getNodeText(node.getChildByFieldName("name"), source) ?: return null
        SymbolInfo(name, "Package Declaration", startLine, SymbolType.PACKAGE)
      }
      "import_declaration" -> {
        val name =
            getNodeText(node.getChildByFieldName("name"), source)
                ?: getNodeText(node, source)?.removePrefix("import")?.removeSuffix(";")?.trim()
                ?: return null
        SymbolInfo(name, "Import", startLine, SymbolType.IMPORT)
      }
      "class_declaration" -> {
        val name = getNodeText(node.getChildByFieldName("name"), source) ?: "Anonymous Class"
        SymbolInfo(name, "Class", startLine, SymbolType.CLASS)
      }
      "interface_declaration" -> {
        val name = getNodeText(node.getChildByFieldName("name"), source) ?: "Interface"
        SymbolInfo(name, "Interface", startLine, SymbolType.INTERFACE)
      }
      "enum_declaration" -> {
        val name = getNodeText(node.getChildByFieldName("name"), source) ?: "Enum"
        SymbolInfo(name, "Enum", startLine, SymbolType.ENUM)
      }
      "method_declaration" -> {
        val name = getNodeText(node.getChildByFieldName("name"), source) ?: return null
        val params = getNodeText(node.getChildByFieldName("parameters"), source) ?: "()"

        SymbolInfo(name, params, startLine, SymbolType.METHOD)
      }
      "constructor_declaration" -> {
        val name = getNodeText(node.getChildByFieldName("name"), source) ?: return null
        val params = getNodeText(node.getChildByFieldName("parameters"), source) ?: "()"
        SymbolInfo(name, params, startLine, SymbolType.CONSTRUCTOR)
      }
      "field_declaration" -> {
        val typeNode = node.getChildByFieldName("type")
        val typeText = getNodeText(typeNode, source) ?: ""

        // Java 'field_declaration' can have multiple declarators. e.g., int a, b;
        // We need to iterate through them.
        val declarator = node.getChildByFieldName("declarator")
        if (declarator != null) {
          val name = getNodeText(declarator.getChildByFieldName("name"), source)
          if (name != null) {
            return SymbolInfo(name, typeText, startLine, SymbolType.FIELD)
          }
        }

        // Fallback for more complex declarations, just grab the first valid name.
        findChildByType(node, "variable_declarator")
            ?.let { getNodeText(it.getChildByFieldName("name"), source) }
            ?.let { name ->
              return SymbolInfo(name, typeText, startLine, SymbolType.FIELD)
            }

        null
      }
      else -> null
    }
  }

  // --- KOTLIN 处理逻辑 ---
  private fun processKotlinNode(node: TSNode, source: String): SymbolInfo? {
    val type = node.type
    val startLine = node.startPoint.row

    return when (type) {
      "package_header" -> {
        val name = getNodeText(findChildByType(node, "identifier"), source) ?: "package"
        SymbolInfo(name, "Package", startLine, SymbolType.PACKAGE)
      }
      "import_header" -> {
        val name =
            getNodeText(findChildByType(node, "identifier"), source)
                ?: getNodeText(node, source)?.removePrefix("import")?.trim()
                ?: "import"
        SymbolInfo(name, "Import", startLine, SymbolType.IMPORT)
      }
      "class_declaration" -> {
        val name = getNodeText(findChildByType(node, "simple_identifier"), source) ?: "Class"
        SymbolInfo(name, "Class", startLine, SymbolType.CLASS)
      }
      "object_declaration" -> {
        val name = getNodeText(findChildByType(node, "simple_identifier"), source) ?: "Object"
        SymbolInfo(name, "Object", startLine, SymbolType.CLASS)
      }
      "function_declaration" -> {
        val name = getNodeText(findChildByType(node, "simple_identifier"), source) ?: "<anon>"
        val params = getNodeText(findChildByType(node, "function_value_parameters"), source) ?: "()"

        SymbolInfo(name, params, startLine, SymbolType.METHOD)
      }
      "property_declaration" -> {
        val decl = findChildByType(node, "variable_declaration")
        val name = getNodeText(findChildByType(decl, "simple_identifier"), source) ?: "prop"
        val type = getNodeText(findChildByType(decl, "type_annotation"), source) ?: "Property"
        SymbolInfo(name, type, startLine, SymbolType.FIELD)
      }
      else -> null
    }
  }

  private fun getNodeText(node: TSNode?, source: String): String? {
    if (node == null || !node.canAccess()) return null

    return try {
      val start = node.startByte
      val end = node.endByte
      val bytes = source.toByteArray(Charsets.UTF_8)
      if (start < 0 || end > bytes.size || start >= end) {
        null
      } else {
        String(bytes, start, end - start, Charsets.UTF_8)
      }
    } catch (e: IllegalStateException) {
      null
    } catch (e: Exception) {
      null
    }
  }

  private fun findChildByType(parent: TSNode?, type: String): TSNode? {
    if (parent == null || !parent.canAccess()) return null
    for (i in 0 until parent.childCount) {
      val child = parent.getChild(i)
      if (child != null && child.canAccess() && child.type == type) {
        return child
      }
    }
    return null
  }
}
