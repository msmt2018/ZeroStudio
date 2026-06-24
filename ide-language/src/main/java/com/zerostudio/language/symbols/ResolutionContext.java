package com.zerostudio.language.symbols;

import com.zerostudio.language.model.ParsedFile;

/**
 * Per-call context passed to {@link SymbolResolver#resolve}.
 */
public final class ResolutionContext {
    public final ParsedFile currentFile;
    public final com.zerostudio.language.index.ProjectIndex index;
    public final com.zerostudio.language.index.ProjectIndex.Lookup lookup;

    public ResolutionContext(ParsedFile currentFile,
                             com.zerostudio.language.index.ProjectIndex index) {
        this.currentFile = currentFile;
        this.index = index;
        this.lookup = index.lookup();
    }
}
