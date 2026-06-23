package com.zerostudio.language.model;
public final class SourcePosition {
    public final String path;
    public final int line;
    public final int column;
    public SourcePosition(String path, int line, int column) {
        this.path = path; this.line = line; this.column = column;
    }
}