package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.source.TransitiveSymbolResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 引用查找（Find References）：基于 ProjectIndex 跨文件查找符号的所有引用位置。
 *
 *  算法：
 *  1. 给定文件路径 + 行/列，定位到当前位置的 Reference
 *  2. 计算所有可能的 FQN（短名 + 完全限定 + 同包 + import + star import + 外层类）
 *  3. 在 ProjectIndex 中查询匹配所有 FQN 的 Reference
 *  4. 返回带文件的 SourcePosition 列表
 *
 *  支持：
 *  - 简单名匹配（默认）
 *  - FQN 匹配（精确）
 *  - 同包引用（同包内未 import 也能找到）
 *  - star import（import com.foo.*）
 *  - 嵌套类（Outer.Inner 与 Outer$Inner）
 */
public final class FindReferencesService {

    public static final class Match {
        public final String file;
        public final Reference reference;
        public Match(String file, Reference ref) {
            this.file = file; this.reference = ref;
        }
        public int line() { return reference.range.start.line; }
        public int column() { return reference.range.start.column; }
        public SourcePosition position() { return reference.range.start; }
    }

    private final ProjectIndex index;
    private final TransitiveSymbolResolver resolver;

    public FindReferencesService(ProjectIndex index) {
        this.index = index;
        this.resolver = new TransitiveSymbolResolver();
    }

    /**
     * 在文件 filePath 的 (line, column) 位置查找所有引用。
     * @param includeDeclaration 是否把声明本身也算作一处引用
     */
    public List<Match> findReferences(String filePath, int line, int column, boolean includeDeclaration) {
        if (index == null) return Collections.emptyList();
        ParsedFile file = index.fileForPath(filePath);
        if (file == null) return Collections.emptyList();
        Reference hit = findReferenceAt(file, line, column);
        if (hit == null) return Collections.emptyList();

        Set<String> candidates = expandCandidates(file, hit);
        List<Match> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String fqn : candidates) {
            // 1. 直接在 index 中按 FQN 匹配
            collectFor(fqn, includeDeclaration, hit, result, seen);
            // 2. 通过 simpleName 匹配（imports, classes, methods）
            collectFor(fqn.substring(fqn.lastIndexOf('.') + 1), includeDeclaration, hit, result, seen);
        }
        return result;
    }

    /**
     * 给定一个符号名（任意上下文），跨文件查找所有同名引用。
     */
    public List<Match> findByName(String name, boolean includeDeclaration) {
        if (index == null || name == null || name.isEmpty()) return Collections.emptyList();
        List<Match> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectFor(name, includeDeclaration, null, result, seen);
        return result;
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

    private Set<String> expandCandidates(ParsedFile file, Reference hit) {
        Set<String> out = new HashSet<>();
        if (hit.name == null) return out;
        // 1. 自身 FQN（package + name）
        if (file.packageName != null && !file.packageName.isEmpty()
                && !hit.name.contains(".")) {
            out.add(file.packageName + "." + hit.name);
        }
        out.add(hit.name);
        // 2. 简单名（import、class、method、field 都可以按 simple 名匹配）
        int dot = hit.name.lastIndexOf('.');
        if (dot >= 0) out.add(hit.name.substring(dot + 1));
        // 3. 同一文件内所有 import 引用的 FQN
        if (file.references != null) {
            for (Reference r : file.references) {
                if (r.kind == Reference.ReferenceKind.IMPORT) {
                    String imp = r.name;
                    int end = imp.length();
                    // 简单名
                    int last = imp.lastIndexOf('.');
                    if (last >= 0 && imp.substring(last + 1).equals(hit.name)) {
                        out.add(imp);
                    }
                    // star import: import com.foo.*
                    if (imp.endsWith(".*")) {
                        out.add(imp.substring(0, imp.length() - 1) + hit.name);
                    }
                }
            }
        }
        // 4. 同包（reference 自身是简单名时）
        if (file.packageName != null && !file.packageName.isEmpty()
                && !hit.name.contains(".")) {
            out.add(file.packageName + "." + hit.name);
        }
        // 5. nested class 别名（Outer.Inner ↔ Outer$Inner）
        if (hit.name.contains(".")) {
            out.add(hit.name.replace('.', '$'));
        }
        return out;
    }

    private void collectFor(String fqn, boolean includeDeclaration, Reference selfHit,
                            List<Match> result, Set<String> seen) {
        if (fqn == null || fqn.isEmpty()) return;
        // 按 FQN 匹配
        for (java.util.Map.Entry<String, ParsedFile> entry : index.allFiles()) {
            ParsedFile pf = entry.getValue();
            if (pf.references == null) continue;
            for (Reference r : pf.references) {
                if (!matches(fqn, r)) continue;
                if (!includeDeclaration && isDeclaration(r)) continue;
                if (selfHit != null && sameLocation(selfHit, r) && selfHit.kind == r.kind) {
                    // includeDeclaration=true 时包含当前位置的引用（用户要重命名当前位置）
                    if (!includeDeclaration) continue;
                }
                String key = pf.path + ":" + r.range.start.line + ":" + r.range.start.column;
                if (seen.add(key)) result.add(new Match(pf.path, r));
            }
        }
    }

    private boolean matches(String fqn, Reference r) {
        if (fqn.equals(r.name)) return true;
        if (r.name != null && r.name.equals(fqn)) return true;
        // 短名匹配（按 simple name）
        if (!fqn.contains(".") && r.name != null) {
            int dot = r.name.lastIndexOf('.');
            String simple = dot >= 0 ? r.name.substring(dot + 1) : r.name;
            if (simple.equals(fqn)) return true;
        }
        return false;
    }

    private boolean isDeclaration(Reference r) {
        return r.kind == Reference.ReferenceKind.CLASS
                || r.kind == Reference.ReferenceKind.METHOD
                || r.kind == Reference.ReferenceKind.TYPE;
    }

    private boolean sameLocation(Reference a, Reference b) {
        if (a.range == null || b.range == null || a.range.start == null || b.range.start == null) return false;
        return a.range.start.line == b.range.start.line
                && a.range.start.column == b.range.start.column;
    }

    /** 工具：统计匹配数（供 UI 状态栏显示） */
    public int count(String filePath, int line, int column) {
        return findReferences(filePath, line, column, true).size();
    }
}
