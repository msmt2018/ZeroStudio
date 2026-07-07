package com.zerostudio.language.source;

import com.zerostudio.decompiler.api.DecompileRequest;
import com.zerostudio.decompiler.api.DecompileResult;
import com.zerostudio.decompiler.api.Decompiler;
import com.zerostudio.decompiler.api.DecompilerRegistry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 三层源码定位器：workspace 源码 → source-jar → class-jar + CFR 反编译。
 * 与 SourceResolver 类似，但 SourceLocator 接受多个根目录、按 classpath 列表查找、
 * 并通过 DecompilerRegistry.firstOrNull() 拉取反编译器。
 */
public final class SourceLocator {

    public enum Kind { WORKSPACE_SOURCE, SOURCE_JAR, DECOMPILED, BUILTIN, MISSING }

    public static final class LocatedSource {
        public final Kind kind;
        public final String className;
        public final String displayPath;
        public final String sourceText;
        public final String originPath;
        public final String failure;

        private LocatedSource(Kind kind, String className, String displayPath,
                              String sourceText, String originPath, String failure) {
            this.kind = kind;
            this.className = className;
            this.displayPath = displayPath;
            this.sourceText = sourceText;
            this.originPath = originPath;
            this.failure = failure;
        }

        public boolean isResolved() {
            return kind != Kind.MISSING && sourceText != null;
        }

        public static LocatedSource of(Kind kind, String className, String displayPath,
                                       String sourceText, String originPath) {
            return new LocatedSource(kind, className, displayPath, sourceText, originPath, null);
        }

        public static LocatedSource missing(String className, String reason) {
            return new LocatedSource(Kind.MISSING, className, null, null, null, reason);
        }
    }

    public static final class ClasspathEntry {
        public enum Kind { SOURCE_JAR, CLASS_JAR }
        public final String path;
        public final Kind kind;

        public ClasspathEntry(String path, Kind kind) {
            this.path = path;
            this.kind = kind;
        }

        public static ClasspathEntry sourceJar(String path) { return new ClasspathEntry(path, Kind.SOURCE_JAR); }
        public static ClasspathEntry classJar(String path) { return new ClasspathEntry(path, Kind.CLASS_JAR); }

        @Override public boolean equals(Object o) {
            if (!(o instanceof ClasspathEntry)) return false;
            ClasspathEntry e = (ClasspathEntry) o;
            return Objects.equals(path, e.path) && kind == e.kind;
        }
        @Override public int hashCode() { return Objects.hash(path, kind); }
    }

    private final List<File> workspaceRoots = new ArrayList<>();
    private final List<ClasspathEntry> classpath = new ArrayList<>();
    private final ConcurrentHashMap<String, LocatedSource> cache = new ConcurrentHashMap<>();
    private final java.util.LinkedHashMap<String, Boolean> lruOrder = new java.util.LinkedHashMap<>(16, 0.75f, true);
    private int maxCacheSize = 512;
    private Function<String, Decompiler> decompilerLookup = name -> DecompilerRegistry.get(name);

    public void addWorkspaceRoot(File root) {
        if (root != null && root.exists() && root.isDirectory()) workspaceRoots.add(root);
    }

    public void addClasspathEntry(ClasspathEntry entry) { classpath.add(entry); }

    public void setDecompilerLookup(Function<String, Decompiler> lookup) {
        this.decompilerLookup = lookup;
    }

    public void setMaxCacheSize(int size) { this.maxCacheSize = Math.max(0, size); }
    public int cacheSize() { return cache.size(); }

    public void clearCache() { cache.clear(); lruOrder.clear(); }

    /** 主动失效某个类的缓存（class 文件被修改时调用） */
    public void invalidate(String className) { cache.remove(className); lruOrder.remove(className); }

    public LocatedSource locate(String className) {
        synchronized (lruOrder) {
            LocatedSource cached = cache.get(className);
            if (cached != null) {
                lruOrder.put(className, Boolean.TRUE);
                return cached;
            }
        }
        LocatedSource result = doLocate(className);
        synchronized (lruOrder) {
            cache.put(className, result);
            lruOrder.put(className, Boolean.TRUE);
            // LRU eviction
            while (lruOrder.size() > maxCacheSize) {
                String oldest = lruOrder.keySet().iterator().next();
                lruOrder.remove(oldest);
                cache.remove(oldest);
            }
        }
        return result;
    }

    private LocatedSource doLocate(String className) {
        LocatedSource ws = locateWorkspace(className);
        if (ws != null) return ws;

        for (ClasspathEntry cp : classpath) {
            if (cp.kind == ClasspathEntry.Kind.SOURCE_JAR) {
                LocatedSource src = readFromSourceArchive(cp.path, className);
                if (src != null) return src;
            }
        }

        for (ClasspathEntry cp : classpath) {
            if (cp.kind == ClasspathEntry.Kind.CLASS_JAR) {
                LocatedSource dec = readFromClassArchive(cp.path, className);
                if (dec != null && dec.isResolved()) return dec;
            }
        }

        LocatedSource builtin = builtin(className);
        if (builtin != null) return builtin;

        return LocatedSource.missing(className, "No source for " + className);
    }

    private LocatedSource locateWorkspace(String className) {
        String rel = className.replace('.', File.separatorChar);
        for (File root : workspaceRoots) {
            for (String ext : Arrays.asList(".java", ".kt")) {
                File f = new File(root, rel + ext);
                if (f.isFile()) {
                    try {
                        return LocatedSource.of(Kind.WORKSPACE_SOURCE, className,
                                f.getAbsolutePath(), new String(Files.readAllBytes(f.toPath())),
                                f.getAbsolutePath());
                    } catch (IOException e) { return null; }
                }
            }
        }
        return null;
    }

    private LocatedSource readFromSourceArchive(String archivePath, String className) {
        File archive = new File(archivePath);
        if (!archive.isFile()) return null;
        String entryName = className.replace('.', '/');
        try (ZipFile zf = new ZipFile(archive)) {
            for (String ext : Arrays.asList("", "-src", "-sources")) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.isDirectory()) continue;
                    String n = e.getName();
                    if (!n.endsWith(".java") && !n.endsWith(".kt")) continue;
                    if (!n.startsWith(entryName)) continue;
                    if (ext.isEmpty() ? n.substring(entryName.length()).startsWith(".")
                            : n.contains(ext)) {
                        try (InputStream is = zf.getInputStream(e)) {
                            return LocatedSource.of(Kind.SOURCE_JAR, className,
                                    archivePath + "!/" + n,
                                    new String(is.readAllBytes()),
                                    archivePath + "!/" + n);
                        }
                    }
                }
            }
        } catch (IOException e) { return null; }
        return null;
    }

    private LocatedSource readFromClassArchive(String archivePath, String className) {
        Decompiler decompiler = decompilerLookup != null ? decompilerLookup.apply("cfr") : null;
        if (decompiler == null) return null;
        String entryName = className.replace('.', '/') + ".class";
        File archive = new File(archivePath);
        byte[] bytes = null;
        if (archive.isDirectory()) {
            // Treat directory as a classpath root containing .class files
            File classFile = new File(archive, entryName);
            if (classFile.isFile()) {
                try {
                    bytes = java.nio.file.Files.readAllBytes(classFile.toPath());
                } catch (IOException e) { return null; }
            }
        } else if (archive.isFile()) {
            try (ZipFile zf = new ZipFile(archive)) {
                ZipEntry e = zf.getEntry(entryName);
                if (e != null) {
                    try (InputStream is = zf.getInputStream(e)) {
                        bytes = is.readAllBytes();
                    }
                }
            } catch (IOException e) { return null; }
        }
        if (bytes == null) return null;
        DecompileResult r = decompiler.decompile(DecompileRequest.builder()
                .className(className).classBytes(bytes).classpathEntry(archivePath).build());
        if (r.isOk()) {
            return LocatedSource.of(Kind.DECOMPILED, className,
                    "[" + className + "] (decompiled from " + archivePath + ")",
                    r.source, archivePath);
        }
        return null;
    }

    private LocatedSource builtin(String className) {
        if (className.startsWith("java.lang.") || className.startsWith("java.util.")
                || className.startsWith("java.io.") || className.startsWith("java.nio.")) {
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            String text = "public final class " + simpleName + " { /* builtin */ }";
            return LocatedSource.of(Kind.BUILTIN, className,
                    "[" + className + "] (builtin)", text, "builtin:" + className);
        }
        return null;
    }
}
