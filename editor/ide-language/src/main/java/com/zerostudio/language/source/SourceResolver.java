package com.zerostudio.language.source;

import com.zerostudio.decompiler.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public final class SourceResolver {
    public enum Kind { WORKSPACE_SOURCE, SOURCE_JAR, DECOMPILED, BUILTIN, MISSING }

    public static final class ResolvedSource {
        public final Kind kind;
        public final String className;
        public final String displayPath;
        public final String sourceText;
        public final String originPath;
        public final String failure;
        private ResolvedSource(Kind kind, String className, String displayPath,
                               String sourceText, String originPath, String failure) {
            this.kind = kind; this.className = className; this.displayPath = displayPath;
            this.sourceText = sourceText; this.originPath = originPath; this.failure = failure;
        }
        public boolean isResolved() { return kind != Kind.MISSING && sourceText != null; }
    }

    public static final class ClasspathEntry {
        public enum Kind { SOURCE_JAR, CLASS_JAR }
        public final String path;
        public final Kind kind;
        public ClasspathEntry(String path, Kind kind) { this.path = path; this.kind = kind; }
        public static ClasspathEntry sourceJar(String path) { return new ClasspathEntry(path, Kind.SOURCE_JAR); }
        public static ClasspathEntry classJar(String path) { return new ClasspathEntry(path, Kind.CLASS_JAR); }
    }

    private File workspaceRoot;
    private Decompiler decompiler;
    private final List<ClasspathEntry> classpath = new ArrayList<>();

    public void setWorkspaceRoot(File root) { this.workspaceRoot = root; }

    public void setDecompiler(Decompiler d) { this.decompiler = d; }

    public void addClasspathEntry(ClasspathEntry entry) { this.classpath.add(entry); }

    public ResolvedSource resolve(String className) {
        // 1) workspace
        if (workspaceRoot != null) {
            ResolvedSource ws = resolveWorkspace(className);
            if (ws != null && ws.isResolved()) return ws;
        }
        // 2) source JAR
        for (ClasspathEntry cp : classpath) {
            if (cp.kind == ClasspathEntry.Kind.SOURCE_JAR) {
                ResolvedSource src = readFromSourceJar(cp.path, className);
                if (src != null && src.isResolved()) return src;
            }
        }
        // 3) class JAR -> decompile
        for (ClasspathEntry cp : classpath) {
            if (cp.kind == ClasspathEntry.Kind.CLASS_JAR) {
                ResolvedSource dec = readClassBytes(cp.path, className);
                if (dec != null && dec.isResolved()) return dec;
            }
        }
        // 4) builtin
        ResolvedSource builtin = builtin(className);
        if (builtin != null) return builtin;
        return new ResolvedSource(Kind.MISSING, className, null, null, null,
                "Cannot find source for: " + className);
    }

    private ResolvedSource resolveWorkspace(String className) {
        if (workspaceRoot == null) return null;
        String relative = className.replace('.', '/');
        for (String ext : Arrays.asList(".java", ".kt")) {
            File f = new File(workspaceRoot, relative + ext);
            if (f.exists()) {
                try {
                    String text = new String(Files.readAllBytes(f.toPath()));
                    return new ResolvedSource(Kind.WORKSPACE_SOURCE, className,
                            f.getAbsolutePath(), text, f.getAbsolutePath(), null);
                } catch (IOException e) { return null; }
            }
        }
        return null;
    }

    private ResolvedSource readFromSourceJar(String jarPath, String className) {
        String entryName = className.replace('.', '/');
        File f = new File(jarPath);
        if (!f.exists()) return null;
        try (JarFile jf = new JarFile(f)) {
            for (String ext : Arrays.asList("", "-src", "-sources")) {
                for (String pkg : Arrays.asList("", "/src/main/java", "/src")) {
                    String path = entryName + ext + ".java";
                    JarEntry e = jf.getJarEntry(path);
                    if (e == null) {
                        path = entryName + ext + ".kt";
                        e = jf.getJarEntry(path);
                    }
                    if (e != null) {
                        try (InputStream is = jf.getInputStream(e)) {
                            String text = new String(is.readAllBytes());
                            return new ResolvedSource(Kind.SOURCE_JAR, className,
                                    jarPath + "!/" + e.getName(), text, jarPath + "!/" + e.getName(), null);
                        }
                    }
                }
            }
        } catch (IOException e) { /* ignore */ }
        return null;
    }

    private ResolvedSource readClassBytes(String jarPath, String className) {
        if (decompiler == null) return null;
        String entryName = className.replace('.', '/') + ".class";
        File f = new File(jarPath);
        if (!f.exists()) return null;
        byte[] bytes = null;
        
        // Check if it's a directory (classpath directory) or a JAR file
        if (f.isDirectory()) {
            // Directories contain .class files directly on filesystem
            File classFile = new File(f, entryName);
            if (classFile.exists()) {
                try {
                    bytes = Files.readAllBytes(classFile.toPath());
                } catch (IOException e) { return null; }
            }
        } else {
            // JAR file
            try (JarFile jf = new JarFile(f)) {
                JarEntry e = jf.getJarEntry(entryName);
                if (e != null) {
                    try (InputStream is = jf.getInputStream(e)) {
                        bytes = is.readAllBytes();
                    }
                }
            } catch (IOException e) { return null; }
        }
        
        if (bytes == null) return null;
        DecompileResult r = decompiler.decompile(DecompileRequest.builder()
                .className(className).classBytes(bytes).classpathEntry(jarPath).build());
        if (r.isOk()) {
            return new ResolvedSource(Kind.DECOMPILED, className,
                    "[" + className + "] (decompiled from " + jarPath + ")",
                    r.source, jarPath, null);
        }
        return new ResolvedSource(Kind.MISSING, className, null, null, jarPath,
                "decompile failed: " + r.failure);
    }

    private ResolvedSource builtin(String className) {
        // java.lang.*, java.util.* etc - return synthetic source
        if (className.startsWith("java.lang.") || className.startsWith("java.util.")
                || className.startsWith("java.io.") || className.startsWith("java.nio.")) {
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            String text = "public final class " + simpleName + " { /* builtin */ }";
            return new ResolvedSource(Kind.BUILTIN, className,
                    "[" + className + "] (builtin)", text, "builtin:" + className, null);
        }
        return null;
    }
}