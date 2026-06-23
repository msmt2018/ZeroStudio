package com.zerostudio.language.model;

import java.util.Objects;

/**
 * A half-open range {@code [start, end)} in a source file.
 *
 * <p>Line and column are 0-based; the range is inclusive of {@code start} and
 * exclusive of {@code end}.
 */
public final class SourceRange {
    public final SourcePosition start;
    public final SourcePosition end;

    public SourceRange(SourcePosition start, SourcePosition end) {
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
    }

    public SourceRange(int startLine, int startCol, int endLine, int endCol) {
        this(new SourcePosition(startLine, startCol), new SourcePosition(endLine, endCol));
    }

    public boolean contains(SourcePosition p) {
        return p.compareTo(start) >= 0 && p.compareTo(end) < 0;
    }

    public boolean isValid() { return start.isValid() && end.isValid(); }

    public static final SourceRange NONE =
            new SourceRange(SourcePosition.NONE, SourcePosition.NONE);

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SourceRange)) return false;
        SourceRange r = (SourceRange) o;
        return start.equals(r.start) && end.equals(r.end);
    }

    @Override
    public int hashCode() { return Objects.hash(start, end); }

    @Override
    public String toString() { return "[" + start + "-" + end + "]"; }
}
