package com.zerostudio.language.model;
public final class SourceRange {
    public final SourcePosition start;
    public final SourcePosition end;
    public SourceRange(SourcePosition start, SourcePosition end) {
        this.start = start; this.end = end;
    }
    /** 便捷构造：从起止行列直接生成。 */
    public SourceRange(int startLine, int startCol, int endLine, int endCol) {
        this(new SourcePosition(null, startLine, startCol),
             new SourcePosition(null, endLine, endCol));
    }

    /** 判断 position 是否落在该范围内（左闭右闭，按行列比较）。 */
    public boolean contains(SourcePosition position) {
        if (position == null || start == null || end == null) return false;
        if (position.line < start.line || position.line > end.line) return false;
        if (position.line == start.line && position.column < start.column) return false;
        if (position.line == end.line && position.column > end.column) return false;
        return true;
    }
}
