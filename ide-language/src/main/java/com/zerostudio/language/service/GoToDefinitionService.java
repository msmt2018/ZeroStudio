package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import java.util.*;

public final class GoToDefinitionService {
    private final LanguageService language;

    public GoToDefinitionService(LanguageService language) { this.language = language; }

    public Optional<ResolutionResult> findDefinition(String filePath, String text, int offset) {
        ParsedFile parsed = language.parse(filePath, text).orElse(null);
        if (parsed == null) return Optional.empty();

        // find reference at offset
        Reference targetRef = null;
        for (Reference ref : parsed.references) {
            if (ref.range != null && offset >= ref.range.start.line * 10000 + ref.range.start.column
                    && offset <= ref.range.end.line * 10000 + ref.range.end.column) {
                targetRef = ref;
                break;
            }
        }
        if (targetRef == null) return Optional.empty();

        // resolve via language service
        Optional<ResolutionResult> result = language.resolve(parsed, targetRef);

        // fallback: import chain
        if ((!result.isPresent() || !result.get().isResolved())
                && language.importResolver() != null
                && parsed.language == LanguageId.JAVA) {
            Optional<ResolutionResult> imported = language.importResolver().resolveImport(parsed, targetRef);
            if (imported.isPresent()) return imported;
        }
        return result;
    }
}