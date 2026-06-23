package com.zerostudio.language.eval;

import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Variables 适配层：将 FrameSnapshot 的值映射为表格行（IDE 侧 Variables 面板使用）。
 * 字段：
 *  - name: 变量名
 *  - value: 显示值
 *  - kindLabel: "Local" / "Field" / "Static" / "Param"
 *  - typeName: 完整类型名
 *  - editable: 是否可修改（仅 Local 可写）
 */
public final class VariablesAdapter {

    public static final class Row {
        public final String name;
        public final String value;
        public final String kindLabel;
        public final String typeName;
        public final boolean editable;

        public Row(String name, String value, String kindLabel, String typeName, boolean editable) {
            this.name = name;
            this.value = value;
            this.kindLabel = kindLabel;
            this.typeName = typeName;
            this.editable = editable;
        }
    }

    public List<Row> toRows(FrameSnapshot frame) {
        List<Row> out = new ArrayList<>();
        if (frame == null) return out;
        for (Value v : frame.values().values()) {
            boolean editable = "Local".equalsIgnoreCase(v.kindLabel) || "Param".equalsIgnoreCase(v.kindLabel);
            out.add(new Row(v.name, v.displayValue(), v.kindLabel, v.typeName, editable));
        }
        return out;
    }

    public List<Row> locals(FrameSnapshot frame) { return filter(frame, "Local", "Param"); }
    public List<Row> fields(FrameSnapshot frame) { return filter(frame, "Field"); }
    public List<Row> statics(FrameSnapshot frame) { return filter(frame, "Static"); }

    private List<Row> filter(FrameSnapshot frame, String... kinds) {
        List<Row> all = toRows(frame);
        List<Row> out = new ArrayList<>();
        for (Row r : all) {
            for (String k : kinds) if (k.equalsIgnoreCase(r.kindLabel)) { out.add(r); break; }
        }
        return out;
    }
}
