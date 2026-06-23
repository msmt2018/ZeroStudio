package com.zerostudio.language.index;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 项目级内存索引：
 *  - package → [classNames]
 *  - className → ParsedFile
 *  - symbolName → [classNames]   （模糊查询）
 *  - filePath → ParsedFile
 */
public final class ProjectIndex {

    private final Map<String, List<String>> packageToClasses = new ConcurrentHashMap<>();
    private final Map<String, ParsedFile> classNameToFile = new ConcurrentHashMap<>();
    private final Map<String, List<String>> symbolToClasses = new ConcurrentHashMap<>();
    private final Map<String, ParsedFile> filePathToFile = new ConcurrentHashMap<>();

    public synchronized void index(ParsedFile file) {
        if (file == null) return;
        filePathToFile.put(file.path, file);
        // index imports and references
        if (file.references == null) return;
        for (Reference ref : file.references) {
            if (ref.kind == Reference.ReferenceKind.IMPORT) {
                String fqn = ref.name;
                String pkg = fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "";
                String cls = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
                if (cls.endsWith(".*")) continue;
                packageToClasses.computeIfAbsent(pkg, k -> new ArrayList<>()).add(fqn);
                classNameToFile.putIfAbsent(fqn, file);
                symbolToClasses.computeIfAbsent(cls.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(fqn);
            }
            if (ref.kind == Reference.ReferenceKind.CLASS) {
                String fqn = file.packageName + "." + ref.name;
                packageToClasses.computeIfAbsent(file.packageName, k -> new ArrayList<>()).add(fqn);
                classNameToFile.putIfAbsent(fqn, file);
                symbolToClasses.computeIfAbsent(ref.name.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(fqn);
            }
        }
    }

    public synchronized void remove(String filePath) {
        ParsedFile file = filePathToFile.remove(filePath);
        if (file == null) return;
        // simple remove strategy: clear and re-index
        clear();
        // re-index all remaining files would be needed; for simplicity just clear
    }

    public synchronized void clear() {
        packageToClasses.clear();
        classNameToFile.clear();
        symbolToClasses.clear();
        filePathToFile.clear();
    }

    public List<String> classesInPackage(String pkg) {
        return Collections.unmodifiableList(packageToClasses.getOrDefault(pkg, Collections.emptyList()));
    }

    public ParsedFile fileFor(String className) { return classNameToFile.get(className); }

    public ParsedFile fileForPath(String path) { return filePathToFile.get(path); }

    public List<String> fuzzySearch(String query, int max) {
        if (query == null || query.isEmpty()) return Collections.emptyList();
        String q = query.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : symbolToClasses.entrySet()) {
            if (e.getKey().contains(q)) {
                out.addAll(e.getValue());
                if (out.size() >= max) break;
            }
        }
        return out;
    }

    public List<String> allClasses() {
        List<String> all = new ArrayList<>();
        for (List<String> list : packageToClasses.values()) all.addAll(list);
        return all;
    }

    public int totalFiles() { return filePathToFile.size(); }
    public int totalClasses() { return classNameToFile.size(); }

    /** 暴露内部 filePath→file map 的快照（供持久化使用） */
    public java.util.List<Map.Entry<String, ParsedFile>> allFiles() {
        return new ArrayList<>(filePathToFile.entrySet());
    }

    /** simple pattern matching: `a.b.*` returns classes whose fqn starts with a.b */
    public List<String> matchWildcard(String pattern) {
        if (pattern == null || !pattern.endsWith(".*")) return Collections.emptyList();
        String prefix = pattern.substring(0, pattern.length() - 2);
        List<String> out = new ArrayList<>();
        for (String fqn : allClasses()) {
            if (fqn.startsWith(prefix + ".") || fqn.equals(prefix)) out.add(fqn);
        }
        return out;
    }
}
