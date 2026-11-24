package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.source.SourceResolver;

import java.util.Optional;

public final class JavaLanguageService extends LanguageService {
    private final JavaParserFacade parser = new JavaParserFacade();

    public JavaLanguageService() {
        super(LanguageId.JAVA);
    }

    @Override
    public Optional<ParsedFile> parse(String path, String text) {
        try {
            ParsedFile pf = parser.parse(path, text);
            notifyChanged(pf);
            return Optional.of(pf);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ResolutionResult> resolve(ParsedFile file, Reference ref) {
        if (importResolver() != null) {
            Optional<ResolutionResult> imported = importResolver().resolveImport(file, ref);
            if (imported.isPresent()) return imported;
        }
        if (sourceResolver() != null && ref.kind == Reference.ReferenceKind.CLASS) {
            String fqn = resolveFqn(file, ref.name);
            SourceResolver.ResolvedSource src = sourceResolver().resolve(fqn);
            if (src.isResolved()) {
                return Optional.of(ResolutionResult.resolved(
                        src.displayPath,
                        null,
                        new Symbol(ref.name, fqn, SymbolKind.CLASS, "", src.displayPath)
                ));
            }
        }
        return Optional.empty();
    }

    private String resolveFqn(ParsedFile file, String name) {
        if (name.contains(".")) return name;
        if (file.packageName != null && !file.packageName.isEmpty()) {
            return file.packageName + "." + name;
        }
        return name;
    }
}
