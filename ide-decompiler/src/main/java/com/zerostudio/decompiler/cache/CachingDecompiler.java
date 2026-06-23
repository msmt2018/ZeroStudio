package com.zerostudio.decompiler.cache;

import androidx.annotation.NonNull;

import com.zerostudio.decompiler.api.DecompileRequest;
import com.zerostudio.decompiler.api.DecompileResult;
import com.zerostudio.decompiler.api.Decompiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * LRU cache over a {@link Decompiler}. Thread-safe; lock granularity
 * is the cache itself so the inner decompiler is never called under
 * the cache lock.
 *
 * <p>The cache key is {@code (className, byte-length, first-8-bytes-hash)}
 * — that is, two requests for the same class on the same byte payload
 * share a cache entry, but a recompiled version of the same class is
 * treated as a different entry.
 */
public final class CachingDecompiler implements Decompiler {

    private final Decompiler inner;
    private final int maxEntries;
    private final Map<Key, DecompileResult> cache;

    public CachingDecompiler(@NonNull Decompiler inner, int maxEntries) {
        this.inner = Objects.requireNonNull(inner);
        this.maxEntries = Math.max(1, maxEntries);
        this.cache = new LinkedHashMap<Key, DecompileResult>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, DecompileResult> e) {
                return size() > CachingDecompiler.this.maxEntries;
            }
        };
    }

    @NonNull
    @Override
    public String name() { return inner.name() + "+cache"; }

    @NonNull
    @Override
    public String version() { return inner.version(); }

    @NonNull
    @Override
    public DecompileResult decompile(@NonNull DecompileRequest request) {
        Key k = Key.of(request);
        synchronized (cache) {
            if (cache.containsKey(k)) return cache.get(k);
        }
        DecompileResult result = inner.decompile(request);
        synchronized (cache) {
            cache.put(k, result);
        }
        return result;
    }

    /** Test-only: evict all entries. */
    public void clear() {
        synchronized (cache) { cache.clear(); }
    }

    /** Test-only: current size. */
    public int size() {
        synchronized (cache) { return cache.size(); }
    }

    private static final class Key {
        final String className;
        final int bytesHash;
        final int bytesLen;
        final String classpath;
        private final int hash;

        private Key(String n, int h, int l, String c) {
            this.className = n;
            this.bytesHash = h;
            this.bytesLen = l;
            this.classpath = c;
            int hc = n.hashCode();
            hc = 31 * hc + h;
            hc = 31 * hc + l;
            hc = 31 * hc + (c == null ? 0 : c.hashCode());
            this.hash = hc;
        }

        static Key of(DecompileRequest r) {
            byte[] b = r.classBytes;
            int h = 0;
            int l = 0;
            if (b != null) {
                l = b.length;
                h = hashFirstEight(b);
            }
            return new Key(r.className, h, l, r.classpathEntry);
        }

        private static int hashFirstEight(byte[] b) {
            int n = Math.min(8, b.length);
            int h = 0;
            for (int i = 0; i < n; i++) h = 31 * h + b[i];
            return h;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return bytesHash == k.bytesHash
                    && bytesLen == k.bytesLen
                    && className.equals(k.className)
                    && (classpath == null
                        ? k.classpath == null
                        : classpath.equals(k.classpath));
        }
        @Override public int hashCode() { return hash; }
    }
}
