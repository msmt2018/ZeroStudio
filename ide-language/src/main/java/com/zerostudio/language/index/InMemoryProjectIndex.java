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
 * In-memory implementation of {@link ProjectIndex}. Suitable for unit tests
 * and small-to-medium projects. Designed to be cheap to build incrementally:
 * updates are per-file, and a single read-write lock keeps structure
 * consistent.
 */
public final class InMemoryProjectIndex implements ProjectIndex {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, ParsedFile> filesByPath = new ConcurrentHashMap<>();
    private final Map<String, List<Symbol>> byName = new HashMap<>();
    private final Map<String, List<Symbol>> byFqn = new HashMap<>();

    @Override
    public void updateFile(ParsedFile parsed) {
        lock.writeLock().lock();
        try {
            removeFileInternal(parsed.path);
            filesByPath.put(parsed.path, parsed);
            for (Symbol s : parsed.symbols) {
                byName.computeIfAbsent(s.name, k -> new ArrayList<>()).add(s);
                if (s.fqn != null) {
                    byFqn.computeIfAbsent(s.fqn, k -> new ArrayList<>()).add(s);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void removeFile(String path) {
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
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int fileCount() {
        return filesByPath.size();
    }

    private void removeFileInternal(String path) {
        ParsedFile old = filesByPath.remove(path);
        if (old == null) return;
        for (Symbol s : old.symbols) {
            removeFromMultiMap(byName, s.name, s);
            if (s.fqn != null) {
                removeFromMultiMap(byFqn, s.fqn, s);
            }
        }
    }

    private static <V> void removeFromMultiMap(Map<String, List<V>> map, String key, V value) {
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
            return f == null ? Collections.emptyList() : new ArrayList<>(f.symbols);
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
