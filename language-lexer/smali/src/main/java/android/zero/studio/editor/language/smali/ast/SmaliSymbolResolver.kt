package android.zero.studio.editor.language.smali.ast

import android.zero.studio.symbol.SymbolInfo
import android.zero.studio.symbol.SymbolType
import com.itsaky.androidide.lexers.smali.SmaliBaseVisitor
import com.itsaky.androidide.lexers.smali.SmaliParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import com.itsaky.androidide.lexers.smali.SmaliLexer

object SmaliSymbolResolver {

    fun parseSymbols(code: String): List<SymbolInfo> {
        if (code.isBlank()) return emptyList()
        return runCatching {
            val parser = SmaliParser(CommonTokenStream(SmaliLexer(CharStreams.fromString(code))))
            parser.removeErrorListeners()
            val collector = Collector()
            collector.visit(parser.smali())
            collector.items
        }.getOrDefault(emptyList())
    }

    private class Collector : SmaliBaseVisitor<Void?>() {
        val items = mutableListOf<SymbolInfo>()

        override fun visitClassSpec(ctx: SmaliParser.ClassSpecContext): Void? {
            items.add(SymbolInfo(ctx.text, "Class", ctx.start.line - 1, SymbolType.CLASS))
            return super.visitClassSpec(ctx)
        }

        override fun visitFieldAlt(ctx: SmaliParser.FieldAltContext): Void? {
            items.add(SymbolInfo(ctx.text, "Field", ctx.start.line - 1, SymbolType.FIELD))
            return super.visitFieldAlt(ctx)
        }

        override fun visitMethodAlt(ctx: SmaliParser.MethodAltContext): Void? {
            items.add(SymbolInfo(ctx.text, "Method", ctx.start.line - 1, SymbolType.METHOD))
            return super.visitMethodAlt(ctx)
        }
    }
}
