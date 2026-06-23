package com.zerostudio.language.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaParser-backed Java parser. Extracts a list of declared symbols and
 * references from each {@code .java} file.
 *
 * <p>The native AST (a {@link CompilationUnit}) is preserved in
 * {@link ParsedFile#nativeAst} so that callers can re-traverse it.
 */
public final class JavaParserFacade implements Parser {

    private final JavaParser parser;
    private final String languageVersion;

    public JavaParserFacade() {
        this(ParserConfiguration.LanguageLevel.POPULAR);
    }

    public JavaParserFacade(ParserConfiguration.LanguageLevel level) {
        this.languageVersion = level.name();
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setLanguageLevel(level);
        // Don't storeComments=false because we want doc comments resolved to
        // declarations.
        cfg.setStoreTokens(true);
        this.parser = new JavaParser(cfg);
    }

    @Override
    public ParsedFile parse(File file) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()));
        return parse(file.getAbsolutePath(), text);
    }

    @Override
    public ParsedFile parse(String path, String text) {
        ParseResult<CompilationUnit> result = parser.parse(text);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            String err = result.getProblems().toString();
            return new ParsedFile(path, LanguageId.JAVA, System.currentTimeMillis(),
                    text, List.of(), List.of(), null, err);
        }
        CompilationUnit cu = result.getResult().get();
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> refs = new ArrayList<>();
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        for (TypeDeclaration<?> td : cu.getTypes()) {
            extractType(path, packageName, null, td, symbols, refs);
        }

        // Top-level expressions are uncommon in Java, but we still harvest
        // any NameExpr / MethodCallExpr that lives at the top level inside
        // an initializer (rare in practice).
        cu.walk(NameExpr.class, n -> addNameRef(path, packageName, null, n, refs));
        cu.walk(MethodCallExpr.class, n -> addCallRef(path, packageName, null, n, refs));

        return new ParsedFile(path, LanguageId.JAVA, System.currentTimeMillis(),
                text, symbols, refs, cu, null);
    }

    @Override
    public LanguageId language() { return LanguageId.JAVA; }

    private void extractType(String path,
                             String pkg,
                             String outerName,
                             TypeDeclaration<?> td,
                             List<Symbol> symbols,
                             List<Reference> refs) {
        String name = td.getNameAsString();
        String fqn = pkg.isEmpty() ? name : pkg + "." + name;
        if (outerName != null) fqn = outerName + "$" + name;
        final String classFqn = fqn;
        SymbolKind kind = kindFor(td);
        symbols.add(new Symbol(name, fqn, kind, outerName, path,
                toRange(td.getRange().orElse(null)), LanguageId.JAVA));

        for (BodyDeclaration<?> member : td.getMembers()) {
            if (member instanceof MethodDeclaration) {
                final MethodDeclaration md = (MethodDeclaration) member;
                String sig = fqn + "#" + md.getNameAsString()
                        + "(" + paramTypes(md.getParameters()) + ")";
                symbols.add(new Symbol(md.getNameAsString(), sig,
                        SymbolKind.METHOD, fqn, path,
                        toRange(md.getRange().orElse(null)), LanguageId.JAVA));
                // references inside method
                BlockStmt body = md.getBody().orElse(null);
                if (body != null) {
                    final String methodName = md.getNameAsString();
                    body.walk(NameExpr.class, n -> addNameRef(path, classFqn, methodName, n, refs));
                    body.walk(MethodCallExpr.class, n -> addCallRef(path, classFqn, methodName, n, refs));
                }
            } else if (member instanceof ConstructorDeclaration) {
                ConstructorDeclaration cd = (ConstructorDeclaration) member;
                String sig = fqn + "#<init>(" + paramTypes(cd.getParameters()) + ")";
                symbols.add(new Symbol("<init>", sig, SymbolKind.CONSTRUCTOR, fqn,
                        path, toRange(cd.getRange().orElse(null)), LanguageId.JAVA));
            } else if (member instanceof FieldDeclaration) {
                FieldDeclaration fd = (FieldDeclaration) member;
                for (VariableDeclarator vd : fd.getVariables()) {
                    symbols.add(new Symbol(vd.getNameAsString(),
                            fqn + "." + vd.getNameAsString(),
                            SymbolKind.FIELD, fqn, path,
                            toRange(vd.getRange().orElse(null)), LanguageId.JAVA));
                }
                fd.getVariables().forEach(v -> v.getInitializer().ifPresent(init -> {
                    init.walk(NameExpr.class, n -> addNameRef(path, classFqn, null, n, refs));
                    init.walk(MethodCallExpr.class, n -> addCallRef(path, classFqn, null, n, refs));
                }));
            } else if (member instanceof ClassOrInterfaceDeclaration
                    || member instanceof EnumDeclaration) {
                TypeDeclaration<?> inner = (TypeDeclaration<?>) member;
                extractType(path, pkg, fqn, inner, symbols, refs);
            }
        }
    }

    private void addNameRef(String path, String pkg, String container,
                            NameExpr n, List<Reference> refs) {
        refs.add(new Reference(n.getNameAsString(), toRange(n.getRange().orElse(null)),
                Reference.ReferenceKind.READ, container, path, LanguageId.JAVA));
    }

    private void addCallRef(String path, String pkg, String container,
                            MethodCallExpr mc, List<Reference> refs) {
        // Use the range of the method name, not the whole call expression;
        // otherwise nested references (e.g. Toast.makeText where "Toast"
        // also gets a reference) overlap and the cursor-based lookup can't
        // disambiguate.
        refs.add(new Reference(mc.getNameAsString(),
                toRange(mc.getName().getRange().orElse(null)),
                Reference.ReferenceKind.CALL, container, path, LanguageId.JAVA));
        // Also note the receiver / argument names.
        mc.getScope().ifPresent(s -> s.walk(NameExpr.class,
                nn -> addNameRef(path, pkg, container, nn, refs)));
        for (Expression arg : mc.getArguments()) {
            arg.walk(NameExpr.class, nn -> addNameRef(path, pkg, container, nn, refs));
        }
    }

    private static SymbolKind kindFor(TypeDeclaration<?> td) {
        if (td instanceof EnumDeclaration) return SymbolKind.ENUM;
        if (td instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration ci = (ClassOrInterfaceDeclaration) td;
            return ci.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS;
        }
        return SymbolKind.CLASS;
    }

    private static String paramTypes(NodeList<Parameter> params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(params.get(i).getType().asString());
        }
        return sb.toString();
    }

    private static SourceRange toRange(Range r) {
        if (r == null) return SourceRange.NONE;
        com.github.javaparser.Position b = r.begin;
        com.github.javaparser.Position e = r.end;
        return new SourceRange(b.line - 1, b.column - 1, e.line - 1, e.column - 1);
    }

    /**
     * Helper for callers: a full {@link SourceRange} from a JavaParser range
     * (start + end positions).
     */
    public static SourceRange rangeOf(com.github.javaparser.Range r) {
        if (r == null) return SourceRange.NONE;
        com.github.javaparser.Position b = r.begin;
        com.github.javaparser.Position e = r.end;
        return new SourceRange(b.line - 1, b.column - 1, e.line - 1, e.column - 1);
    }
}
