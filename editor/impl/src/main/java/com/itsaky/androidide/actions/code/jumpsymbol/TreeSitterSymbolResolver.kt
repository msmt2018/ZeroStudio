package com.itsaky.androidide.actions.code.jumpsymbol

import android.content.Context
import android.zero.studio.symbol.SymbolInfo
import android.zero.studio.symbol.SymbolType
import com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguageProvider
import com.itsaky.androidide.lexers.java20.Java20Lexer
import com.itsaky.androidide.lexers.java20.Java20Parser
import com.itsaky.androidide.lexers.java20.Java20ParserBaseListener
import com.itsaky.androidide.lexers.kotlin.KotlinLexer
import com.itsaky.androidide.lexers.kotlin.KotlinParser
import com.itsaky.androidide.lexers.kotlin.KotlinParserBaseListener
import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.TSParser
import com.itsaky.androidide.treesitter.string.UTF16String
import com.itsaky.androidide.treesitter.string.UTF16StringFactory
import java.io.File
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTreeWalker

object TreeSitterSymbolResolver {

  fun parseSymbols(context: Context, file: File, code: String): List<SymbolInfo> {
    return when (val extension = file.extension.lowercase()) {
      "java" -> parseJavaSymbols(code)
      "kt",
      "kts" -> parseKotlinSymbols(code, extension)
      else -> parseTreeSitterSymbols(context, file, code, extension)
    }
  }

  private fun parseJavaSymbols(source: String): List<SymbolInfo> {
    val lexer = Java20Lexer(CharStreams.fromString(source))
    val tokens = CommonTokenStream(lexer)
    val parser = Java20Parser(tokens).withoutDiagnostics()
    val symbols = mutableListOf<SymbolInfo>()
    ParseTreeWalker.DEFAULT.walk(JavaSymbolListener(source, symbols), parser.compilationUnit())
    return symbols.sortedBy { it.line }
  }

  private fun parseKotlinSymbols(source: String, language: String): List<SymbolInfo> {
    val lexer = KotlinLexer(CharStreams.fromString(source))
    val tokens = CommonTokenStream(lexer)
    val parser = KotlinParser(tokens).withoutDiagnostics()
    val symbols = mutableListOf<SymbolInfo>()
    ParseTreeWalker.DEFAULT.walk(
        KotlinSymbolListener(source, symbols, language),
        parser.kotlinFile(),
    )
    return symbols.sortedBy { it.line }
  }

  private class JavaSymbolListener(
      private val source: String,
      private val symbols: MutableList<SymbolInfo>,
  ) : Java20ParserBaseListener() {
    private var classDepth = 0

    override fun enterPackageDeclaration(ctx: Java20Parser.PackageDeclarationContext) {
      val name = ctx.identifier().joinToString(".") { it.text }
      symbols += symbol(name, "package", ctx, SymbolType.PACKAGE, classDepth)
    }

    override fun enterImportDeclaration(ctx: Java20Parser.ImportDeclarationContext) {
      val text = ctx.cleanedText().removePrefix("import ").removeSuffix(";").trim()
      symbols += symbol(text, "import", ctx, SymbolType.IMPORT, classDepth)
    }

    override fun enterNormalClassDeclaration(ctx: Java20Parser.NormalClassDeclarationContext) {
      val nameNode = ctx.typeIdentifier() ?: return
      symbols += symbol(
          name = nameNode.text,
          signature = ctx.signatureAfterName(nameNode, ctx.classBody()),
          ctx = ctx,
          type = SymbolType.CLASS,
          indent = classDepth,
      )
      classDepth++
    }

    override fun exitNormalClassDeclaration(ctx: Java20Parser.NormalClassDeclarationContext) {
      classDepth = (classDepth - 1).coerceAtLeast(0)
    }

    override fun enterNormalInterfaceDeclaration(ctx: Java20Parser.NormalInterfaceDeclarationContext) {
      val nameNode = ctx.typeIdentifier() ?: return
      symbols += symbol(
          name = nameNode.text,
          signature = ctx.signatureAfterName(nameNode, ctx.interfaceBody()),
          ctx = ctx,
          type = SymbolType.INTERFACE,
          indent = classDepth,
      )
      classDepth++
    }

    override fun exitNormalInterfaceDeclaration(ctx: Java20Parser.NormalInterfaceDeclarationContext) {
      classDepth = (classDepth - 1).coerceAtLeast(0)
    }

    override fun enterEnumDeclaration(ctx: Java20Parser.EnumDeclarationContext) {
      val nameNode = ctx.typeIdentifier() ?: return
      symbols += symbol(
          name = nameNode.text,
          signature = ctx.signatureAfterName(nameNode, ctx.enumBody()),
          ctx = ctx,
          type = SymbolType.ENUM,
          indent = classDepth,
      )
      classDepth++
    }

    override fun exitEnumDeclaration(ctx: Java20Parser.EnumDeclarationContext) {
      classDepth = (classDepth - 1).coerceAtLeast(0)
    }

    override fun enterRecordDeclaration(ctx: Java20Parser.RecordDeclarationContext) {
      val nameNode = ctx.typeIdentifier() ?: return
      symbols += symbol(
          name = nameNode.text,
          signature = ctx.signatureAfterName(nameNode, ctx.recordBody()),
          ctx = ctx,
          type = SymbolType.CLASS,
          indent = classDepth,
      )
      classDepth++
    }

    override fun exitRecordDeclaration(ctx: Java20Parser.RecordDeclarationContext) {
      classDepth = (classDepth - 1).coerceAtLeast(0)
    }

    override fun enterFieldDeclaration(ctx: Java20Parser.FieldDeclarationContext) {
      val typeText = ctx.unannType()?.cleanedText().orEmpty()
      ctx.variableDeclaratorList()?.variableDeclarator().orEmpty().forEach { declarator ->
        val id = declarator.variableDeclaratorId() ?: return@forEach
        val name = id.identifier()?.text ?: return@forEach
        val arraySuffix = id.dims()?.cleanedText().orEmpty()
        symbols +=
            symbol(
                name,
                listOf(typeText, arraySuffix).joinToString(" ").trim(),
                declarator,
                SymbolType.FIELD,
                classDepth,
            )
      }
    }

    override fun enterMethodDeclaration(ctx: Java20Parser.MethodDeclarationContext) {
      val header = ctx.methodHeader() ?: return
      val declarator = header.methodDeclarator() ?: return
      val nameNode = declarator.identifier() ?: return
      val parameters = declarator.signatureAfterName(nameNode, null).ifBlank { "()" }
      val returnType = header.result()?.cleanedText()?.takeIf { it.isNotBlank() }
      val throwsText = header.throwsT()?.cleanedText()?.takeIf { it.isNotBlank() }
      val signature = buildString {
        append(parameters)
        if (returnType != null) append(" : ").append(returnType)
        if (throwsText != null) append(' ').append(throwsText)
      }
      symbols += symbol(nameNode.text, signature, ctx, SymbolType.METHOD, classDepth)
    }

    override fun enterConstructorDeclaration(ctx: Java20Parser.ConstructorDeclarationContext) {
      val declarator = ctx.constructorDeclarator() ?: return
      val nameNode = declarator.simpleTypeName()?.typeIdentifier() ?: return
      val body = ctx.constructorBody()
      symbols +=
          symbol(
              nameNode.text,
              declarator.signatureAfterName(nameNode, body),
              ctx,
              SymbolType.CONSTRUCTOR,
              classDepth,
          )
    }

    private fun symbol(
        name: String,
        signature: String,
        ctx: ParserRuleContext,
        type: SymbolType,
        indent: Int,
    ): SymbolInfo =
        SymbolInfo(
            name = name,
            signature = signature.ifBlank { type.name.lowercase() },
            line = ctx.start.line - 1,
            type = type,
            indentLevel = indent,
            language = "java",
        )

    private fun ParserRuleContext.signatureAfterName(
        nameNode: ParserRuleContext,
        bodyNode: ParserRuleContext?,
    ): String {
      val start = nameNode.stop.endIndex + 1
      val end = (bodyNode?.start?.startIndex ?: stop.endIndex + 1) - 1
      return source.sliceSafe(start, end).cleanSymbolText()
    }

    private fun ParserRuleContext.cleanedText(): String =
        source.sliceSafe(start.startIndex, stop.endIndex).cleanSymbolText()
  }

  private class KotlinSymbolListener(
      private val source: String,
      private val symbols: MutableList<SymbolInfo>,
      private val language: String,
  ) : KotlinParserBaseListener() {
    private var classDepth = 0

    override fun enterPackageHeader(ctx: KotlinParser.PackageHeaderContext) {
      val name = ctx.identifier()?.text ?: return
      symbols += symbol(name, "package", ctx, SymbolType.PACKAGE, classDepth)
    }

    override fun enterImportHeader(ctx: KotlinParser.ImportHeaderContext) {
      val text = ctx.cleanedText().removePrefix("import ").trim()
      symbols += symbol(text, "import", ctx, SymbolType.IMPORT, classDepth)
    }

    override fun enterClassDeclaration(ctx: KotlinParser.ClassDeclarationContext) {
      val nameNode = ctx.simpleIdentifier() ?: return
      val declarationText = ctx.cleanedText()
      val type =
          when {
            ctx.INTERFACE() != null -> SymbolType.INTERFACE
            declarationText.startsWith("enum class") || declarationText.contains(" enum class ") -> SymbolType.ENUM
            else -> SymbolType.CLASS
          }
      symbols += symbol(
          name = nameNode.text,
          signature = ctx.signatureAfterName(nameNode, ctx.classBody() ?: ctx.enumClassBody()),
          ctx = ctx,
          type = type,
          indent = classDepth,
      )
      classDepth++
    }

    override fun exitClassDeclaration(ctx: KotlinParser.ClassDeclarationContext) {
      classDepth = (classDepth - 1).coerceAtLeast(0)
    }

    override fun enterObjectDeclaration(ctx: KotlinParser.ObjectDeclarationContext) {
      val nameNode = ctx.simpleIdentifier() ?: return
      symbols += symbol(
          name = nameNode.text,
          signature = ctx.signatureAfterName(nameNode, ctx.classBody()),
          ctx = ctx,
          type = SymbolType.CLASS,
          indent = classDepth,
      )
      classDepth++
    }

    override fun exitObjectDeclaration(ctx: KotlinParser.ObjectDeclarationContext) {
      classDepth = (classDepth - 1).coerceAtLeast(0)
    }

    override fun enterCompanionObject(ctx: KotlinParser.CompanionObjectContext) {
      val nameNode = ctx.simpleIdentifier()
      symbols += symbol(
          name = nameNode?.text ?: "Companion",
          signature = ctx.signatureAfterName(nameNode, ctx.classBody()).ifBlank { "object" },
          ctx = ctx,
          type = SymbolType.CLASS,
          indent = classDepth,
      )
      classDepth++
    }

    override fun exitCompanionObject(ctx: KotlinParser.CompanionObjectContext) {
      classDepth = (classDepth - 1).coerceAtLeast(0)
    }

    override fun enterFunctionDeclaration(ctx: KotlinParser.FunctionDeclarationContext) {
      val nameNode = ctx.identifier() ?: return
      symbols += symbol(
          name = nameNode.text,
          signature = ctx.signatureAfterName(nameNode, ctx.functionBody()),
          ctx = ctx,
          type = SymbolType.FUNCTION,
          indent = classDepth,
      )
    }

    override fun enterPropertyDeclaration(ctx: KotlinParser.PropertyDeclarationContext) {
      val variable = ctx.variableDeclaration() ?: return
      val nameNode = variable.simpleIdentifier() ?: return
      val kind = if (ctx.VAR() != null) "var" else "val"
      val modifiers = ctx.modifierList()?.cleanedText()?.takeIf { it.isNotBlank() }
      val typeText = variable.type()?.let { ": ${it.cleanedText()}" }.orEmpty()
      symbols += symbol(
          name = nameNode.text,
          signature = listOfNotNull(modifiers, kind).joinToString(" ") + typeText,
          ctx = ctx,
          type = SymbolType.FIELD,
          indent = classDepth,
      )
    }

    override fun enterClassParameter(ctx: KotlinParser.ClassParameterContext) {
      val nameNode = ctx.simpleIdentifier() ?: return
      val kind =
          when {
            ctx.VAR() != null -> "var"
            ctx.VAL() != null -> "val"
            else -> return
          }
      val modifiers = ctx.modifierList()?.cleanedText()?.takeIf { it.isNotBlank() }
      val typeText = ctx.type()?.let { ": ${it.cleanedText()}" }.orEmpty()
      symbols += symbol(
          name = nameNode.text,
          signature = listOfNotNull(modifiers, kind).joinToString(" ") + typeText,
          ctx = ctx,
          type = SymbolType.FIELD,
          indent = classDepth,
      )
    }

    private fun symbol(
        name: String,
        signature: String,
        ctx: ParserRuleContext,
        type: SymbolType,
        indent: Int,
    ): SymbolInfo =
        SymbolInfo(
            name = name,
            signature = signature.ifBlank { type.name.lowercase() },
            line = ctx.start.line - 1,
            type = type,
            indentLevel = indent,
            language = language,
        )

    private fun ParserRuleContext.signatureAfterName(
        nameNode: ParserRuleContext?,
        bodyNode: ParserRuleContext?,
    ): String {
      val signatureStart = (nameNode?.stop?.endIndex ?: this.start.endIndex) + 1
      val signatureEnd = (bodyNode?.start?.startIndex ?: stop.endIndex + 1) - 1
      return source.sliceSafe(signatureStart, signatureEnd).cleanSymbolText()
    }

    private fun ParserRuleContext.cleanedText(): String =
        source.sliceSafe(start.startIndex, stop.endIndex).cleanSymbolText()
  }

  private fun parseTreeSitterSymbols(
      context: Context,
      file: File,
      code: String,
      extension: String,
  ): List<SymbolInfo> {
    val languageImpl = TreeSitterLanguageProvider.forType(extension, context) ?: return emptyList()

    val parser = TSParser.create()
    parser.language = languageImpl.languageSpec.language

    return parser.use { p ->
      val content: UTF16String = UTF16StringFactory.newString(code)
      p.parseString(null, content)?.use { tree ->
        val symbols = ArrayList<SymbolInfo>()
        tree.rootNode?.let { root -> traverseTree(root, symbols, code, extension) }
        symbols
      } ?: emptyList()
    }
  }

  private fun traverseTree(
      node: TSNode,
      list: MutableList<SymbolInfo>,
      sourceCode: String,
      lang: String,
  ) {
    val symbol =
        when (lang) {
          else -> processFallbackNode(node, sourceCode, lang)
        }

    symbol?.let { list.add(it) }

    for (i in 0 until node.childCount) {
      val child = node.getChild(i)
      if (child != null && child.canAccess()) {
        traverseTree(child, list, sourceCode, lang)
      }
    }
  }

  private fun processFallbackNode(node: TSNode, source: String, language: String): SymbolInfo? {
    val startLine = node.startPoint.row
    return when (node.type) {
      "function_declaration",
      "function_definition" -> {
        val name = getNodeText(node.getChildByFieldName("name"), source) ?: return null
        SymbolInfo(name, "function", startLine, SymbolType.FUNCTION, language = language)
      }
      "class_declaration" -> {
        val name = getNodeText(node.getChildByFieldName("name"), source) ?: return null
        SymbolInfo(name, "class", startLine, SymbolType.CLASS, language = language)
      }
      else -> null
    }
  }

  private fun getNodeText(node: TSNode?, source: String): String? {
    if (node == null || !node.canAccess()) return null

    return try {
      // parser 使用 parseString(UTF16String)，因此 startByte/endByte 返回的是
      // UTF-16LE 字节偏移。需要除以 2 转换为 char 索引才能用于 String.substring。
      val start = node.startByte / 2
      val end = node.endByte / 2
      if (start < 0 || end > source.length || start >= end) null else source.substring(start, end)
    } catch (e: IllegalStateException) {
      null
    }
  }

  private fun <T : Parser> T.withoutDiagnostics(): T {
    removeErrorListeners()
    return this
  }

  private fun String.sliceSafe(start: Int, endInclusive: Int): String {
    if (isEmpty()) return ""
    val safeStart = start.coerceIn(0, length)
    val safeEnd = endInclusive.coerceIn(-1, length - 1)
    if (safeStart > safeEnd) return ""
    return substring(safeStart, safeEnd + 1)
  }

  private fun String.cleanSymbolText(): String =
      replace(Regex("\\s+"), " ")
          .replace(Regex("\\s+([,;)<>])"), "$1")
          .replace(Regex("([(<])\\s+"), "$1")
          .removeSuffix(";")
          .trim()

  private val Token.endIndex: Int
    get() = stopIndex
}
