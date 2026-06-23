package com.zerostudio.decompiler.cache;

import com.zerostudio.decompiler.api.*;
import java.util.*;

public final class CachingDecompiler implements Decompiler {
    private final Decompiler inner;
    private final int maxEntries;
    private final Map<Key, DecompileResult> cache;

    public CachingDecompiler(Decompiler inner, int maxEntries) {
        this.inner = inner;
        this.maxEntries = maxEntries;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<Key, DecompileResult> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override public String name() { return inner.name(); }
    @Override public String version() { return inner.version(); }

    @Override
    public DecompileResult decompile(DecompileRequest request) {
        Key k = Key.of(request);
        synchronized (cache) {
            if (cache.containsKey(k)) return cache.get(k);
        }
        DecompileResult result = inner.decompile(request);
        synchronized (cache) { cache.put(k, result); }
        return result;
    }

    public static final class Key {
        public final String className;
        public final long bytesHash;
        public final int bytesLen;
        public final String classpath;

        private Key(String className, long bytesHash, int bytesLen, String classpath) {
            this.className = className;
            this.bytesHash = bytesHash;
            this.bytesLen = bytesLen;
            this.classpath = classpath;
        }

        public static Key of(DecompileRequest r) {
            long h = 0;
            if (r.classBytes != null) {
                for (int i = 0; i < Math.min(r.classBytes.length, 8); i++) {
                    h = h * 31 + (r.classBytes[i] & 0xFF);
                }
            }
            String cp = r.classpathEntry != null ? r.classpathEntry : "";
            return new Key(r.className, h, r.classBytes != null ? r.classBytes.length : -1, cp);
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return Objects.equals(className, k.className) && bytesHash == k.bytesHash
                    && bytesLen == k.bytesLen && Objects.equals(classpath, k.classpath);
        }
        @Override public int hashCode() { return Objects.hash(className, bytesHash, bytesLen, classpath); }
    }
}