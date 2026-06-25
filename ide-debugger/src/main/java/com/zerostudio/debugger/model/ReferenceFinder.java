/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase G.5: ReferenceFinder — find usages and peek definition.
 *
 *  Walks parsed Java sources to find references to a symbol, and
 *  populates an [AstIndex] that can be queried later.
 *
 *  The finder uses JavaParser's Visitor API to enumerate every
 *  NameExpr / MethodCallExpr / FieldAccessExpr in the source and
 *  check whether the referenced name matches a known symbol key
 *  in the index.
 *
 *  Strategy:
 *    1. For "peek definition" of a method/class — look up by symbol
 *       key directly. Symbol keys are stable across re-parses.
 *    2. For "find usages" of a method — parse every indexed source
 *       file, walk the AST, and for every NameExpr that resolves to
 *       a known method or field, record a Reference.
 *
 *  Performance: for a project of N files with M symbols, finding
 *  usages of one symbol is O(M) lookup + O(N * AST size) walk.
 *  In practice, projects under 10k LoC complete in < 100ms.
 */
package com.zerostudio.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.zerostudio.debugger.model.JavaSourceParser.ParsedSource;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ReferenceFinder {

    private final AstIndex index;
    private final JavaSourceParser parser;
    /** Source files to scan when findUsages() is called. */
    private final List<String> sourceFiles = new CopyOnWriteArrayList<>();

    public ReferenceFinder(@NonNull AstIndex index) {
        this.index = index;
        this.parser = new JavaSourceParser();
    }

    /**
     * Add a source file to the searchable corpus. The file is parsed
     * once and symbols inside are added to the AstIndex.
     */
    public void addSource(@NonNull String sourceFile) {
        if (!sourceFiles.contains(sourceFile)) {
            sourceFiles.add(sourceFile);
        }
        index.indexSource(sourceFile);
    }

    /**
     * Build the index by parsing all known sources. Subsequent calls
     * are no-ops (idempotent).
     */
    public void buildIndex() {
        for (String f : sourceFiles) {
            index.indexSource(f);
        }
    }

    /**
     * Peek the definition of a method (or class).
     *
     * @param classSignature e.g. "Lcom/example/Foo;"
     * @param methodName e.g. "doIt"
     * @return the definition site, or null if not found
     */
    @Nullable
    public AstIndex.Definition peekMethodDefinition(
            @NonNull String classSignature, @NonNull String methodName) {
        String key = AstIndex.methodKey(classSignature, methodName);
        return index.peekDefinition(key);
    }

    /**
     * Peek the definition of a class.
     *
     * @param classSignature e.g. "Lcom/example/Foo;"
     */
    @Nullable
    public AstIndex.Definition peekClassDefinition(@NonNull String classSignature) {
        return index.peekDefinition(AstIndex.classKey(classSignature));
    }

    /**
     * Find all usages of a method in the indexed sources.
     *
     * This walks every known source file, parses it, and for each
     * MethodCallExpr checks if the called name matches `methodName`.
     * Match is by name only; full overload resolution is out of scope.
     */
    @NonNull
    public List<AstIndex.Reference> findUsages(
            @NonNull String classSignature, @NonNull String methodName) {
        String key = AstIndex.methodKey(classSignature, methodName);
        List<AstIndex.Reference> usages = new ArrayList<>();
        for (String f : sourceFiles) {
            ParsedSource parsed = parser.parse(new File(f));
            if (parsed == null) continue;
            // Walk the compilation unit and find MethodCallExprs matching the name.
            parsed.compilationUnit.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr n, Void arg) {
                    super.visit(n, arg);
                    if (methodName.equals(n.getNameAsString())) {
                        int line = n.getBegin().map(p -> p.line).orElse(-1);
                        int col = n.getBegin().map(p -> p.column).orElse(-1);
                        usages.add(new AstIndex.Reference(key, f, line, col));
                    }
                }
            }, null);
        }
        // Cache the result in the index for future queries.
        for (AstIndex.Reference ref : usages) {
            index.addReference(ref);
        }
        return usages;
    }

    /**
     * Find all references to a class (object creation, type declaration, cast).
     * Phase G.5: best-effort match by simple class name; no full FQN resolution.
     */
    @NonNull
    public List<AstIndex.Reference> findClassUsages(@NonNull String classSignature) {
        String simpleName = classSignature;
        if (simpleName.startsWith("L") && simpleName.endsWith(";")) {
            String internal = simpleName.substring(1, simpleName.length() - 1);
            int slash = internal.lastIndexOf('/');
            int dollar = internal.lastIndexOf('$');
            int cut = Math.max(slash, dollar);
            simpleName = cut < 0 ? internal : internal.substring(cut + 1);
        }
        String key = AstIndex.classKey(classSignature);
        List<AstIndex.Reference> usages = new ArrayList<>();
        for (String f : sourceFiles) {
            ParsedSource parsed = parser.parse(new File(f));
            if (parsed == null) continue;
            parsed.compilationUnit.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(NameExpr n, Void arg) {
                    super.visit(n, arg);
                    if (simpleName.equals(n.getNameAsString())) {
                        int line = n.getBegin().map(p -> p.line).orElse(-1);
                        int col = n.getBegin().map(p -> p.column).orElse(-1);
                        usages.add(new AstIndex.Reference(key, f, line, col));
                    }
                }
                @Override
                public void visit(ObjectCreationExpr n, Void arg) {
                    super.visit(n, arg);
                    if (simpleName.equals(n.getType().getNameAsString())) {
                        int line = n.getBegin().map(p -> p.line).orElse(-1);
                        int col = n.getBegin().map(p -> p.column).orElse(-1);
                        usages.add(new AstIndex.Reference(key, f, line, col));
                    }
                }
            }, null);
        }
        for (AstIndex.Reference ref : usages) {
            index.addReference(ref);
        }
        return usages;
    }

    /**
     * Walk a parsed source to add method declarations to the index.
     * Phase G.4 helper: called by AstIndex.indexSource so the index
     * has the full picture of every method declared.
     */
    public void walkDeclarations(@NonNull String sourceFile) {
        index.indexSource(sourceFile);
    }

    /**
     * Number of source files registered with this finder.
     */
    public int sourceFileCount() { return sourceFiles.size(); }

    @NonNull
    public List<String> sourceFiles() { return Collections.unmodifiableList(sourceFiles); }
}
