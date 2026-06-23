package com.zerostudio.language.source;

import com.zerostudio.decompiler.api.DecompileRequest;
import com.zerostudio.decompiler.api.DecompileResult;
import com.zerostudio.decompiler.api.Decompiler;
import com.zerostudio.decompiler.api.DecompilerRegistry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Three-tier source resolver used by Go-to-Definition when the user
 * clicks on an imported class / method that is NOT in the workspace:
 *
 * <ol>
 *   <li>Workspace source (a parsed file in the project index).</li>
 *   <li>Source JAR: an attached {@code -sources.jar} whose entry
 *       matches the class FQN. We extract the .java / .kt file
 *       directly without decompilation.</li>
 *   <li>Class JAR + Decompiler (CFR): the .class file is decompiled
 *       on demand and the result is presented as a virtual file
 *       with a banner like {@code [decompiled from android.jar]}.</li>
 * </ol>
 *
 * <p>The resolver is read-only. It does not modify the project index.
 * All classpath entries are passed in at construction time; the
 * resolver never re-reads Gradle / Maven configuration.
 */
public final class SourceResolver {

    /** How the source was obtained. */
    public enum Kind {
        /** A file that lives in the user's workspace and is already parsed. */
        WORKSPACE_SOURCE,
        /** A .java / .kt file extracted from a -sources.jar. */
        SOURCE_JAR,
        /** Java source reconstructed by the decompiler from a .class file. */
        DECOMPILED,
        /** Built-in (e.g. java.lang.* on the bootclasspath). */
        BUILTIN,
        /** Nothing usable was found. */
        MISSING
    }

    /** The result of a resolution. */
    public static final class ResolvedSource {
        public final Kind kind;
        @Nullable public final String className;
        @NonNull public final String displayPath;
        @NonNull public final String sourceText;
        @Nullable public final String originPath;
        @Nullable public final String failure;

        private ResolvedSource(Kind kind, @Nullable String className,
                               @NonNull String displayPath,
                               @NonNull String sourceText,
                               @Nullable String originPath,
                               @Nullable String failure) {
            this.kind = kind;
            this.className = className;
            this.displayPath = displayPath;
            this.sourceText = sourceText;
            this.originPath = originPath;
            this.failure = failure;
        }

        public boolean isResolved() {
            return kind != Kind.MISSING;
        }

        public static ResolvedSource workspace(String path, String source) {
            return new ResolvedSource(Kind.WORKSPACE_SOURCE, null,
                    path, source, path, null);
        }
        public static ResolvedSource sourceJar(String className, String jar,
                                               String entry, String source) {
            return new ResolvedSource(Kind.SOURCE_JAR, className,
                    "[" + jar + "]" + entry, source, jar, null);
        }
        public static ResolvedSource decompiled(String className, String jar,
                                                String source, String banner) {
            return new ResolvedSource(Kind.DECOMPILED, className,
                    banner, source, jar, null);
        }
        public static ResolvedSource missing(String className, String reason) {
            return new ResolvedSource(Kind.MISSING, className,
                    className, "", null, reason);
        }
    }

    /** A classpath entry: source jar, class jar, or both. */
    public static final class ClasspathEntry {
        public enum Kind { SOURCE_JAR, CLASS_JAR }
        public final Kind kind;
        public final String path;
        public ClasspathEntry(Kind k, String p) {
            this.kind = Objects.requireNonNull(k);
            this.path = Objects.requireNonNull(p);
        }
        public static ClasspathEntry sourceJar(String p) {
            return new ClasspathEntry(Kind.SOURCE_JAR, p);
        }
        public static ClasspathEntry classJar(String p) {
            return new ClasspathEntry(Kind.CLASS_JAR, p);
        }
    }

    private final List<ClasspathEntry> classpath;
    private final Function<String, String> workspaceSourceLookup;
    private final Decompiler decompiler;
    private final ConcurrentHashMap<String, ResolvedSource> cache =
            new ConcurrentHashMap<>();

    public SourceResolver(@NonNull List<ClasspathEntry> classpath,
                          @NonNull Function<String, String> workspaceSourceLookup) {
        this(classpath, workspaceSourceLookup,
                DecompilerRegistry.firstOrNull());
    }

    public SourceResolver(@NonNull List<ClasspathEntry> classpath,
                          @NonNull Function<String, String> workspaceSourceLookup,
                          @Nullable Decompiler decompiler) {
        this.classpath = Collections.unmodifiableList(
                new ArrayList<>(classpath));
        this.workspaceSourceLookup = workspaceSourceLookup;
        this.decompiler = decompiler;
    }

    /**
     * Resolve a class to source code.
     *
     * @param className fully qualified class name, e.g.
     *                  {@code android.widget.Toast}
     */
    @NonNull
    public ResolvedSource resolve(@NonNull String className) {
        Objects.requireNonNull(className);
        ResolvedSource cached = cache.get(className);
        if (cached != null) return cached;
        ResolvedSource r = doResolve(className);
        cache.put(className, r);
        return r;
    }

    private ResolvedSource doResolve(String className) {
        // 1) Workspace.
        String wsPath = workspacePathOf(className);
        if (wsPath != null) {
            String src = workspaceSourceLookup.apply(wsPath);
            if (src != null) {
                return ResolvedSource.workspace(wsPath, src);
            }
        }
        // 2) Source JAR.
        for (ClasspathEntry cp : classpath) {
            if (cp.kind != ClasspathEntry.Kind.SOURCE_JAR) continue;
            SourceJarRead r = readFromSourceJar(cp.path, className);
            if (r != null) {
                return ResolvedSource.sourceJar(className, cp.path, r.entry, r.text);
            }
        }
        // 3) Class JAR + decompiler.
        for (ClasspathEntry cp : classpath) {
            if (cp.kind != ClasspathEntry.Kind.CLASS_JAR) continue;
            byte[] bytes = readClassBytes(cp.path, className);
            if (bytes == null) continue;
            if (decompiler == null) {
                return ResolvedSource.missing(className,
                        "class " + className
                                + " found in " + cp.path
                                + " but no decompiler registered");
            }
            DecompileResult dr = decompiler.decompile(
                    DecompileRequest.builder(className)
                            .classBytes(bytes)
                            .build());
            if (dr.isOk()) {
                String banner = "[decompiled from " + cp.path + "]";
                return ResolvedSource.decompiled(className, cp.path,
                        dr.source, banner);
            }
        }
        return ResolvedSource.missing(className,
                "class " + className + " not found in any source location");
    }

    /** Test-only: clear the resolution cache. */
    public void clearCache() { cache.clear(); }

    /**
     * Convert a FQN to the canonical workspace file path.
     * {@code com.example.Foo} becomes {@code com/example/Foo.java}.
     * Returns null when the FQN cannot be a source file (e.g. it
     * contains characters that cannot appear in a source file name).
     */
    @Nullable
    public static String workspacePathOf(@NonNull String fqn) {
        if (fqn.indexOf('/') >= 0) return null;
        return fqn.replace('.', '/') + ".java";
    }

    private static class SourceJarRead {
        final String entry;
        final String text;
        SourceJarRead(String e, String t) { this.entry = e; this.text = t; }
    }

    @Nullable
    private static SourceJarRead readFromSourceJar(String jar, String className) {
        String rel = className.replace('.', '/');
        // Try .java and .kt as both are valid sources.
        String[] candidates = { rel + ".java", rel + ".kt" };
        try (ZipFile zf = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> es = zf.entries();
            while (es.hasMoreElements()) {
                ZipEntry e = es.nextElement();
                String name = e.getName();
                for (String c : candidates) {
                    if (name.equals(c)) {
                        byte[] b = readAll(zf.getInputStream(e));
                        return new SourceJarRead(name, new String(b));
                    }
                }
            }
        } catch (IOException ex) {
            return null;
        }
        return null;
    }

    @Nullable
    private static byte[] readClassBytes(String jar, String className) {
        String entry = className.replace('.', '/') + ".class";
        try (ZipFile zf = new ZipFile(jar)) {
            ZipEntry e = zf.getEntry(entry);
            if (e == null) return null;
            return readAll(zf.getInputStream(e));
        } catch (IOException ex) {
            return null;
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
