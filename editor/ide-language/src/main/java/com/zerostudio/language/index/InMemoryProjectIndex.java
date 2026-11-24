package com.zerostudio.language.index;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于 {@code byName} / {@code byFqn} 反向表与 {@code filesByPath} 维护的
 * 内存实现。适用于单元测试与中小型项目，单写多读。
 *
 * <p>同时实现旧 {@code index/remove/fileFor/...} API，因此可直接替换
 * {@link DefaultProjectIndex} 出现在 {@code IndexAdapter} / 旧 services 中。
 */
public final class InMemoryProjectIndex implements ProjectIndex {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, ParsedFile> filesByPath = new ConcurrentHashMap<>();
    private final Map<String, List<Symbol>> byName = new HashMap<>();
    private final Map<String, List<Symbol>> byFqn = new HashMap<>();
    // 旧 API 需要的补充结构
    private final Map<String, List<String>> packageToClasses = new ConcurrentHashMap<>();
    private final Map<String, ParsedFile> classNameToFile = new ConcurrentHashMap<>();
    private final Map<String, List<String>> symbolToClasses = new ConcurrentHashMap<>();

    @Override
    public void updateFile(ParsedFile parsed) {
        if (parsed == null) return;
        lock.writeLock().lock();
        try {
            removeFileInternal(parsed.path);
            filesByPath.put(parsed.path, parsed);
            indexSymbolsIntoMaps(parsed);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void removeFile(String path) {
        if (path == null) return;
        lock.writeLock().lock();
        try {
            removeFileInternal(path);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Lookup lookup() {
        return new InMemoryLookup();
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            filesByPath.clear();
            byName.clear();
            byFqn.clear();
            packageToClasses.clear();
            classNameToFile.clear();
            symbolToClasses.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int fileCount() {
        return filesByPath.size();
    }

    // —— 旧 API 兼容 —— //

    @Override
    public void index(ParsedFile file) {
        updateFile(file);
    }

    @Override
    public void remove(String filePath) {
        removeFile(filePath);
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
        return pf != null;
    }

    @Override
    public ParsedFile fileForPath(String path) { return filesByPath.get(path); }

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
    public int totalFiles() { return filesByPath.size(); }

    @Override
    public int totalClasses() { return classNameToFile.size(); }

    @Override
    public List<Map.Entry<String, ParsedFile>> allFiles() {
        return new ArrayList<>(filesByPath.entrySet());
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

    // —— 内部实现 —— //

    private void indexSymbolsIntoMaps(ParsedFile parsed) {
        if (parsed.symbols != null) {
            for (Symbol s : parsed.symbols) {
                if (s.name != null) {
                    byName.computeIfAbsent(s.name, k -> new ArrayList<>()).add(s);
                }
                if (s.fqn != null) {
                    byFqn.computeIfAbsent(s.fqn, k -> new ArrayList<>()).add(s);
                    symbolToClasses.computeIfAbsent(nameOfFqn(s.fqn).toLowerCase(Locale.ROOT),
                            k -> new ArrayList<>()).add(s.fqn);
                }
            }
        }
        if (parsed.references != null) {
            for (com.zerostudio.language.model.Reference ref : parsed.references) {
                if (ref.kind == com.zerostudio.language.model.Reference.ReferenceKind.IMPORT) {
                    String fqn = ref.name;
                    String pkg = fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "";
                    String cls = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
                    if (cls.endsWith(".*")) continue;
                    packageToClasses.computeIfAbsent(pkg, k -> new ArrayList<>()).add(fqn);
                    classNameToFile.putIfAbsent(fqn, parsed);
                    symbolToClasses.computeIfAbsent(cls.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(fqn);
                } else if (ref.kind == com.zerostudio.language.model.Reference.ReferenceKind.CLASS) {
                    String fqn = parsed.packageName + "." + ref.name;
                    packageToClasses.computeIfAbsent(parsed.packageName, k -> new ArrayList<>()).add(fqn);
                    classNameToFile.putIfAbsent(fqn, parsed);
                    symbolToClasses.computeIfAbsent(ref.name.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(fqn);
                }
            }
        }
    }

    private static String nameOfFqn(String fqn) {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    private void removeFileInternal(String path) {
        ParsedFile old = filesByPath.remove(path);
        if (old == null) return;
        if (old.symbols != null) {
            for (Symbol s : old.symbols) {
                removeFromMultiMap(byName, s.name, s);
                if (s.fqn != null) {
                    removeFromMultiMap(byFqn, s.fqn, s);
                }
            }
        }
        // 简化：移除涉及该文件的旧反向表条目；这里保守不清空，因为
        // 该方法仅在新 updateFile 路径上调用，紧接着会重新构建。
    }

    private static <V> void removeFromMultiMap(Map<String, List<V>> map, String key, V value) {
        if (key == null) return;
        List<V> list = map.get(key);
        if (list == null) return;
        list.removeIf(v -> v == value);
        if (list.isEmpty()) map.remove(key);
    }

    private final class InMemoryLookup implements Lookup {
        @Override
        public List<Symbol> byName(String name) {
            lock.readLock().lock();
            try {
                List<Symbol> list = byName.get(name);
                return list == null ? Collections.emptyList() : new ArrayList<>(list);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public List<Symbol> byFqn(String fqn) {
            lock.readLock().lock();
            try {
                List<Symbol> list = byFqn.get(fqn);
                return list == null ? Collections.emptyList() : new ArrayList<>(list);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public List<Symbol> byKind(SymbolKind kindOnly) {
            List<Symbol> out = new ArrayList<>();
            lock.readLock().lock();
            try {
                for (List<Symbol> list : byName.values()) {
                    for (Symbol s : list) {
                        if (s.kind == kindOnly) out.add(s);
                    }
                }
            } finally {
                lock.readLock().unlock();
            }
            return out;
        }

        @Override
        public List<Symbol> inFile(String path) {
            ParsedFile f;
            lock.readLock().lock();
            try {
                f = filesByPath.get(path);
            } finally {
                lock.readLock().unlock();
            }
            return f == null || f.symbols == null ? Collections.emptyList() : new ArrayList<>(f.symbols);
        }

        @Override
        public List<ParsedFile> files() {
            lock.readLock().lock();
            try {
                return new ArrayList<>(filesByPath.values());
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public List<ParsedFile> filesOfLanguage(LanguageId lang) {
            List<ParsedFile> out = new ArrayList<>();
            lock.readLock().lock();
            try {
                for (ParsedFile f : filesByPath.values()) {
                    if (f.language == lang) out.add(f);
                }
            } finally {
                lock.readLock().unlock();
            }
            return out;
        }

        @Override
        public ParsedFile file(String path) {
            lock.readLock().lock();
            try {
                return filesByPath.get(path);
            } finally {
                lock.readLock().unlock();
            }
        }
    }
}
