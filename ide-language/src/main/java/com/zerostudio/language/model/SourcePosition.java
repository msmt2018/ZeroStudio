package com.zerostudio.language.model;
public final class SourcePosition {
    public final String path;
    public final int line;
    public final int column;
    public SourcePosition(String path, int line, int column) {
        this.path = path; this.line = line; this.column = column;
    }
    /** 兼容仅有行列的便捷构造（路径为空）。 */
    public SourcePosition(int line, int column) {
        this("", line, column);
    }

    /** 判断 pos 是否位于 this（含端点）。 */
    public boolean contains(SourcePosition pos) {
        if (pos == null) return false;
        if (pos.line < this.line) return false;
        if (pos.line == this.line && pos.column < this.column) return false;
        return true;
    }
}
