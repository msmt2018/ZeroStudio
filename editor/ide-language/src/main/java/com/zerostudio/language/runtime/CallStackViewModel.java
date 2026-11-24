package com.zerostudio.language.runtime;

import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.List;

/**
 * 调用栈视图模型：把 FrameSnapshot.frames() 转换为 UI 友好的 List<CallStackRow>，
 * 包含点击跳转所需的 SourceRange。
 */
public final class CallStackViewModel {

    public static final class Row {
        public final int index;        // 0 = top
        public final String methodName;
        public final String className;
        public final int lineNumber;
        public final String sourcePath;
        public final String displayName;
        public final boolean isTop;
        public final boolean isHighlighted;
        public final boolean isSynthetic;     // access$ / lambda$ 等
        public final SourceRange range;

        public Row(int index, FrameSnapshot.StackFrame frame, SourceRange range) {
            this.index = index;
            this.methodName = frame.methodName;
            this.className = frame.className;
            this.lineNumber = frame.lineNumber;
            this.sourcePath = frame.sourcePath;
            this.displayName = frame.className + "." + frame.methodName + ":" + frame.lineNumber;
            this.isTop = index == 0;
            this.isHighlighted = false; // updated by view model
            this.isSynthetic = frame.methodName.startsWith("access$")
                    || frame.methodName.startsWith("lambda$")
                    || frame.methodName.contains("$");
            this.range = range;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int highlightedIndex = 0;
    private boolean collapsedSynthetic = false;

    public void loadFrom(FrameSnapshot snapshot) {
        rows.clear();
        if (snapshot == null) return;
        List<FrameSnapshot.StackFrame> frames = snapshot.frames();
        for (int i = 0; i < frames.size(); i++) {
            FrameSnapshot.StackFrame f = frames.get(i);
            SourceRange range = new SourceRange(
                    new com.zerostudio.language.model.SourcePosition(
                            f.sourcePath != null ? f.sourcePath : f.className, f.lineNumber, 1),
                    new com.zerostudio.language.model.SourcePosition(
                            f.sourcePath != null ? f.sourcePath : f.className, f.lineNumber, 1));
            rows.add(new Row(i, f, range));
        }
        highlightedIndex = 0;
    }

    public List<Row> rows() { return java.util.Collections.unmodifiableList(rows); }

    public List<Row> visibleRows() {
        if (!collapsedSynthetic) return rows();
        List<Row> out = new ArrayList<>();
        for (Row r : rows) if (!r.isSynthetic) out.add(r);
        return java.util.Collections.unmodifiableList(out);
    }

    public void setCollapseSynthetic(boolean v) { this.collapsedSynthetic = v; }
    public boolean isCollapsedSynthetic() { return collapsedSynthetic; }

    public void setHighlighted(int idx) {
        if (idx >= 0 && idx < rows.size()) highlightedIndex = idx;
    }

    public int highlightedIndex() { return highlightedIndex; }

    public Row highlightedRow() {
        if (highlightedIndex < 0 || highlightedIndex >= rows.size()) return null;
        return rows.get(highlightedIndex);
    }

    public Row top() { return rows.isEmpty() ? null : rows.get(0); }

    public int size() { return rows.size(); }
}
