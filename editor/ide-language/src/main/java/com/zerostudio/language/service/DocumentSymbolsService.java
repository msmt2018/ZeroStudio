package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文档符号（Document Symbols）：列出当前文件的所有符号（class / method / field / variable）。
 * 用于 IDE 大纲视图 (Outline) 与文件结构弹窗。
 */
public final class DocumentSymbolsService {

    public static final class Symbol {
        public final String name;
        public final String detail;       // 类型 / 返回类型
        public final Kind kind;
        public final SourceRange range;
        public final SourceRange selectionRange;  // 名称自身的位置
        public final List<Symbol> children;

        public Symbol(String name, String detail, Kind kind,
                      SourceRange range, SourceRange selectionRange, List<Symbol> children) {
            this.name = name; this.detail = detail; this.kind = kind;
            this.range = range; this.selectionRange = selectionRange;
            this.children = children == null ? Collections.emptyList() : children;
        }
    }

    public enum Kind { FILE, CLASS, INTERFACE, ENUM, METHOD, FIELD, VARIABLE, FUNCTION, PROPERTY }

    private final ProjectIndex index;

    public DocumentSymbolsService(ProjectIndex index) {
        this.index = index;
    }

    public List<Symbol> listSymbols(String filePath) {
        if (index == null) return Collections.emptyList();
        ParsedFile file = index.fileForPath(filePath);
        if (file == null || file.references == null) return Collections.emptyList();
        List<Symbol> result = new ArrayList<>();
        Symbol fileSymbol = new Symbol(filePath, file.packageName, Kind.FILE,
                new SourceRange(
                        new com.zerostudio.language.model.SourcePosition(filePath, 1, 1),
                        new com.zerostudio.language.model.SourcePosition(filePath, Integer.MAX_VALUE, 1)),
                new SourceRange(
                        new com.zerostudio.language.model.SourcePosition(filePath, 1, 1),
                        new com.zerostudio.language.model.SourcePosition(filePath, 1, 1)),
                new ArrayList<>());
        for (Reference r : file.references) {
            if (r.range == null) continue;
            Kind k = toKind(r.kind);
            if (k == Kind.VARIABLE) continue; // 不显示普通变量
            fileSymbol.children.add(new Symbol(r.name, r.kind.name(), k, r.range, r.range,
                    new ArrayList<>()));
        }
        result.add(fileSymbol);
        return result;
    }

    private Kind toKind(Reference.ReferenceKind rk) {
        switch (rk) {
            case CLASS:    return Kind.CLASS;
            case METHOD:   return Kind.METHOD;
            case FIELD:    return Kind.FIELD;
            case TYPE:     return Kind.INTERFACE;
            case VARIABLE: return Kind.VARIABLE;
            case IMPORT:   return Kind.PROPERTY;
            case PARAMETER:return Kind.VARIABLE;
            default:       return Kind.VARIABLE;
        }
    }
}
