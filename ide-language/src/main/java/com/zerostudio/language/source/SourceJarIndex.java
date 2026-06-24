package com.zerostudio.language.source;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * JAR 内 source-jar 索引：预解析所有 source-jar 中的 .java / .kt 文件，
 * 建立 "方法签名 → 源文件位置" 映射。
 *
 * 用途：
 *  - 调试器命中 .class 的某个方法时，直接在索引中查源码位置，无需扫描整个 jar
 *  - "Go to Implementation" 跨 jar 跳转
 *
 * 索引 key 形式：{@code className#methodName(argType1,argType2)} 或简化的 {@code className#methodName}。
 */
public final class SourceJarIndex {

    private static final Pattern METHOD_DECL = Pattern.compile(
            "(?:(?:public|private|protected|static|abstract|final|synchronized|native|default)\\s+)*"
                    + "(?:[A-Za-z_][\\w$.<>?, ]*|void)\\s+"
                    + "([A-Za-z_]\\w*)\\s*\\(([^)]*)\\)");

    /** 索引条目：方法签名 + 源文件 + 行号 */
    public static final class Entry {
        public final String className;
        public final String methodName;
        public final String signature;       // 原始签名行
        public final String sourceFile;      // jar 内 entry 路径
        public final int line;               // 方法签名所在行（1-indexed）
        public final String sourceArchive;   // jar 文件路径

        public Entry(String className, String methodName, String signature,
                     String sourceFile, int line, String sourceArchive) {
            this.className = className;
            this.methodName = methodName;
            this.signature = signature;
            this.sourceFile = sourceFile;
            this.line = line;
            this.sourceArchive = sourceArchive;
        }

        public String key() { return className + "#" + methodName; }
        public String fullKey() { return key() + "(" + signature + ")"; }
    }

    private final Map<String, List<Entry>> byKey = new HashMap<>();  // className#methodName -> entries
    private final Map<String, List<Entry>> byClass = new HashMap<>(); // className -> entries
    private final List<String> indexedArchives = new ArrayList<>();

    /** 索引一个 source-jar */
    public void indexArchive(String sourceArchivePath) {
        if (sourceArchivePath == null) return;
        File f = new File(sourceArchivePath);
        if (!f.isFile()) return;
        try (ZipFile zf = new ZipFile(f)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String n = e.getName();
                if (e.isDirectory()) continue;
                if (!n.endsWith(".java") && !n.endsWith(".kt")) continue;
                String className = n.endsWith(".java")
                        ? n.substring(0, n.length() - 5).replace('/', '.')
                        : n.substring(0, n.length() - 3).replace('/', '.');
                try (InputStream is = zf.getInputStream(e)) {
                    String content = new String(is.readAllBytes());
                    scanSource(className, n, content, sourceArchivePath);
                }
            }
            indexedArchives.add(sourceArchivePath);
        } catch (IOException ignored) { }
    }

    private void scanSource(String className, String entryName, String content, String archive) {
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 排除注释行
            String t = line.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
            Matcher m = METHOD_DECL.matcher(line);
            while (m.find()) {
                String methodName = m.group(1);
                String args = m.group(2).trim();
                Entry entry = new Entry(className, methodName, args, entryName, i + 1, archive);
                String key = className + "#" + methodName;
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
                byClass.computeIfAbsent(className, k -> new ArrayList<>()).add(entry);
            }
        }
    }

    /** 索引一个源文件（不来自 jar） */
    public void indexSourceFile(String className, String filePath, String content) {
        scanSource(className, filePath, content, filePath);
    }

    /** 按 className + methodName 查找所有候选 */
    public List<Entry> find(String className, String methodName) {
        if (className == null || methodName == null) return Collections.emptyList();
        return byKey.getOrDefault(className + "#" + methodName, Collections.emptyList());
    }

    /** 查找类的所有方法 */
    public List<Entry> methodsOf(String className) {
        if (className == null) return Collections.emptyList();
        return byClass.getOrDefault(className, Collections.emptyList());
    }

    /** 查找包含某方法名的所有类（不限定 class） */
    public List<Entry> findByMethodName(String methodName) {
        if (methodName == null) return Collections.emptyList();
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> kv : byKey.entrySet()) {
            if (kv.getKey().endsWith("#" + methodName)) out.addAll(kv.getValue());
        }
        return out;
    }

    public int entryCount() {
        return byKey.values().stream().mapToInt(List::size).sum();
    }

    public int classCount() { return byClass.size(); }

    public List<String> indexedArchives() { return Collections.unmodifiableList(indexedArchives); }

    public void clear() {
        byKey.clear();
        byClass.clear();
        indexedArchives.clear();
    }

    /**
     * 把索引与 ProjectIndex 合并（用 SourceJarEntry 形式追加到 ProjectIndex）。
     * ProjectIndex 需要 index(ParsedFile) 方法。
     */
    public void mergeInto(ProjectIndex idx) {
        if (idx == null) return;
        for (Map.Entry<String, List<Entry>> kv : byClass.entrySet()) {
            String className = kv.getKey();
            List<Reference> refs = new ArrayList<>();
            for (Entry e : kv.getValue()) {
                refs.add(new Reference(e.methodName, null,
                        Reference.ReferenceKind.METHOD, className,
                        e.sourceArchive, LanguageId.JAVA));
            }
            ParsedFile pf = new ParsedFile(
                    className.replace('.', '/') + ".java",
                    LanguageId.JAVA, className, refs, null);
            idx.index(pf);
        }
    }
}
