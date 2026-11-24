package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 重命名重构（Rename Refactoring）：跨文件重命名符号。
 *
 *  算法：
 *  1. FindReferencesService 查找所有引用
 *  2. 排除声明处的位置（可选：保留 - 声明也要改名）
 *  3. 对每个引用生成 TextEdit（oldText → newText）
 *  4. 返回一个 WorkspaceEdit（按文件分组的 edits 列表）
 *
 *  文本精确替换算法：使用 SourceRange 锁定位置 + 长度，构造 Edit。
 *  为保证唯一性，仅在 (file, line, column, length) 完全匹配时才替换。
 */
public final class RenameService {

    public static final class TextEdit {
        public final String file;
        public final int startLine;
        public final int startColumn;
        public final int endLine;
        public final int endColumn;
        public final String oldText;
        public final String newText;

        public TextEdit(String file, int sLine, int sCol, int eLine, int eCol, String oldText, String newText) {
            this.file = file; this.startLine = sLine; this.startColumn = sCol;
            this.endLine = eLine; this.endColumn = eCol;
            this.oldText = oldText; this.newText = newText;
        }
    }

    public static final class WorkspaceEdit {
        public final List<TextEdit> edits;
        public final String oldName;
        public final String newName;
        public WorkspaceEdit(List<TextEdit> edits, String oldName, String newName) {
            this.edits = edits; this.oldName = oldName; this.newName = newName;
        }
        public int totalChanges() { return edits.size(); }
    }

    private final ProjectIndex index;

    public RenameService(ProjectIndex index) {
        this.index = index;
    }

    public WorkspaceEdit rename(String filePath, int line, int column, String newName) {
        if (newName == null || newName.isEmpty()) return new WorkspaceEdit(Collections.emptyList(), "", "");
        if (index == null) return new WorkspaceEdit(Collections.emptyList(), "", "");
        FindReferencesService find = new FindReferencesService(index);
        List<FindReferencesService.Match> refs = find.findReferences(filePath, line, column, true);
        if (refs.isEmpty()) return new WorkspaceEdit(Collections.emptyList(), "", "");

        String oldName = refs.get(0).reference.name;
        // 简单名（如果 oldName 是 FQN，则取最后一段）
        String oldSimple = oldName.contains(".") ? oldName.substring(oldName.lastIndexOf('.') + 1) : oldName;
        String newSimple = newName.contains(".") ? newName.substring(newName.lastIndexOf('.') + 1) : newName;

        List<TextEdit> edits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FindReferencesService.Match m : refs) {
            if (m.reference.range == null) continue;
            SourceRange r = m.reference.range;
            String key = m.file + ":" + r.start.line + ":" + r.start.column + ":" + r.end.line + ":" + r.end.column;
            if (!seen.add(key)) continue;
            // 决定替换的文本：对于 FQN 引用，我们只替换简单名（避免破坏 FQN 前缀）
            String oldToReplace = chooseReplaceText(m.reference.name, oldSimple);
            edits.add(new TextEdit(m.file, r.start.line, r.start.column,
                    r.end.line, r.end.column, oldToReplace, newSimple));
        }
        return new WorkspaceEdit(edits, oldName, newName);
    }

    /**
     * 给定 Reference 的 name（如 com.x.Foo 或 Foo），决定实际要替换的子串。
     * 如果 name 是 FQN（包含 .），则从右向左找到最后一段作为 oldSimple 替换。
     */
    private String chooseReplaceText(String refName, String oldSimple) {
        if (refName == null) return oldSimple;
        if (refName.equals(oldSimple)) return oldSimple;
        if (refName.endsWith("." + oldSimple)) return oldSimple;
        if (refName.endsWith("$" + oldSimple)) return oldSimple;
        return oldSimple;
    }

    /**
     * 应用一个 WorkspaceEdit 到给定文本（仅用于测试 / 模拟）。
     * 真实应用应使用编辑器侧的 TextBuffer。
     */
    public String applyToText(WorkspaceEdit edit, String filePath, String sourceText) {
        String result = sourceText;
        for (TextEdit e : edit.edits) {
            if (!e.file.equals(filePath)) continue;
            result = result.replace(e.oldText, e.newText);
        }
        return result;
    }
}
