package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 跨语言符号解析：在多语言项目中从一个语言跳转到另一个语言。
 *
 * 场景：
 *  - Kotlin 文件调用 Java 类：{@code val s: String = JavaClass.method()} → 跳到 JavaClass.java
 *  - Java 文件调用 Kotlin 扩展函数：{@code StringUtil.foo(x)} → 跳到 StringUtil.kt
 *
 * 实现：扫描所有 ParsedFile，对每个未解析的 import 尝试在不同语言的解析器中匹配。
 */
public final class CrossLanguageResolver {

    private final ProjectIndex index;
    private final List<LanguageAdapter> adapters = new ArrayList<>();

    public CrossLanguageResolver(ProjectIndex index) {
        this.index = index;
    }

    public CrossLanguageResolver addAdapter(LanguageAdapter adapter) {
        if (adapter != null) adapters.add(adapter);
        return this;
    }

    /** 把一个源文件里所有未解析的引用尝试跨语言解析 */
    public List<Resolution> resolveMissing(ParsedFile file) {
        List<Resolution> out = new ArrayList<>();
        if (file == null || file.references == null) return out;
        for (Reference r : file.references) {
            if (r.kind != Reference.ReferenceKind.IMPORT) continue;
            Resolution res = resolveImport(file, r);
            if (res != null) out.add(res);
        }
        return out;
    }

    /** 解析单个未解析的 import，跨语言尝试 */
    public Resolution resolveImport(ParsedFile fromFile, Reference importRef) {
        if (importRef == null || importRef.name == null) return null;
        String name = importRef.name;

        // 1. 先在同语言中按 FQN 查找
        if (index != null) {
            ParsedFile direct = index.fileFor(name);
            if (direct != null) {
                return new Resolution(name, direct);
            }
            // 1.1 尝试用同包 + 简名解析（com.x.Foo + Foo → com.x.Foo）
            if (fromFile != null && fromFile.packageName != null && !fromFile.packageName.isEmpty()
                    && !name.contains(".")) {
                ParsedFile samePkg = index.fileFor(fromFile.packageName + "." + name);
                if (samePkg != null) {
                    return new Resolution(name, samePkg);
                }
            }
        }

        // 2. 跨语言：在每个 adapter 中尝试
        for (LanguageAdapter ad : adapters) {
            ParsedFile f = ad.lookup(name);
            if (f != null) {
                return new Resolution(name, f);
            }
        }
        return null;
    }

    /** 跨语言查找包含 methodName 的所有类（不限语言） */
    public List<ParsedFile> findImplementations(String methodName) {
        Set<String> seen = new HashSet<>();
        List<ParsedFile> out = new ArrayList<>();
        if (index == null) return out;
        for (java.util.Map.Entry<String, ParsedFile> e : index.allFiles()) {
            ParsedFile pf = e.getValue();
            if (pf.references == null) continue;
            for (Reference r : pf.references) {
                if (r.kind == Reference.ReferenceKind.METHOD && methodName.equals(r.name)) {
                    if (seen.add(pf.path)) out.add(pf);
                    break;
                }
            }
        }
        return out;
    }

    /** 跨语言查找类（支持简名/FQN/nested） */
    public List<ParsedFile> findClasses(String className) {
        List<ParsedFile> out = new ArrayList<>();
        if (index == null) return out;
        for (java.util.Map.Entry<String, ParsedFile> e : index.allFiles()) {
            ParsedFile pf = e.getValue();
            if (pf.references == null) continue;
            for (Reference r : pf.references) {
                if (r.kind == Reference.ReferenceKind.CLASS && matchesName(r.name, className, pf)) {
                    out.add(pf);
                    break;
                }
            }
        }
        return out;
    }

    private boolean matchesName(String refName, String target, ParsedFile pf) {
        if (refName == null || target == null) return false;
        if (refName.equals(target)) return true;
        if (refName.equals(target.substring(target.lastIndexOf('.') + 1))) {
            // 检查同包
            if (pf.packageName != null
                    && target.startsWith(pf.packageName + ".")
                    && target.substring(pf.packageName.length() + 1).equals(refName)) {
                return true;
            }
            // nested
            if (target.endsWith("." + refName)) return true;
        }
        return false;
    }

    /** 适配器接口：每个语言可以注册自己的查找逻辑 */
    public interface LanguageAdapter {
        ParsedFile lookup(String name);
    }

    /** 解析结果 */
    public static final class Resolution {
        public final String originalName;
        public final ParsedFile resolved;
        public final boolean crossLanguage;

        public Resolution(String originalName, ParsedFile resolved) {
            this.originalName = originalName;
            this.resolved = resolved;
            this.crossLanguage = resolved != null
                    && resolved.language != LanguageId.JAVA
                    && resolved.language != LanguageId.KOTLIN;
        }
    }
}
