package com.zerostudio.language.model;
public final class SourceRange {
    public final SourcePosition start;
    public final SourcePosition end;
    public SourceRange(SourcePosition start, SourcePosition end) {
        this.start = start; this.end = end;
    }
}