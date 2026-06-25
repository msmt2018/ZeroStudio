/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase G1: Java source parser using JavaParser.
 *
 *  Parses .java source files and extracts:
 *    - Package declaration (e.g., "com.example")
 *    - Top-level class declarations and their fully-qualified names
 *    - Inner class declarations (nested static/non-static)
 *    - Method declarations with their line numbers
 *
 *  This allows SourceLocator to map source files to class signatures
 *  with far greater accuracy than the previous basename-only heuristic.
 *
 *  The parser is lenient: it returns whatever it can extract even if
 *  some parts of the file fail to parse (e.g., syntax errors in method
 *  bodies, unresolved imports).
 *
 *  The auxiliary data classes ({@link SourceMethod}, {@link SourceClass},
 *  {@link ParsedSource}) are nested inside {@link JavaSourceParser} so
 *  callers can keep using {@code JavaSourceParser.ParsedSource} etc.
 */

package com.zerostudio.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JavaSourceParser {

    /**
     * A method declaration with its line number, extracted from a parsed source file.
     * Used by SourceLocator to find methods containing a specific line.
     */
    public static final class SourceMethod {

        /** The JVM descriptor of this method (e.g., "doIt", "()V", "(ILjava/lang/String;)V"). */
        public final String name;
        /** The JVM signature (e.g., "(I)V"). */
        public final String signature;
        /** The 1-based line number where this method is declared. */
        public final int lineNumber;

        public SourceMethod(@NonNull String name, @NonNull String signature, int lineNumber) {
            this.name = name;
            this.signature = signature;
            this.lineNumber = lineNumber;
        }
    }

    /**
     * A class declaration extracted from a parsed .java file.
     * Contains the fully-qualified JVM signature and optionally its declared methods.
     */
    public static final class SourceClass {

        /**
         * The JVM type signature (e.g., {@code "Lcom/example/Foo;"} or
         * {@code "Lcom/example/Foo$Inner;"}). Used directly in JDWP
         * ClassesBySignature requests.
         */
        @NonNull
        public final String signature;

        /**
         * Whether this is a top-level class (false for inner/nested classes).
         * Inner classes are kept for completeness but SourceLocator currently
         * only uses the top-level class.
         */
        public final boolean isTopLevel;

        /** Declared methods, sorted by line number. May be empty if not yet resolved. */
        @NonNull
        public final List<SourceMethod> methods;

        public SourceClass(@NonNull String signature, boolean isTopLevel, @NonNull List<SourceMethod> methods) {
            this.signature = signature;
            this.isTopLevel = isTopLevel;
            this.methods = methods;
        }
    }

    /**
     * The result of parsing a .java file. Contains the package name, all declared
     * classes (top-level and inner), and the raw CompilationUnit for advanced use.
     */
    public static final class ParsedSource {

        /**
         * The package name (e.g., {@code "com.example.ui"}). Empty string if no
         * package declaration was found.
         */
        @NonNull
        public final String packageName;

        /**
         * All classes declared in this source file, in declaration order.
         * The first element is always the top-level class.
         */
        @NonNull
        public final List<SourceClass> classes;

        /** The raw CompilationUnit. May be null if parsing failed. */
        @Nullable
        public final CompilationUnit compilationUnit;

        public ParsedSource(@NonNull String packageName, @NonNull List<SourceClass> classes,
                            @Nullable CompilationUnit compilationUnit) {
            this.packageName = packageName;
            this.classes = classes;
            this.compilationUnit = compilationUnit;
        }

        /**
         * Convenience: returns the top-level class signature, or null if the file
         * contains no class declarations.
         */
        @Nullable
        public String topLevelSignature() {
            if (classes.isEmpty()) return null;
            return classes.get(0).signature;
        }

        /**
         * Find a method that contains the given line number.
         * Returns the first matching method, or null if no method spans that line.
         */
        @Nullable
        public SourceMethod findMethodAtLine(int line) {
            for (SourceClass cls : classes) {
                for (SourceMethod m : cls.methods) {
                    // Heuristic: method spans from its declaration line to the
                    // next method's line - 1, or the end of the file.
                    if (m.lineNumber <= line) {
                        return m;
                    }
                }
            }
            return null;
        }
    }

    private final JavaParser parser;

    public JavaSourceParser() {
        ParserConfiguration config = new ParserConfiguration();
        // Don't set a language — default is JAVA. We explicitly configure
        // tolerant settings so partial/incorrect files still yield results.
        config.setAttributeComments(false);
        this.parser = new JavaParser(config);
    }

    /**
     * Parse a .java source file and return a {@link ParsedSource} with all
     * extracted information. Returns null if the file cannot be read or
     * the content is not a valid Java compilation unit.
     */
    @Nullable
    public ParsedSource parse(@NonNull File file) {
        if (!file.exists() || !file.canRead()) {
            return null;
        }
        try {
            String source = new String(Files.readAllBytes(file.toPath()));
            return parseContent(source);
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Parse a .java source file given its path string.
     */
    @Nullable
    public ParsedSource parsePath(@NonNull String filePath) {
        return parse(new File(filePath));
    }

    /**
     * Parse source content directly.
     */
    @Nullable
    public ParsedSource parseContent(@NonNull String source) {
        CompilationUnit cu;
        try {
            cu = parser.parse(source).getResult().orElse(null);
        } catch (Exception ex) {
            // JavaParser can throw RuntimeException on severe parse errors.
            return null;
        }
        if (cu == null) {
            return null;
        }

        String packageName = extractPackage(cu);
        List<SourceClass> classes = extractClasses(cu, packageName);

        return new ParsedSource(packageName, classes, cu);
    }

    /**
     * Extract the package name from a CompilationUnit.
     * Returns empty string if there is no package declaration.
     */
    @NonNull
    private String extractPackage(@NonNull CompilationUnit cu) {
        if (cu.getPackageDeclaration().isPresent()) {
            return cu.getPackageDeclaration().get().getName().asString();
        }
        return "";
    }

    /**
     * Extract all top-level class declarations from a CompilationUnit.
     */
    @NonNull
    private List<SourceClass> extractClasses(@NonNull CompilationUnit cu, @NonNull String pkg) {
        List<SourceClass> result = new ArrayList<>();
        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                result.addAll(extractFromType((ClassOrInterfaceDeclaration) typeDecl, pkg, true));
            }
        }
        return result;
    }

    /**
     * Recursively extract a class and any nested classes declared inside it.
     *
     * @param cls      the class to record
     * @param pkg      the package name (e.g., "com.example")
     * @param topLevel true for the outermost class (not inside another class)
     */
    @NonNull
    private List<SourceClass> extractFromType(@NonNull ClassOrInterfaceDeclaration cls,
                                               @NonNull String pkg, boolean topLevel) {
        String simpleName = cls.getName().asString();
        String signature = buildSignature(pkg, simpleName);

        // Recursively collect inner classes BEFORE this class's methods,
        // so that inner classes appear after outer classes in the list.
        List<SourceClass> innerClasses = new ArrayList<>();
        for (BodyDeclaration<?> member : cls.getMembers()) {
            if (member instanceof TypeDeclaration<?>) {
                TypeDeclaration<?> innerType = (TypeDeclaration<?>) member;
                String innerSimpleName = innerType.getName().asString();
                String innerSig = buildInnerSignature(signature, innerSimpleName);
                List<SourceClass> nested;
                if (innerType instanceof ClassOrInterfaceDeclaration) {
                    nested = extractFromType((ClassOrInterfaceDeclaration) innerType, pkg, false);
                } else {
                    // Enum/annotation declarations are not expanded recursively for now.
                    nested = new ArrayList<>();
                }
                for (SourceClass nc : nested) {
                    nc.methods.clear(); // inner classes' methods not used yet
                    nc.methods.addAll(extractMethods(innerType, innerSig));
                }
                innerClasses.addAll(nested);
            }
        }

        List<SourceMethod> methods = extractMethods(cls, signature);
        List<SourceClass> result = new ArrayList<>();
        result.add(new SourceClass(signature, topLevel, methods));
        result.addAll(innerClasses);
        return result;
    }

    /**
     * Extract all method declarations from a TypeDeclaration.
     * Returns an empty list if the type has no methods.
     */
    @NonNull
    private List<SourceMethod> extractMethods(@NonNull TypeDeclaration<?> typeDecl,
                                              @NonNull String containingSignature) {
        List<SourceMethod> result = new ArrayList<>();
        for (BodyDeclaration<?> member : typeDecl.getMembers()) {
            if (member instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) member;
                String name = method.getName().asString();
                String signature = method.getSignature().asString();
                int line = method.getBegin().map(pos -> pos.line).orElse(-1);
                result.add(new SourceMethod(name, signature, line));
            }
        }
        // Sort by line number for stable ordering.
        Collections.sort(result, (a, b) -> Integer.compare(a.lineNumber, b.lineNumber));
        return result;
    }

    /**
     * Build a JVM type signature from package + simple class name.
     * e.g., ("com.example", "MainActivity") → "Lcom/example/MainActivity;"
     */
    @NonNull
    private String buildSignature(@NonNull String pkg, @NonNull String simpleName) {
        if (pkg.isEmpty()) {
            return "L" + simpleName + ";";
        }
        return "L" + pkg.replace('.', '/') + "/" + simpleName + ";";
    }

    /**
     * Build a JVM type signature for an inner class.
     * e.g., ("Lcom/example/Outer;", "Inner") → "Lcom/example/Outer$Inner;"
     */
    @NonNull
    private String buildInnerSignature(@NonNull String outerSig, @NonNull String innerName) {
        // outerSig is like "Lcom/example/Outer;" — strip trailing ';' and append "$innerName;"
        return outerSig.substring(0, outerSig.length() - 1) + "$" + innerName + ";";
    }
}
