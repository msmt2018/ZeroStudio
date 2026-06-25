package com.zerostudio.language.parser;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.zerostudio.language.model.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public final class JavaParserFacade implements Parser {
    private final SourcePosition pos = new SourcePosition("", 0, 0);
    private String path;
    private String packageName;
    private List<Reference> refs = new ArrayList<>();

    @Override
    public LanguageId language() { return LanguageId.JAVA; }

    @Override
    public ParsedFile parse(File file) throws IOException {
        return parse(file.getPath(), new String(Files.readAllBytes(file.toPath())));
    }

    public ParsedFile parse(String path, String text) {
        this.path = path;
        this.packageName = "";
        this.refs = new ArrayList<>();
        try {
            CompilationUnit cu = new JavaParser().parse(text).getResult().get();
            if (cu.getPackageDeclaration().isPresent()) {
                packageName = cu.getPackageDeclaration().get().getNameAsString();
            }
            extractRefs(cu, text);
            // harvest imports
            cu.getImports().forEach(imp -> {
                String fqn = imp.getNameAsString();
                if (!fqn.isEmpty()) {
                    int col = imp.getRange().map(r -> r.begin.column).orElse(0);
                    int line = imp.getRange().map(r -> r.begin.line).orElse(1);
                    refs.add(new Reference(fqn, 
                            new SourceRange(new SourcePosition(path, line, col),
                                    new SourcePosition(path, line, col + fqn.length())),
                            Reference.ReferenceKind.IMPORT, packageName, path, LanguageId.JAVA));
                }
            });
            return new ParsedFile(path, LanguageId.JAVA, packageName, refs, text);
        } catch (Exception e) {
            return new ParsedFile(path, LanguageId.JAVA, "", Collections.emptyList(), text);
        }
    }

    private void extractRefs(CompilationUnit cu, String text) {
        // extract class/method/field references - simplified
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
            int line = c.getRange().map(r -> r.begin.line).orElse(1);
            int col = c.getRange().map(r -> r.begin.column).orElse(1);
            refs.add(new Reference(c.getNameAsString(),
                    new SourceRange(new SourcePosition(path, line, col),
                            new SourcePosition(path, line, col + c.getNameAsString().length())),
                    Reference.ReferenceKind.CLASS, packageName, path, LanguageId.JAVA));
        });
    }
}