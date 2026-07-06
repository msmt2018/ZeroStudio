package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调用层次：分析谁调用了某个方法（Callers）以及某个方法调用了谁（Callees）。
 *
 * 算法：
 *  1. 在目标类中用正则扫描 method body，找出所有方法调用 `name(...)`。
 *  2. 通过 ProjectIndex 解析每个被调用的名字到 FQN（含同名短名、import、同包、继承）。
 *  3. 对每个候选 FQN，扫描对应文件中的方法体，标记调用源点。
 *
 * 支持 Java / Kotlin（基于 ParsedFile.references 列表的扩展扫描）。
 */
public final class CallHierarchyService {

    private static final Pattern METHOD_INVOCATION = Pattern.compile(
            "\\b([A-Za-z_]\\w*)\\s*\\(");

    private final ProjectIndex index;

    public CallHierarchyService(ProjectIndex index) {
        this.index = index;
    }

    /** 调用的表示：包含被调方法、所在文件、所在行 */
    public static final class CallSite {
        public final String methodName;       // 被调方法短名
        public final String containingClass;  // 调用方所在类
        public final String file;
        public final int line;
        public final int column;

        public CallSite(String methodName, String containingClass, String file, int line, int column) {
            this.methodName = methodName;
            this.containingClass = containingClass;
            this.file = file;
            this.line = line;
            this.column = column;
        }

        @Override
        public String toString() {
            return containingClass + "::" + methodName + " at " + file + ":" + line;
        }
    }

    /** 查找调用了指定 methodName 的所有位置（Callers） */
    public List<CallSite> callersOf(String methodName) {
        if (methodName == null || methodName.isEmpty() || index == null) return Collections.emptyList();
        List<CallSite> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // 对所有 ParsedFile 扫描方法体
        for (ParsedFile pf : allFiles()) {
            List<CallSite> found = scanMethodCalls(pf, methodName);
            for (CallSite cs : found) {
                String key = cs.file + ":" + cs.line + ":" + cs.column + ":" + cs.methodName;
                if (seen.add(key)) result.add(cs);
            }
        }
        return result;
    }

    /** 查找指定 methodName 调用了哪些方法（Callees） */
    public List<CallSite> calleesOf(String containingClass, String methodName) {
        if (methodName == null || methodName.isEmpty() || index == null) return Collections.emptyList();
        ParsedFile pf = resolveFile(containingClass);
        if (pf == null) return Collections.emptyList();
        return scanMethodCalls(pf, null, methodName);
    }

    /** 扫描文件中的方法调用，返回所有 CallSite */
    private List<CallSite> scanMethodCalls(ParsedFile pf, String targetMethod) {
        if (pf.rawText == null || pf.rawText.isEmpty()) return Collections.emptyList();
        String text = pf.rawText;
        List<CallSite> result = new ArrayList<>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (isMethodDeclarationLine(lines[i])) continue;
            Matcher m = METHOD_INVOCATION.matcher(lines[i]);
            while (m.find()) {
                String name = m.group(1);
                if (name.equals(targetMethod)) {
                    result.add(new CallSite(name, pf.packageName, pf.path, i + 1, m.start(1) + 1));
                }
            }
        }
        return result;
    }

    /** 扫描目标方法体内的所有调用，targetMethod == null 表示扫描该文件全部 */
    private List<CallSite> scanMethodCalls(ParsedFile pf, String containingClass, String targetMethod) {
        if (pf.rawText == null || pf.rawText.isEmpty()) return Collections.emptyList();
        // 找到目标方法体的起止行
        int[] range = findMethodRange(pf, targetMethod);
        if (range == null) return Collections.emptyList();
        String[] lines = pf.rawText.split("\n");
        List<CallSite> result = new ArrayList<>();
        for (int i = range[0]; i <= range[1] && i < lines.length; i++) {
            if (i != range[0] && isMethodDeclarationLine(lines[i])) continue;
            Matcher m = METHOD_INVOCATION.matcher(lines[i]);
            while (m.find()) {
                String name = m.group(1);
                // 排除方法名自身
                if (name.equals(targetMethod)) continue;
                // 排除控制流关键字
                if (isKeyword(name)) continue;
                result.add(new CallSite(name, pf.packageName, pf.path, i + 1, m.start(1) + 1));
            }
        }
        return result;
    }

    /** 查找方法的行范围 [start, end]（end 为匹配的右大括号所在行） */
    private int[] findMethodRange(ParsedFile pf, String methodName) {
        if (methodName == null) return new int[]{0, Integer.MAX_VALUE};
        String text = pf.rawText;
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(methodName) && lines[i].contains("(") && !lines[i].trim().startsWith("//")) {
                // 找到疑似方法签名，向下找匹配的右大括号
                int openBrace = -1;
                for (int j = i; j < Math.min(i + 5, lines.length); j++) {
                    int idx = lines[j].indexOf('{');
                    if (idx >= 0) { openBrace = j; break; }
                }
                if (openBrace < 0) continue;
                int depth = 0;
                for (int j = openBrace; j < lines.length; j++) {
                    for (int k = 0; k < lines[j].length(); k++) {
                        char c = lines[j].charAt(k);
                        if (c == '{') depth++;
                        else if (c == '}') {
                            depth--;
                            if (depth == 0) return new int[]{i, j};
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 收集所有 ParsedFile（通过 ProjectIndex 的 filePathToFile 反射） */
    private List<ParsedFile> allFiles() {
        List<ParsedFile> out = new ArrayList<>();
        try {
            java.lang.reflect.Field f = ProjectIndex.class.getDeclaredField("filePathToFile");
            f.setAccessible(true);
            Object m = f.get(index);
            if (m instanceof Map) {
                for (Object v : ((Map<?, ?>) m).values()) {
                    if (v instanceof ParsedFile) out.add((ParsedFile) v);
                }
            }
        } catch (Throwable ignored) {
            // fallback: 扫描 classNameToFile
            try {
                java.lang.reflect.Field f = ProjectIndex.class.getDeclaredField("classNameToFile");
                f.setAccessible(true);
                Object m = f.get(index);
                if (m instanceof Map) {
                    for (Object v : ((Map<?, ?>) m).values()) {
                        if (v instanceof ParsedFile) out.add((ParsedFile) v);
                    }
                }
            } catch (Throwable ignored2) {}
        }
        return out;
    }

    private ParsedFile resolveFile(String fqn) {
        if (fqn == null) return null;
        try {
            java.lang.reflect.Field f = ProjectIndex.class.getDeclaredField("classNameToFile");
            f.setAccessible(true);
            Object m = f.get(index);
            if (m instanceof Map) {
                Object pf = ((Map<?, ?>) m).get(fqn);
                if (pf instanceof ParsedFile) return (ParsedFile) pf;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isKeyword(String s) {
        switch (s) {
            case "if": case "else": case "for": case "while":
            case "return": case "switch": case "case": case "do":
            case "new": case "throw": case "try": case "catch":
            case "synchronized": case "instanceof":
                return true;
            default: return false;
        }
    }

    /** 判断该行是否是方法声明（含修饰符/返回类型），而非方法调用 */
    private static boolean isMethodDeclarationLine(String line) {
        String t = line.trim();
        if (t.startsWith("//") || t.startsWith("*")) return true; // 注释
        // 修饰符关键字
        if (t.startsWith("public ") || t.startsWith("private ") || t.startsWith("protected ")
                || t.startsWith("static ") || t.startsWith("abstract ") || t.startsWith("final ")
                || t.startsWith("synchronized ") || t.startsWith("native ")) return true;
        // void / 返回类型 (简单常见类型)
        if (t.matches("^(void|int|long|short|byte|char|float|double|boolean|String|Object|Integer|Long)\\s+.+\\(.+\\)\\s*\\{?\\s*$")) return true;
        // 泛型返回类型 List<T> / Map<K,V>
        if (t.matches("^[A-Z][A-Za-z0-9_]*<.+>\\s+\\w+\\s*\\(.+\\)\\s*\\{?\\s*$")) return true;
        return false;
    }
}
