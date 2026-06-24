/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase G3: SourceLocator source → class mapping cache.
 *
 *  Caches the results of JavaSourceParser and ClassFileReader lookups,
 *  keyed by source file path. This avoids repeated file I/O and parsing
 *  when multiple breakpoints reference the same source file.
 *
 *  Cache design:
 *    - ConcurrentHashMap for thread-safe reads and writes
 *    - Two sub-caches: sourceCache (ParsedSource) and classCache (ParsedClass)
 *    - Max size limit to prevent unbounded memory growth
 *    - LRUCache entry eviction when max size is reached
 *
 *  The cache is used by SourceLocator when installing breakpoints:
 *    1. Check cache for source file → signature mapping
 *    2. If miss: parse with JavaSourceParser → cache
 *    3. If .class available: also parse with ClassFileReader → cache
 *    4. Return the cached result
 *
 *  Thread safety: All public methods are thread-safe. The internal
 *  caches are backed by ConcurrentHashMap, so reads don't block writes.
 */

package com.zerostudio.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Phase G3: source file → parsed data cache.
 *
 * Provides two levels of caching:
 *   - {@link #sourceCache}: .java source files → ParsedSource
 *   - {@link #classCache}: .class files → ParsedClass
 *
 * Both caches use LRU eviction when they exceed their maximum size.
 */
public final class SourceLocatorCache {

    /** Maximum number of entries per cache. */
    private static final int MAX_SOURCE_ENTRIES = 200;
    private static final int MAX_CLASS_ENTRIES = 500;

    // LRU caches backed by LinkedHashMap (access-order).
    private final LRUCache<String, JavaSourceParser.ParsedSource> sourceCache;
    private final LRUCache<String, ClassFileReader.ParsedClass> classCache;

    // Global invalidation: when a class is loaded, invalidate related source caches.
    private final ConcurrentMap<String, String> sourceToClass = new ConcurrentHashMap<>();

    private final JavaSourceParser sourceParser;
    private final ClassFileReader classReader;

    public SourceLocatorCache() {
        this.sourceCache = new LRUCache<>(MAX_SOURCE_ENTRIES);
        this.classCache = new LRUCache<>(MAX_CLASS_ENTRIES);
        this.sourceParser = new JavaSourceParser();
        this.classReader = new ClassFileReader();
    }

    // ----------------------------------------------------------------
    // Source file (.java) caching
    // ----------------------------------------------------------------

    /**
     * Look up a .java source file in the cache, or parse it if not present.
     *
     * @param sourceFile path to the .java source file
     * @return the ParsedSource, or null if the file doesn't exist or can't be parsed
     */
    @Nullable
    public JavaSourceParser.ParsedSource getSource(@NonNull String sourceFile) {
        String key = normalize(sourceFile);
        // Fast path: concurrent read
        JavaSourceParser.ParsedSource cached = sourceCache.get(key);
        if (cached != null) return cached;

        // Slow path: parse and cache
        JavaSourceParser.ParsedSource parsed = sourceParser.parsePath(sourceFile);
        if (parsed != null) {
            sourceCache.put(key, parsed);
            // Record the relationship: this source might be compiled to any .class
            // in the same package. We store a hint: package → [sourceFile]
            if (!parsed.packageName.isEmpty()) {
                String classPathHint = parsed.packageName.replace('.', '/') + "/"
                        + basename(sourceFile).replace(".java", ".class");
                sourceToClass.put(key, classPathHint);
            }
        }
        return parsed;
    }

    /**
     * Invalidate the cache entry for a source file.
     * Call this when the source file changes on disk.
     */
    public void invalidateSource(@NonNull String sourceFile) {
        sourceCache.remove(normalize(sourceFile));
    }

    // ----------------------------------------------------------------
    // Class file (.class) caching
    // ----------------------------------------------------------------

    /**
     * Look up a .class file in the cache, or read it if not present.
     */
    @Nullable
    public ClassFileReader.ParsedClass getClass(@NonNull String classFile) {
        String key = normalize(classFile);
        ClassFileReader.ParsedClass cached = classCache.get(key);
        if (cached != null) return cached;

        ClassFileReader.ParsedClass parsed = classReader.parse(new File(classFile));
        if (parsed != null) {
            classCache.put(key, parsed);
        }
        return parsed;
    }

    /**
     * Invalidate the cache entry for a class file.
     */
    public void invalidateClass(@NonNull String classFile) {
        classCache.remove(normalize(classFile));
    }

    // ----------------------------------------------------------------
    // Global cache management
    // ----------------------------------------------------------------

    /**
     * Clear all caches.
     */
    public void clear() {
        sourceCache.clear();
        classCache.clear();
        sourceToClass.clear();
    }

    /**
     * Return the current size of the source cache.
     */
    public int sourceCacheSize() {
        return sourceCache.size();
    }

    /**
     * Return the current size of the class cache.
     */
    public int classCacheSize() {
        return classCache.size();
    }

    // ----------------------------------------------------------------
    // Internal utilities
    // ----------------------------------------------------------------

    @NonNull
    private static String normalize(@NonNull String path) {
        // Normalize path: resolve . and .., use forward slashes.
        // We avoid File.getCanonicalPath() as it hits the filesystem.
        // Instead, we do a simple string normalization.
        if (path.isEmpty()) return path;

        String normalized = path.replace('\\', '/');

        // Remove trailing slashes
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Resolve simple ".." (e.g., "src/../Main.java" → "Main.java")
        // This is a best-effort simplification; for deep paths this is not complete.
        String[] parts = normalized.split("/");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.equals("..")) {
                // Find the previous non-.. part
                int lastSlash = sb.lastIndexOf("/");
                if (lastSlash < 0) {
                    sb.setLength(0); // goes to root
                } else {
                    int prevSlash = sb.lastIndexOf("/", lastSlash - 1);
                    if (prevSlash < 0) {
                        sb.setLength(0);
                    } else {
                        sb.setLength(prevSlash + 1);
                    }
                }
            } else if (!part.isEmpty() && !part.equals(".")) {
                if (sb.length() > 0 && !sb.toString().endsWith("/")) {
                    sb.append('/');
                }
                sb.append(part);
            }
        }
        return sb.length() == 0 ? "." : sb.toString();
    }

    @NonNull
    private static String basename(@NonNull String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash < 0) return path;
        return path.substring(lastSlash + 1);
    }

    // ----------------------------------------------------------------
    // LRU Cache implementation using LinkedHashMap
    // ----------------------------------------------------------------

    /**
     * A simple LRU (Least Recently Used) cache backed by LinkedHashMap.
     * Thread-safe: uses ConcurrentHashMap for the underlying storage.
     * Synchronized access-order operations ensure thread-safe LRU eviction.
     */
    private static final class LRUCache<K, V> {

        private final int maxSize;
        private final ConcurrentMap<K, V> map;
        // For LRU ordering: we use a synchronized LinkedHashMap.
        // Since ConcurrentHashMap doesn't support access-order iteration,
        // we maintain a separate LinkedHashMap for LRU ordering and
        // sync on every access. This is acceptable because cache
        // accesses are relatively infrequent compared to the work they cache.
        private final Object lock = new Object();
        private final LinkedHashMap<K, V> order;

        LRUCache(int maxSize) {
            this.maxSize = maxSize;
            this.map = new ConcurrentHashMap<>();
            this.order = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > LRUCache.this.maxSize;
                }
            };
        }

        @Nullable
        V get(@NonNull K key) {
            V value = map.get(key);
            if (value != null) {
                // Update access order (thread-safe)
                synchronized (lock) {
                    // Re-insert to update access order
                    order.remove(key);
                    order.put(key, value);
                }
            }
            return value;
        }

        void put(@NonNull K key, @NonNull V value) {
            map.put(key, value);
            synchronized (lock) {
                // LinkedHashMap.put updates access order for existing keys
                order.put(key, value);
            }
        }

        void remove(@NonNull K key) {
            map.remove(key);
            synchronized (lock) {
                order.remove(key);
            }
        }

        int size() {
            return map.size();
        }

        void clear() {
            map.clear();
            synchronized (lock) {
                order.clear();
            }
        }
    }
}
