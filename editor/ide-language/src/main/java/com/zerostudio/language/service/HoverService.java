package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 悬浮提示（Hover）：鼠标悬停时显示符号的文档/类型/签名。
 *
 *  数据来源：
 *  - ProjectIndex 中的 Reference（kind + package + file）
 *  - 对于 class → 显示 "class package.Name extends ... implements ..."
 *  - 对于 method → 显示签名 "method(params) → returnType"
 *  - 对于 field → 显示类型 + 文档注释（如果有）
 */
public final class HoverService {

    public static final class HoverInfo {
        public final String title;          // 主标题（符号名）
        public final String subtitle;       // 副标题（类型 / 签名）
        public final String description;    // 详细描述（文档 + 修饰符）
        public final SourceRange range;     // 触发位置
        public final Reference reference;   // 触发的符号

        public HoverInfo(String title, String subtitle, String description,
                         SourceRange range, Reference reference) {
            this.title = title; this.subtitle = subtitle; this.description = description;
            this.range = range; this.reference = reference;
        }
    }

    private final ProjectIndex index;

    public HoverService(ProjectIndex index) {
        this.index = index;
    }

    public HoverInfo hover(String filePath, int line, int column) {
        if (index == null) return null;
        ParsedFile file = index.fileForPath(filePath);
        if (file == null) return null;
        Reference hit = findReferenceAt(file, line, column);
        if (hit == null) return null;

        String fqn = (file.packageName != null && !file.packageName.isEmpty()
                && !hit.name.contains("."))
                ? file.packageName + "." + hit.name
                : hit.name;
        ParsedFile declFile = index.fileFor(fqn);

        switch (hit.kind) {
            case CLASS:    return classHover(hit, file, declFile, fqn);
            case METHOD:   return methodHover(hit, file, declFile, fqn);
            case FIELD:    return fieldHover(hit, file, declFile, fqn);
            case VARIABLE: return variableHover(hit, file, declFile, fqn);
            case TYPE:     return typeHover(hit, file, declFile, fqn);
            case IMPORT:   return importHover(hit, file);
            default:       return genericHover(hit, file, declFile, fqn);
        }
    }

    private HoverInfo classHover(Reference hit, ParsedFile file, ParsedFile decl, String fqn) {
        String modifiers = "public";  // 简化
        String type = decl != null ? "class" : "class";
        Set<String> supers = new TypeHierarchyService(index).supertypesOf(fqn);
        String superStr = supers.isEmpty() ? "" : " extends " + String.join(", ", supers);
        return new HoverInfo(
                hit.name,
                modifiers + " " + type + " in " + (decl != null ? decl.packageName : file.packageName),
                fqn + superStr,
                hit.range, hit);
    }

    private HoverInfo methodHover(Reference hit, ParsedFile file, ParsedFile decl, String fqn) {
        return new HoverInfo(
                hit.name + "()",
                "method in " + (decl != null ? decl.packageName : file.packageName),
                fqn,
                hit.range, hit);
    }

    private HoverInfo fieldHover(Reference hit, ParsedFile file, ParsedFile decl, String fqn) {
        return new HoverInfo(
                hit.name,
                "field in " + (decl != null ? decl.packageName : file.packageName),
                fqn,
                hit.range, hit);
    }

    private HoverInfo variableHover(Reference hit, ParsedFile file, ParsedFile decl, String fqn) {
        return new HoverInfo(
                hit.name,
                "local variable",
                fqn,
                hit.range, hit);
    }

    private HoverInfo typeHover(Reference hit, ParsedFile file, ParsedFile decl, String fqn) {
        return new HoverInfo(
                hit.name,
                "interface/type in " + (decl != null ? decl.packageName : file.packageName),
                fqn,
                hit.range, hit);
    }

    private HoverInfo importHover(Reference hit, ParsedFile file) {
        return new HoverInfo(
                hit.name,
                "import",
                "Imported class " + hit.name,
                hit.range, hit);
    }

    private HoverInfo genericHover(Reference hit, ParsedFile file, ParsedFile decl, String fqn) {
        return new HoverInfo(hit.name, hit.kind.name(), fqn, hit.range, hit);
    }

    private Reference findReferenceAt(ParsedFile file, int line, int column) {
        for (Reference r : file.references) {
            if (r.range == null || r.range.start == null) continue;
            SourcePosition s = r.range.start;
            SourcePosition e = r.range.end;
            if (line < s.line || line > e.line) continue;
            if (line == s.line && column < s.column) continue;
            if (line == e.line && column > e.column) continue;
            return r;
        }
        return null;
    }
}
