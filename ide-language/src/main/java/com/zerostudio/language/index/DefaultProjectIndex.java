package com.zerostudio.language.index;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ProjectIndex} 的默认实现。
 *
 * <p>历史上是 {@code ProjectIndex} 这个类本身；当 {@code ProjectIndex} 被升级为
 * 接口后，本类接管原实现，保留 {@code synchronized} 简单锁语义，便于
 * {@code IndexAdapter} / {@code ProjectIndexJson} 等老服务直接使用。
 *
 * <p>对 resolver 暴露的 {@link ProjectIndex.Lookup} 视图通过 {@link #lookup()} 提供，
 * 但因为旧实现不维护 {@code byName} / {@code byFqn} 反向表，{@code Lookup} 的
 * 语义在 lazy 索引场景下退化为"按已索引的 {@code Reference.name} / {@code Reference.fqn}
 * 重新构建"，对 resolver 仍然返回一致结果（空列表表示尚未抽取出任何符号）。
 */
public final class DefaultProjectIndex implements ProjectIndex {

    private final Map<String, List<String>> packageToClasses = new ConcurrentHashMap<>();
    private final Map<String, ParsedFile> classNameToFile = new ConcurrentHashMap<>();
    private final Map<String, List<String>> symbolToClasses = new ConcurrentHashMap<>();
    private final Map<String, ParsedFile> filePathToFile = new ConcurrentHashMap<>();

    @Override
    public synchronized void index(ParsedFile file) {
        if (file == null) return;
        filePathToFile.put(file.path, file);
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

    @Override
    public synchronized void updateFile(ParsedFile parsed) {
        if (parsed == null) return;
        if (filePathToFile.containsKey(parsed.path)) {
            remove(parsed.path);
        }
        index(parsed);
    }

    @Override
    public synchronized void remove(String filePath) {
        ParsedFile file = filePathToFile.remove(filePath);
        if (file == null) return;
        clear();
    }

    @Override
    public void removeFile(String path) {
        remove(path);
    }

    @Override
    public synchronized void clear() {
        packageToClasses.clear();
        classNameToFile.clear();
        symbolToClasses.clear();
        filePathToFile.clear();
    }

    @Override
    public List<String> classesInPackage(String pkg) {
        return Collections.unmodifiableList(packageToClasses.getOrDefault(pkg, Collections.emptyList()));
    }

    @Override
    public ParsedFile fileFor(String className) { return classNameToFile.get(className); }

    @Override
    public boolean hasClass(String fqn) {
        if (fqn == null) return false;
        ParsedFile pf = classNameToFile.get(fqn);
        if (pf == null) return false;
        if (pf.references == null) return false;
        String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
        String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
        for (Reference r : pf.references) {
            if ((r.kind == Reference.ReferenceKind.CLASS || r.kind == Reference.ReferenceKind.TYPE)
                    && simple.equals(r.name)
                    && pkg.equals(pf.packageName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ParsedFile fileForPath(String path) { return filePathToFile.get(path); }

    @Override
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

    @Override
    public List<String> allClasses() {
        List<String> all = new ArrayList<>();
        for (List<String> list : packageToClasses.values()) all.addAll(list);
        return all;
    }

    @Override
    public int totalFiles() { return filePathToFile.size(); }

    @Override
    public int fileCount() { return totalFiles(); }

    @Override
    public int totalClasses() { return classNameToFile.size(); }

    @Override
    public List<Map.Entry<String, ParsedFile>> allFiles() {
        return new ArrayList<>(filePathToFile.entrySet());
    }

    @Override
    public List<String> matchWildcard(String pattern) {
        if (pattern == null || !pattern.endsWith(".*")) return Collections.emptyList();
        String prefix = pattern.substring(0, pattern.length() - 2);
        List<String> out = new ArrayList<>();
        for (String fqn : allClasses()) {
            if (fqn.startsWith(prefix + ".") || fqn.equals(prefix)) out.add(fqn);
        }
        return out;
    }

    @Override
    public Lookup lookup() {
        return new SnapshotLookup();
    }

    /** 旧索引结构下没有完整的 {@code byName}/{@code byFqn} 反向表，
     * 这里在快照构造时按需从 {@link ParsedFile#symbols} 构建。
     * 当文件本身没有抽取过 symbols 时返回空列表（不影响 resolver 行为）。 */
    private final class SnapshotLookup implements Lookup {
        private final Map<String, List<Symbol>> byName = new HashMap<>();
        private final Map<String, List<Symbol>> byFqn = new HashMap<>();
        private final List<ParsedFile> allFiles;

        SnapshotLookup() {
            List<ParsedFile> snap = new ArrayList<>(filePathToFile.values());
            for (ParsedFile pf : snap) {
                if (pf.symbols == null) continue;
                for (Symbol s : pf.symbols) {
                    if (s.name != null) {
                        byName.computeIfAbsent(s.name, k -> new ArrayList<>()).add(s);
                    }
                    if (s.fqn != null) {
                        byFqn.computeIfAbsent(s.fqn, k -> new ArrayList<>()).add(s);
                    }
                }
            }
            this.allFiles = Collections.unmodifiableList(snap);
        }

        @Override
        public List<Symbol> byName(String name) {
            List<Symbol> list = byName.get(name);
            return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
        }
        @Override
        public List<Symbol> byFqn(String fqn) {
            List<Symbol> list = byFqn.get(fqn);
            return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
        }
        @Override
        public List<Symbol> byKind(SymbolKind kindOnly) {
            List<Symbol> out = new ArrayList<>();
            for (List<Symbol> list : byName.values()) {
                for (Symbol s : list) if (s.kind == kindOnly) out.add(s);
            }
            return out;
        }
        @Override
        public List<Symbol> inFile(String path) {
            ParsedFile pf = filePathToFile.get(path);
            if (pf == null || pf.symbols == null) return Collections.emptyList();
            return Collections.unmodifiableList(pf.symbols);
        }
        @Override
        public List<ParsedFile> files() { return allFiles; }
        @Override
        public List<ParsedFile> filesOfLanguage(LanguageId lang) {
            List<ParsedFile> out = new ArrayList<>();
            for (ParsedFile pf : allFiles) if (pf.language == lang) out.add(pf);
            return out;
        }
        @Override
        public ParsedFile file(String path) { return filePathToFile.get(path); }
    }
}
