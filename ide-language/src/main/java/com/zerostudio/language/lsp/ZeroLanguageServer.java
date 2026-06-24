package com.zerostudio.language.lsp;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.service.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ZeroLanguageServer {

    private final ProjectIndex index = new ProjectIndex();
    private final JavaParserFacade parser = new JavaParserFacade();
    private final CodeCompletionService completion = new CodeCompletionService(index);
    private final FindReferencesService references = new FindReferencesService(index);
    private final HoverService hover = new HoverService(index);
    private final DiagnosticsService diagnostics = new DiagnosticsService(index);
    private final FormatterService formatter = new FormatterService();
    private final DocumentSymbolsService symbols = new DocumentSymbolsService(index);
    private final FoldingRangeService folding = new FoldingRangeService();
    private final RenameService rename = new RenameService(index);

    public String getServerId() {
        return "zerostudio";
    }

    public void shutdown() {
        index.clear();
    }

    public void didOpen(String path, String content) {
        indexSource(path, content);
    }

    public void didChange(String path, String content) {
        indexSource(path, content);
    }

    public void didClose(String path) {
        index.remove(path);
    }

    public List<CompletionItem> complete(String path, int line, int column, String prefix) {
        List<CodeCompletionService.Item> items = completion.complete(path, line, column, prefix);
        List<CompletionItem> result = new ArrayList<>();
        for (CodeCompletionService.Item item : items) {
            result.add(new CompletionItem(item.label, item.kind.name()));
        }
        return result;
    }

    public List<Match> findReferences(String path, int line, int column, boolean includeDeclaration) {
        List<FindReferencesService.Match> matches = references.findReferences(path, line, column, includeDeclaration);
        List<Match> result = new ArrayList<>();
        for (FindReferencesService.Match m : matches) {
            result.add(new Match(m.file, m.reference.range, m.reference.name));
        }
        return result;
    }

    public List<Definition> findDefinition(String path, String text, int offset) {
        ParsedFile pf = index.fileForPath(path);
        if (pf == null) return Collections.emptyList();
        List<Definition> result = new ArrayList<>();
        for (Reference ref : pf.references) {
            if (ref.range != null) {
                int start = ref.range.start.line * 10000 + ref.range.start.column;
                int end = ref.range.end.line * 10000 + ref.range.end.column;
                if (offset >= start && offset <= end) {
                    String fqn = (pf.packageName != null && !pf.packageName.isEmpty() && !ref.name.contains("."))
                            ? pf.packageName + "." + ref.name : ref.name;
                    ParsedFile decl = index.fileFor(fqn);
                    if (decl != null) {
                        result.add(new Definition(decl.path, null, ref.name));
                    }
                }
            }
        }
        return result;
    }

    public String hover(String path, int line, int column) {
        HoverService.HoverInfo info = hover.hover(path, line, column);
        if (info == null) return "";
        StringBuilder sb = new StringBuilder();
        if (info.title != null) sb.append(info.title);
        if (info.subtitle != null) sb.append(" ").append(info.subtitle);
        if (info.description != null) sb.append("\n").append(info.description);
        return sb.toString();
    }

    public List<Diagnostic> analyze(String path) {
        List<DiagnosticsService.Diagnostic> diags = diagnostics.check(path);
        List<Diagnostic> result = new ArrayList<>();
        for (DiagnosticsService.Diagnostic d : diags) {
            result.add(new Diagnostic(d.message, d.severity == DiagnosticsService.Diagnostic.Severity.ERROR));
        }
        return result;
    }

    public String formatCode(String source) {
        return formatter.format(source);
    }

    public List<Symbol> documentSymbols(String path) {
        List<DocumentSymbolsService.Symbol> syms = symbols.listSymbols(path);
        List<Symbol> result = new ArrayList<>();
        for (DocumentSymbolsService.Symbol s : syms) {
            result.add(new Symbol(s.name, s.kind.name()));
        }
        return result;
    }

    public List<Fold> foldingRanges(String path) {
        ParsedFile pf = index.fileForPath(path);
        if (pf == null) return Collections.emptyList();
        List<FoldingRangeService.FoldingRange> ranges = folding.computeFoldingRanges(pf.rawText);
        List<Fold> result = new ArrayList<>();
        for (FoldingRangeService.FoldingRange r : ranges) {
            result.add(new Fold(r.startLine, r.endLine));
        }
        return result;
    }

    public RenameEdit rename(String path, int line, int column, String newName) {
        RenameService.WorkspaceEdit edit = rename.rename(path, line, column, newName);
        List<Edit> edits = new ArrayList<>();
        for (RenameService.TextEdit e : edit.edits) {
            edits.add(new Edit(e.file, new SourceRange(
                    new com.zerostudio.language.model.SourcePosition(e.file, e.startLine, e.startColumn),
                    new com.zerostudio.language.model.SourcePosition(e.file, e.endLine, e.endColumn)),
                    edit.newName));
        }
        return new RenameEdit(edit.newName, edits);
    }

    private void indexSource(String path, String content) {
        ParsedFile pf = parser.parse(path, content);
        index.index(pf);
    }

    public static class CompletionItem {
        public final String name;
        public final String kind;
        public CompletionItem(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }
    }

    public static class Match {
        public final String filePath;
        public final SourceRange range;
        public final String symbolName;
        public Match(String filePath, SourceRange range, String symbolName) {
            this.filePath = filePath;
            this.range = range;
            this.symbolName = symbolName;
        }
    }

    public static class Definition {
        public final String filePath;
        public final SourceRange range;
        public final String symbolName;
        public Definition(String filePath, SourceRange range, String symbolName) {
            this.filePath = filePath;
            this.range = range;
            this.symbolName = symbolName;
        }
    }

    public static class Diagnostic {
        public final String message;
        public final boolean isError;
        public Diagnostic(String message, boolean isError) {
            this.message = message;
            this.isError = isError;
        }
    }

    public static class Symbol {
        public final String name;
        public final String kind;
        public Symbol(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }
    }

    public static class Fold {
        public final int startLine;
        public final int endLine;
        public Fold(int startLine, int endLine) {
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }

    public static class Edit {
        public final String filePath;
        public final SourceRange range;
        public final String newText;
        public Edit(String filePath, SourceRange range, String newText) {
            this.filePath = filePath;
            this.range = range;
            this.newText = newText;
        }
    }

    public static class RenameEdit {
        public final String newName;
        public final List<Edit> edits;
        public RenameEdit(String newName, List<Edit> edits) {
            this.newName = newName;
            this.edits = edits;
        }
    }
}
