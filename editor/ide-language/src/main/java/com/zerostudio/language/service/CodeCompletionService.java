package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 代码补全（Code Completion）：基于 ProjectIndex 的符号补全。
 *
 *  触发：
 *  - 在类的 body 内、方法内、import 后输入时
 *  - 输入的前缀 prefix 决定候选项过滤
 *
 *  排序：
 *  - 精确匹配（同包 + 简单名相同）排最前
 *  - 前缀匹配
 *  - 模糊匹配
 *  - 按类型分组（class / method / field / variable / keyword）
 *
 *  上下文感知：
 *  - 在 import 位置 → 补全包名 / import 路径
 *  - 在 . 之后 → 补全成员（基于 FQN）
 *  - 在 : 之后（Kotlin）→ 补全继承 / 实现
 */
public final class CodeCompletionService {

    public static final class Item {
        public final String label;       // 显示文本
        public final String insertText;  // 实际插入的文本
        public final String detail;      // 副标题（类型 / FQN）
        public final Kind kind;          // 类别
        public final int priority;       // 越大越靠前

        public Item(String label, String insertText, String detail, Kind kind, int priority) {
            this.label = label;
            this.insertText = insertText;
            this.detail = detail;
            this.kind = kind;
            this.priority = priority;
        }
    }

    public enum Kind {
        CLASS, METHOD, FIELD, VARIABLE, PACKAGE, KEYWORD, INTERFACE, ENUM, ANNOTATION, SNIPPET
    }

    public enum Context { UNKNOWN, IMPORT, MEMBER_ACCESS, TYPE_POSITION, EXPRESSION }

    private final ProjectIndex index;

    public CodeCompletionService(ProjectIndex index) {
        this.index = index;
    }

    public List<Item> complete(String filePath, int line, int column, String prefix) {
        if (index == null) return new ArrayList<>();
        ParsedFile file = index.fileForPath(filePath);
        Context ctx = detectContext(file, line, column, prefix);
        List<Item> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. 当前文件的 symbols（变量 / 类 / 方法）
        if (file != null) {
            addFileItems(file, prefix, items, seen);
        }
        // 2. 索引中的所有 class
        addClassItems(file, prefix, items, seen);
        // 3. 索引中的所有 method
        addMethodItems(prefix, items, seen);
        // 4. Context-specific
        if (ctx == Context.IMPORT) addImportItems(prefix, items, seen);
        if (ctx == Context.MEMBER_ACCESS) addMemberAccessItems(file, prefix, items, seen);

        // 5. 通用关键字
        addKeywords(prefix, items, seen);

        // 按优先级 + 字母排序
        items.sort(Comparator.comparingInt((Item i) -> -i.priority).thenComparing(i -> i.label));
        return items;
    }

    public Context detectContext(ParsedFile file, int line, int column, String prefix) {
        if (prefix != null && prefix.startsWith("import ")) return Context.IMPORT;
        if (prefix != null && prefix.endsWith(".")) return Context.MEMBER_ACCESS;
        if (prefix != null && (prefix.contains(" : ") || prefix.contains("extends ") || prefix.contains("implements "))) {
            return Context.TYPE_POSITION;
        }
        return Context.EXPRESSION;
    }

    private void addFileItems(ParsedFile file, String prefix, List<Item> items, Set<String> seen) {
        if (file == null || file.references == null) return;
        for (Reference r : file.references) {
            if (r.name == null) continue;
            if (!matches(prefix, r.name)) continue;
            Kind kind = toKind(r.kind);
            if (!seen.add(r.name + ":" + kind)) continue;
            int prio = score(prefix, r.name, 100);
            items.add(new Item(r.name, r.name, file.packageName + "." + r.name, kind, prio));
        }
    }

    private void addClassItems(ParsedFile currentFile, String prefix, List<Item> items, Set<String> seen) {
        for (String fqn : index.allClasses()) {
            if (fqn == null) continue;
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
            if (!matches(prefix, simple)) continue;
            if (!seen.add(simple + ":CLASS")) continue;
            int prio = score(prefix, simple, 80);
            // 同包优先
            if (currentFile != null && currentFile.packageName != null
                    && fqn.startsWith(currentFile.packageName + ".")) prio += 20;
            items.add(new Item(simple, simple, fqn, Kind.CLASS, prio));
        }
    }

    private void addMethodItems(String prefix, List<Item> items, Set<String> seen) {
        for (java.util.Map.Entry<String, ParsedFile> entry : index.allFiles()) {
            ParsedFile pf = entry.getValue();
            if (pf.references == null) continue;
            for (Reference r : pf.references) {
                if (r.kind != Reference.ReferenceKind.METHOD) continue;
                if (r.name == null) continue;
                if (!matches(prefix, r.name)) continue;
                if (!seen.add(r.name + ":METHOD")) continue;
                int prio = score(prefix, r.name, 50);
                items.add(new Item(r.name + "()", r.name + "()", pf.packageName + "." + r.name, Kind.METHOD, prio));
            }
        }
    }

    private void addImportItems(String prefix, List<Item> items, Set<String> seen) {
        // 收集所有已知的 FQN
        for (String fqn : index.allClasses()) {
            if (fqn == null) continue;
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
            String q = "import " + fqn;
            if (!matches(prefix, q) && !matches(prefix, fqn) && !matches(prefix, simple)) continue;
            if (!seen.add("import:" + fqn)) continue;
            items.add(new Item("import " + fqn, "import " + fqn, fqn, Kind.PACKAGE, score(prefix, fqn, 30)));
        }
    }

    private void addMemberAccessItems(ParsedFile file, String prefix, List<Item> items, Set<String> seen) {
        // 简单实现：补全所有 method/field（真实生产需基于类型解析）
        for (java.util.Map.Entry<String, ParsedFile> entry : index.allFiles()) {
            ParsedFile pf = entry.getValue();
            if (pf.references == null) continue;
            for (Reference r : pf.references) {
                if (r.kind != Reference.ReferenceKind.METHOD && r.kind != Reference.ReferenceKind.FIELD) continue;
                if (r.name == null) continue;
                if (!matches(prefix, r.name)) continue;
                if (!seen.add("member:" + r.name)) continue;
                Kind k = r.kind == Reference.ReferenceKind.METHOD ? Kind.METHOD : Kind.FIELD;
                items.add(new Item(r.name, r.name, pf.packageName + "." + r.name, k, score(prefix, r.name, 60)));
            }
        }
    }

    private void addKeywords(String prefix, List<Item> items, Set<String> seen) {
        String[] kws = {"class", "interface", "fun", "val", "var", "if", "else", "for", "while", "return", "object", "package", "import", "private", "public", "protected", "internal"};
        for (String k : kws) {
            if (!matches(prefix, k)) continue;
            if (!seen.add("kw:" + k)) continue;
            items.add(new Item(k, k, "keyword", Kind.KEYWORD, score(prefix, k, 10)));
        }
    }

    private boolean matches(String prefix, String name) {
        if (name == null) return false;
        if (prefix == null || prefix.isEmpty()) return true;
        String p = prefix.toLowerCase(Locale.ROOT);
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith(p)) return true;
        // 模糊匹配：prefix 中字符在 name 中按顺序出现
        int pi = 0;
        for (int i = 0; i < n.length() && pi < p.length(); i++) {
            if (n.charAt(i) == p.charAt(pi)) pi++;
        }
        return pi == p.length();
    }

    private int score(String prefix, String name, int base) {
        if (prefix == null || prefix.isEmpty()) return base;
        String p = prefix.toLowerCase(Locale.ROOT);
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals(p)) return base + 200;
        if (n.startsWith(p)) return base + 100;
        if (p.startsWith(n)) return base + 50;
        return base;
    }

    private Kind toKind(Reference.ReferenceKind rk) {
        switch (rk) {
            case CLASS:    return Kind.CLASS;
            case METHOD:   return Kind.METHOD;
            case FIELD:    return Kind.FIELD;
            case VARIABLE: return Kind.VARIABLE;
            case IMPORT:   return Kind.PACKAGE;
            case TYPE:     return Kind.INTERFACE;
            case PARAMETER:return Kind.VARIABLE;
            default:       return Kind.VARIABLE;
        }
    }
}
