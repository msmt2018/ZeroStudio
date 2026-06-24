package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import com.zerostudio.language.source.SourceResolver;
import java.util.*;
import java.util.function.Consumer;

public class LanguageService {
    private final LanguageId languageId;
    private volatile SourceResolver sourceResolver;
    private volatile ImportResolver importResolver;
    private final List<Consumer<ParsedFile>> changeListeners = new ArrayList<>();

    public LanguageService(LanguageId languageId) { this.languageId = languageId; }

    public LanguageId languageId() { return languageId; }
    public SourceResolver sourceResolver() { return sourceResolver; }
    public ImportResolver importResolver() { return importResolver; }

    public void setSourceResolver(SourceResolver resolver) {
        this.sourceResolver = resolver;
        this.importResolver = resolver == null ? null : new ImportResolver(resolver);
    }

    public void addChangeListener(Consumer<ParsedFile> l) { changeListeners.add(l); }

    public Optional<ParsedFile> parse(String path, String text) {
        return Optional.empty(); // override in subclasses
    }

    public Optional<ResolutionResult> resolve(ParsedFile file, Reference ref) {
        return Optional.empty(); // override in subclasses
    }

    protected void notifyChanged(ParsedFile file) {
        for (Consumer<ParsedFile> l : changeListeners) l.accept(file);
    }
}