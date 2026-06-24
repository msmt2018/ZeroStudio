package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类型层次（Type Hierarchy）：基于 extends / implements 关系构建类继承树。
 *
 *  数据来源：
 *  - ProjectIndex 中的 CLASS reference (class A extends B implements C)
 *  - TYPE reference（interface 声明）
 *  - 使用简单的文本扫描：对于 class X extends/implements Y，从同一个文件 / 行中提取 Y
 *
 *  输出：
 *  - supertypesOf(fqn): 所有父类 / 接口
 *  - subtypesOf(fqn): 所有直接 / 间接子类
 *  - hierarchyOf(fqn): 完整树
 */
public final class TypeHierarchyService {

    public static final class Node {
        public final String fqn;
        public final List<Node> parents;
        public final List<Node> children;
        public Node(String fqn) {
            this.fqn = fqn; this.parents = new ArrayList<>(); this.children = new ArrayList<>();
        }
    }

    private final ProjectIndex index;
    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<String, Set<String>> superCache = new HashMap<>();
    private final Map<String, Set<String>> subCache = new HashMap<>();

    public TypeHierarchyService(ProjectIndex index) {
        this.index = index;
    }

    public Set<String> supertypesOf(String fqn) {
        if (fqn == null) return Collections.emptySet();
        if (superCache.containsKey(fqn)) return superCache.get(fqn);
        Set<String> result = new HashSet<>();
        collectSupers(fqn, result, new HashSet<>());
        superCache.put(fqn, result);
        return result;
    }

    public Set<String> subtypesOf(String fqn) {
        if (fqn == null) return Collections.emptySet();
        if (subCache.containsKey(fqn)) return subCache.get(fqn);
        Set<String> result = new HashSet<>();
        collectSubs(fqn, result, new HashSet<>());
        subCache.put(fqn, result);
        return result;
    }

    private void collectSupers(String fqn, Set<String> result, Set<String> visited) {
        if (!visited.add(fqn)) return;
        ParsedFile pf = index == null ? null : index.fileFor(fqn);
        if (pf == null || pf.rawText == null) return;
        // 扫描整个文件内容，提取 class X extends Y implements Z 行
        String[] lines = pf.rawText.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.contains("class ") && !trimmed.contains("interface ")
                    && !trimmed.contains("object ")) continue;
            String[] supers = extractSupers(trimmed);
            for (String s : supers) {
                if (s == null || s.isEmpty()) continue;
                // 本地名 → FQN（如果未限定）
                String resolved = s;
                if (!s.contains(".")) {
                    if (pf.packageName != null && !pf.packageName.isEmpty()) {
                        String tryFqn = pf.packageName + "." + s;
                        // 仅在索引中存在时才使用 FQN
                        if (index != null && index.fileFor(tryFqn) != null) {
                            resolved = tryFqn;
                        } else {
                            // 尝试 java.lang.* 默认包
                            if (isJavaLang(s)) resolved = "java.lang." + s;
                        }
                    }
                }
                if (resolved.isEmpty()) continue;
                if (!result.contains(resolved)) {
                    result.add(resolved);
                    collectSupers(resolved, result, visited);
                }
            }
        }
    }

    private static boolean isJavaLang(String name) {
        switch (name) {
            case "String": case "Integer": case "Long": case "Double": case "Float":
            case "Boolean": case "Byte": case "Short": case "Character": case "Object":
            case "Throwable": case "Exception": case "RuntimeException":
            case "System": case "Math": case "Class": case "Number":
                return true;
            default: return false;
        }
    }

    private void collectSubs(String fqn, Set<String> result, Set<String> visited) {
        if (index == null) return;
        for (String candidate : index.allClasses()) {
            if (candidate == null || candidate.equals(fqn)) continue;
            Set<String> supers = supertypesOf(candidate);
            if (supers.contains(fqn) && !visited.contains(candidate)) {
                result.add(candidate);
                visited.add(candidate);
                collectSubs(candidate, result, visited);
            }
        }
    }

    private String[] extractSupers(String line) {
        List<String> out = new ArrayList<>();
        int idx = line.indexOf("extends");
        if (idx >= 0) {
            String after = line.substring(idx + "extends".length()).trim();
            // 第一个 word / FQN
            out.add(extractNextIdent(after));
        }
        idx = line.indexOf("implements");
        if (idx >= 0) {
            String after = line.substring(idx + "implements".length()).trim();
            // 多个接口，用逗号分隔
            for (String p : after.split(",")) {
                String t = p.trim();
                // 去掉 implements 后面的 { 
                int brace = t.indexOf('{');
                if (brace >= 0) t = t.substring(0, brace).trim();
                if (!t.isEmpty()) out.add(t);
            }
        }
        // Kotlin 的 : 父类
        int colon = line.indexOf(" : ");
        if (colon > 0 && !line.contains("class ") == false) {
            // 仅在 class 声明行
            if (line.contains("class ")) {
                String after = line.substring(colon + 3).trim();
                for (String p : after.split(",")) {
                    String t = p.trim();
                    int brace = t.indexOf('{');
                    if (brace >= 0) t = t.substring(0, brace).trim();
                    if (!t.isEmpty()) out.add(t);
                }
            }
        }
        return out.toArray(new String[0]);
    }

    private String extractNextIdent(String s) {
        StringBuilder sb = new StringBuilder();
        boolean start = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '{' || c == '<') {
                if (start) break;
                continue;
            }
            start = true;
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private String getLine(String text, int lineNumber) {
        if (text == null) return null;
        String[] lines = text.split("\n");
        if (lineNumber < 1 || lineNumber > lines.length) return null;
        return lines[lineNumber - 1];
    }
}
