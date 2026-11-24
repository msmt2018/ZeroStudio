package com.zerostudio.language.source;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 跨源文件 transitive 查找：当用户点击 `com.x.Y` 而 Y 不存在时，
 * 尝试 `com.x` 作为包名展开到 `com.x.Y1`、`com.x.Y2` 等；
 * 反之，当用户点击 `a.b.c.X` 时，回溯到包 `a.b.c` 找兄弟类。
 */
public final class TransitiveSymbolResolver {

    private static final Pattern DOT = Pattern.compile("\\.");

    /** 从 className 中逐层回溯，返回所有候选名（含原名） */
    public Set<String> expandCandidates(String className) {
        Set<String> out = new LinkedHashSet<>();
        if (className == null || className.isEmpty()) return out;
        out.add(className);
        String[] parts = DOT.split(className);
        // 尝试 a.b.c.d → a.b.c.d, a.b.c, a.b, a
        for (int i = parts.length - 1; i > 0; i--) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) {
                if (j > 0) sb.append('.');
                sb.append(parts[j]);
            }
            out.add(sb.toString());
        }
        return out;
    }

    /**
     * 沿着 import 链向下递归查找：
     *   com.a → resolve(com.a) → 失败时 → expandCandidates(com.a) → 试 .* 后缀
     */
    public Set<String> followImports(String simpleName, Set<String> importedNames) {
        Set<String> out = new LinkedHashSet<>();
        if (importedNames == null) return out;
        for (String imp : importedNames) {
            if (imp.endsWith(".*")) {
                String pkg = imp.substring(0, imp.length() - 2);
                out.add(pkg + "." + simpleName);
            } else {
                String tail = imp.substring(imp.lastIndexOf('.') + 1);
                if (tail.equals(simpleName)) {
                    out.add(imp);
                }
            }
        }
        return out;
    }

    /** 包级解析：找出 sourceRoot 下某包的所有源文件 */
    public Set<String> expandPackageWildcard(String packagePrefix, java.util.List<String> knownClassNames) {
        Set<String> out = new LinkedHashSet<>();
        if (packagePrefix == null || knownClassNames == null) return out;
        for (String c : knownClassNames) {
            if (c.startsWith(packagePrefix + ".")) out.add(c);
        }
        return out;
    }

    /** 工具：检测两个 FQN 是否同包 */
    public boolean isSamePackage(String a, String b) {
        if (a == null || b == null) return false;
        int ai = a.lastIndexOf('.');
        int bi = b.lastIndexOf('.');
        String pa = ai >= 0 ? a.substring(0, ai) : "";
        String pb = bi >= 0 ? b.substring(0, bi) : "";
        return pa.equals(pb);
    }

    /**
     * 处理 nested class / inner class：给定外部类 "com.x.Outer" 和简单名 "Inner"，
     * 返回所有可能的 FQN：com.x.Outer.Inner, com.x.Outer$Inner。
     * Java 字节码层面，嵌套类编译后是 Outer$Inner，但源码中是 Outer.Inner。
     */
    public java.util.Set<String> expandNestedClass(String outerFqn, String simpleName) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (outerFqn == null || simpleName == null) return out;
        out.add(outerFqn + "." + simpleName);
        out.add(outerFqn + "$" + simpleName);
        // 也支持更深层嵌套：outer.Inner.Nested
        out.add(outerFqn + "." + simpleName + ".Nested");
        out.add(outerFqn + "$" + simpleName + "$Nested");
        return out;
    }

    /**
     * 给定一个 FQN 字符串，检测它是否引用了 outer.inner 形式，并返回 outer FQN。
     * 例：com.x.Outer.Inner → com.x.Outer
     *     com.x.Outer$Inner → com.x.Outer
     * 简单名（无点）返回空。
     */
    public String outerOf(String fqn) {
        if (fqn == null) return "";
        int dot = fqn.lastIndexOf('.');
        int dollar = fqn.lastIndexOf('$');
        int cut = Math.max(dot, dollar);
        return cut > 0 ? fqn.substring(0, cut) : "";
    }

    private static Set<String> newHashSet() { return new HashSet<>(); }
}
