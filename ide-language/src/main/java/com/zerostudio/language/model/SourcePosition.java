package com.zerostudio.language.model;

import java.util.Objects;

/**
 * A 0-based source position. Both line and column are 0-indexed, inclusive of
 * the start, exclusive of the end for ranges.
 *
 * <p>This class is immutable.
 */
public final class SourcePosition implements Comparable<SourcePosition> {
    public final int line;
    public final int column;

    public SourcePosition(int line, int column) {
        this.line = line;
        this.column = column;
    }

    /** Position that does not exist (e.g. end-of-file sentinel). */
    public static final SourcePosition NONE = new SourcePosition(-1, -1);

    public boolean isValid() { return line >= 0 && column >= 0; }

    @Override
    public int compareTo(SourcePosition o) {
        int c = Integer.compare(this.line, o.line);
        return c != 0 ? c : Integer.compare(this.column, o.column);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SourcePosition)) return false;
        SourcePosition p = (SourcePosition) o;
        return p.line == line && p.column == column;
    }

    @Override
    public int hashCode() { return Objects.hash(line, column); }

    @Override
    public String toString() { return "(" + line + "," + column + ")"; }
}
