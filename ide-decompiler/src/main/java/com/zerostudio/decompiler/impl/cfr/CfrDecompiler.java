package com.zerostudio.decompiler.impl.cfr;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zerostudio.decompiler.api.CfrOptionKeys;
import com.zerostudio.decompiler.api.DecompileRequest;
import com.zerostudio.decompiler.api.DecompileResult;
import com.zerostudio.decompiler.api.Decompiler;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CFR-backed implementation of {@link Decompiler}.
 *
 * <p>For every {@link #decompile(DecompileRequest)} call we build a
 * new {@link CfrDriver} from the request, hand it an in-memory
 * {@link ClassFileSource} that wraps the supplied bytes (and optionally
 * a directory / JAR for cross-references), and let CFR stream the
 * reconstructed source into our sink.
 *
 * <p>The driver and its supporting state are NOT shared across calls:
 * CFR keeps a fair amount of global state and the only safe way to
 * decompile a class is to give it a fresh driver.
 */
public final class CfrDecompiler implements Decompiler {

    private final String engineName;
    private final String engineVersion;

    public CfrDecompiler() {
        this("cfr", detectVersion());
    }

    public CfrDecompiler(String name, String version) {
        this.engineName = name;
        this.engineVersion = version;
    }

    @NonNull
    @Override
    public String name() { return engineName; }

    @NonNull
    @Override
    public String version() { return engineVersion; }

    @NonNull
    @Override
    public DecompileResult decompile(@NonNull DecompileRequest req) {
        try {
            return decompileInternal(req);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            return DecompileResult.fail(req.className,
                    "CfrDecompiler crashed: " + t.getClass().getSimpleName()
                            + ":" + t.getMessage());
        }
    }

    private DecompileResult decompileInternal(@NonNull DecompileRequest req) {
        if (req.classBytes == null && req.classpathEntry == null) {
            return DecompileResult.fail(req.className,
                    "no class bytes or classpath entry provided");
        }

        // CFR works best when it can resolve classes via real disk
        // paths. To support in-memory bytes, we materialise them in a
        // temp dir keyed by their className, then point CFR at the
        // file path. For a classpath entry (a directory or JAR), we
        // resolve the class file inside it and hand CFR the file path.
        String analysePath = null;
        java.io.File tempDir = null;
        java.io.File tempClassFile = null;
        try {
            if (req.classBytes != null) {
                tempClassFile = materialiseClassFile(req.className, req.classBytes);
                if (tempClassFile == null) {
                    return DecompileResult.fail(req.className,
                            "could not materialise temp .class file");
                }
                tempDir = tempClassFile.getParentFile().getParentFile();
                analysePath = tempClassFile.getAbsolutePath();
            } else if (req.classpathEntry != null) {
                analysePath = resolveInClasspath(req.classpathEntry,
                        req.className);
                if (analysePath == null) {
                    return DecompileResult.fail(req.className,
                            "class " + req.className
                                    + " not found in " + req.classpathEntry);
                }
            }
            if (analysePath == null) {
                return DecompileResult.fail(req.className,
                        "no analyse path available");
            }

            // CFR works without a custom source if we give it a real
            // file path. The default file source is what the CLI uses
            // and is the most robust path. We only build a custom
            // source if we need to add extra classpath entries.
            CapturingSink sink = new CapturingSink();

            Map<String, String> options = new HashMap<>(req.options);
            options.put("hideutf", "true");
            options.put("hidebanner", "true");
            options.put("usenametable", "true");
            options.put("trackbytecodeloc", "true");
            options.put("comments", "false");

            CfrDriver.Builder builder = new CfrDriver.Builder()
                    .withOutputSink(sink)
                    .withOptions(options);
            if (req.additionalClasspath != null
                    && !req.additionalClasspath.isEmpty()) {
                ChainedFileSource src = new ChainedFileSource(
                        buildClasspath(req.classpathEntry,
                                req.additionalClasspath));
                builder = builder.withClassFileSource(src);
            }

            CfrDriver driver = builder.build();

            List<String> toAnalyse = new ArrayList<>();
            toAnalyse.add(analysePath);
            Throwable driverErr = null;
            try {
                driver.analyse(toAnalyse);
            } catch (Throwable t) {
                driverErr = t;
            }

            if (driverErr != null) {
                return DecompileResult.fail(req.className,
                        "CFR driver threw: " + driverErr.getMessage());
            }
            SinkReturns.Decompiled dec = sink.byClass.get(req.className);
            if (dec == null && sink.byClass.size() == 1) {
                // CFR may rename the class slightly (e.g. add $1 for
                // inner classes). If there's only one output, use it.
                dec = sink.byClass.values().iterator().next();
            }
            if (dec == null) {
                String err = "CFR produced no output for " + req.className
                        + " (sink map size=" + sink.byClass.size() + ")";
                if (!sink.exceptions.isEmpty())
                    err = "EX:" + String.join(" | ", sink.exceptions);
                else if (!sink.strings.isEmpty())
                    err = "ST:" + String.join(" | ", sink.strings);
                return DecompileResult.fail(req.className, err);
            }

            Map<Integer, Long> lineMap = new HashMap<>();
            for (SinkReturns.LineNumberMapping m : sink.lineMappings) {
                NavigableMap<Integer, Integer> mappings = m.getMappings();
                if (mappings == null) continue;
                for (Map.Entry<Integer, Integer> e : mappings.entrySet()) {
                    lineMap.put(e.getKey(), e.getValue().longValue());
                }
            }
            return DecompileResult.ok(req.className, dec.getJava(), lineMap);
        } finally {
            if (tempClassFile != null) {
                java.io.File parent = tempClassFile.getParentFile();
                if (parent != null) parent.delete();
            }
            if (tempDir != null) deleteRecursively(tempDir);
        }
    }

    /**
     * Locate a class file inside a classpath entry. The entry may be
     * a directory (we look for {@code com/example/Foo.class} inside)
     * or a JAR (we look for the same path inside the zip).
     */
    private static String resolveInClasspath(String classpathEntry,
                                             String className) {
        java.io.File entry = new java.io.File(classpathEntry);
        if (!entry.exists()) return null;
        if (entry.isDirectory()) {
            java.io.File f = new java.io.File(entry,
                    className.replace('.', '/') + ".class");
            return f.isFile() ? f.getAbsolutePath() : null;
        }
        if (entry.getName().toLowerCase().endsWith(".jar")) {
            String entryName = className.replace('.', '/') + ".class";
            try (java.util.jar.JarFile jf = new java.util.jar.JarFile(entry)) {
                java.util.zip.ZipEntry e = jf.getEntry(entryName);
                if (e == null) return null;
                // CFR can analyse JAR entries directly by writing the
                // bytes to a temp file.
                java.io.File tmp = new java.io.File(
                        System.getProperty("java.io.tmpdir"),
                        "ide-cfr-jar-" + System.nanoTime() + "-"
                                + entry.getName());
                tmp.getParentFile().mkdirs();
                try (java.io.InputStream in = jf.getInputStream(e);
                     java.io.FileOutputStream out =
                             new java.io.FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                return tmp.getAbsolutePath();
            } catch (java.io.IOException ex) {
                return null;
            }
        }
        return null;
    }

    private static java.io.File materialiseClassFile(String className,
                                                     byte[] bytes) {
        java.io.File base = new java.io.File(
                System.getProperty("java.io.tmpdir"),
                "ide-cfr-" + System.nanoTime());
        if (!base.mkdirs()) return null;
        String relative = className.replace('.', '/') + ".class";
        java.io.File out = new java.io.File(base, relative);
        out.getParentFile().mkdirs();
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            fos.write(bytes);
        } catch (java.io.IOException e) {
            return null;
        }
        return out;
    }

    private static void deleteRecursively(java.io.File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            java.io.File[] kids = f.listFiles();
            if (kids != null) for (java.io.File k : kids) deleteRecursively(k);
        }
        f.delete();
    }

    private static List<String> buildClasspath(String primary,
                                               List<String> additional) {
        List<String> cp = new ArrayList<>();
        if (primary != null) cp.add(primary);
        if (additional != null) cp.addAll(additional);
        return cp;
    }

    private static String detectVersion() {
        Package p = CfrDriver.class.getPackage();
        if (p != null) {
            String v = p.getImplementationVersion();
            if (v != null && !v.isEmpty()) return v;
        }
        return "0.152";
    }

    /**
     * An in-memory ClassFileSource. Bytes for the primary class are
     * looked up first; everything else is delegated to a chained
     * file-based source that reads from the optional classpath entry
     * (a directory, a JAR, or a list of either).
     */
    private static final class InMemoryClassFileSource implements ClassFileSource {
        private final Map<String, byte[]> primaryBytes;
        private final List<String> classpathEntries;
        private final ChainedFileSource delegate;

        InMemoryClassFileSource(Map<String, byte[]> primary,
                                @Nullable String classpathEntry,
                                List<String> additionalClasspath) {
            this.primaryBytes = primary;
            List<String> cp = new ArrayList<>();
            if (classpathEntry != null) cp.add(classpathEntry);
            cp.addAll(additionalClasspath);
            this.classpathEntries = Collections.unmodifiableList(cp);
            this.delegate = new ChainedFileSource(this.classpathEntries);
        }

        @Override
        public void informAnalysisRelativePathDetail(String a, String b) {
            // no-op
        }

        @Override
        public Collection<String> addJar(String jar) {
            // CFR asks whether the source knows about a JAR it found
            // by classpath. We don't auto-accept arbitrary jars.
            return Collections.emptyList();
        }

        @Override
        public String getPossiblyRenamedPath(String className) {
            if (primaryBytes.containsKey(className)) {
                return "in-memory/" + className.replace('.', '/') + ".class";
            }
            String p = delegate.getPossiblyRenamedPath(className);
            return p == null ? className : p;
        }

        @Override
        public Pair<byte[], String> getClassFileContent(String className)
                throws java.io.IOException {
            if (primaryBytes.containsKey(className)) {
                return Pair.make(primaryBytes.get(className),
                        "in-memory:" + className);
            }
            return delegate.getClassFileContent(className);
        }
    }

    /**
     * Reads .class files from a list of directories and JAR files. Each
     * path is tried in order; the first match wins. CFR uses this to
     * resolve cross-referenced classes (supertypes, referenced types).
     */
    private static final class ChainedFileSource implements ClassFileSource {
        private final List<PathEntry> entries;

        ChainedFileSource(List<String> paths) {
            List<PathEntry> es = new ArrayList<>();
            for (String p : paths) {
                PathEntry e = PathEntry.open(p);
                if (e != null) es.add(e);
            }
            this.entries = es;
        }

        @Override
        public void informAnalysisRelativePathDetail(String a, String b) { /* no-op */ }

        @Override
        public Collection<String> addJar(String jar) {
            return Collections.emptyList();
        }

        @Override
        public String getPossiblyRenamedPath(String className) {
            for (PathEntry e : entries) {
                String s = e.pathFor(className);
                if (s != null) return s;
            }
            return className;
        }

        @Override
        public Pair<byte[], String> getClassFileContent(String className)
                throws java.io.IOException {
            for (PathEntry e : entries) {
                byte[] b = e.readClass(className);
                if (b != null) {
                    return Pair.make(b, e.pathFor(className));
                }
            }
            return null;
        }
    }

    /** Per-entry reader: either a directory or a JAR. */
    private static abstract class PathEntry {
        static PathEntry open(String path) {
            if (path == null || path.isEmpty()) return null;
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return null;
            if (f.isDirectory()) return new DirEntry(f);
            if (f.getName().toLowerCase().endsWith(".jar")) {
                return new JarEntry(f);
            }
            return null;
        }
        abstract byte[] readClass(String className);
        abstract String pathFor(String className);
    }

    private static final class DirEntry extends PathEntry {
        final java.io.File dir;
        DirEntry(java.io.File d) { this.dir = d; }
        @Override byte[] readClass(String className) {
            java.io.File f = classFile(className);
            if (f == null || !f.isFile()) return null;
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[(int) f.length()];
                int read = 0;
                while (read < buf.length) {
                    int n = in.read(buf, read, buf.length - read);
                    if (n < 0) break;
                    read += n;
                }
                return buf;
            } catch (java.io.IOException e) {
                return null;
            }
        }
        @Override String pathFor(String className) {
            java.io.File f = classFile(className);
            return f == null ? null : f.getAbsolutePath();
        }
        private java.io.File classFile(String cn) {
            return new java.io.File(dir,
                    cn.replace('.', '/') + ".class");
        }
    }

    private static final class JarEntry extends PathEntry {
        final java.io.File jar;
        JarEntry(java.io.File j) { this.jar = j; }
        @Override byte[] readClass(String className) {
            try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar)) {
                String entryName = className.replace('.', '/') + ".class";
                java.util.zip.ZipEntry e = jf.getEntry(entryName);
                if (e == null) return null;
                try (java.io.InputStream in = jf.getInputStream(e)) {
                    java.io.ByteArrayOutputStream out =
                            new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    return out.toByteArray();
                }
            } catch (java.io.IOException ex) {
                return null;
            }
        }
        @Override String pathFor(String className) {
            return jar.getAbsolutePath() + "!" + className;
        }
    }

    /**
     * Captures everything CFR writes out. CFR's
     * {@link OutputSinkFactory#getSink} is called by the driver to
     * obtain a sink for each output type; we only care about JAVA
     * (decompiled source) and LINENUMBER (line-number mapping).
     */
    private static final class CapturingSink implements OutputSinkFactory {
        final Map<String, SinkReturns.Decompiled> byClass = new ConcurrentHashMap<>();
        final List<SinkReturns.LineNumberMapping> lineMappings =
                Collections.synchronizedList(new ArrayList<>());
        final List<String> exceptions = Collections.synchronizedList(new ArrayList<>());
        final List<String> strings = Collections.synchronizedList(new ArrayList<>());

        @Override
        public List<SinkClass> getSupportedSinks(SinkType sinkType,
                                                 Collection<SinkClass> available) {
            // The official CFR API example returns the same SinkClass
            // regardless of sinkType. We do the same: pick DECOMPILED
            // if available, otherwise fall back to STRING.
            List<SinkClass> out = new ArrayList<>();
            if (available.contains(SinkClass.DECOMPILED)) {
                out.add(SinkClass.DECOMPILED);
            } else if (available.contains(SinkClass.STRING)) {
                out.add(SinkClass.STRING);
            }
            if (sinkType == SinkType.LINENUMBER
                    && available.contains(SinkClass.LINE_NUMBER_MAPPING)) {
                out.add(SinkClass.LINE_NUMBER_MAPPING);
            }
            if (sinkType == SinkType.EXCEPTION
                    && available.contains(SinkClass.EXCEPTION_MESSAGE)) {
                out.add(SinkClass.EXCEPTION_MESSAGE);
            } else if (sinkType == SinkType.EXCEPTION
                    && available.contains(SinkClass.STRING)) {
                out.add(SinkClass.STRING);
            }
            return out;
        }

        @Override
        public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
            if (sinkType == SinkType.JAVA) {
                if (sinkClass == SinkClass.DECOMPILED) {
                    return (Sink<T>) new JavaSink(byClass);
                }
                if (sinkClass == SinkClass.STRING) {
                    return (Sink<T>) new StringJavaSink(byClass);
                }
            }
            if (sinkType == SinkType.LINENUMBER) {
                return (Sink<T>) new LineNumberSink(lineMappings);
            }
            if (sinkType == SinkType.EXCEPTION) {
                if (sinkClass == SinkClass.EXCEPTION_MESSAGE) {
                    return (Sink<T>) new ExceptionMessageSink(exceptions);
                }
                if (sinkClass == SinkClass.STRING) {
                    return (Sink<T>) new StringSink(strings);
                }
            }
            if (sinkType == SinkType.SUMMARY) {
                if (sinkClass == SinkClass.STRING) {
                    return (Sink<T>) new StringSink(strings);
                }
            }
            return ignoreSink();
        }

        private static <T> Sink<T> ignoreSink() {
            return new Sink<T>() {
                @Override public void write(T t) { /* drop */ }
            };
        }
    }

    private static final class JavaSink implements OutputSinkFactory.Sink<SinkReturns.Decompiled> {
        private final Map<String, SinkReturns.Decompiled> byClass;
        JavaSink(Map<String, SinkReturns.Decompiled> map) { this.byClass = map; }
        @Override public void write(SinkReturns.Decompiled d) {
            byClass.put(d.getClassName(), d);
        }
    }

    private static final class StringSink implements OutputSinkFactory.Sink<String> {
        private final List<String> out;
        StringSink(List<String> out) { this.out = out; }
        @Override public void write(String s) { out.add(s); }
    }

    private static final class ExceptionMessageSink
            implements OutputSinkFactory.Sink<SinkReturns.ExceptionMessage> {
        private final List<String> out;
        ExceptionMessageSink(List<String> out) { this.out = out; }
        @Override public void write(SinkReturns.ExceptionMessage e) {
            // SinkReturns.ExceptionMessage is a thin wrapper around
            // a Throwable, but its getMessage() returns null when the
            // wrapped exception has no message. Render the class name
            // + "null" + stack-trace head so the caller can debug.
            String msg = e.getMessage();
            out.add(e.getClass().getSimpleName()
                    + (msg == null ? ":null" : ":" + msg));
        }
    }

    /**
     * Fallback sink used when CFR only offers STRING. CFR hands us the
     * decompiled source as a String. We have to remember the
     * {@code className} via a thread-local: the call to
     * {@code driver.analyse(List)} is single-threaded so this is safe
     * in practice.
     */
    private static final class StringJavaSink implements OutputSinkFactory.Sink<String> {
        private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
        private final Map<String, SinkReturns.Decompiled> byClass;
        StringJavaSink(Map<String, SinkReturns.Decompiled> map) { this.byClass = map; }
        @Override public void write(String s) {
            String name = CURRENT.get();
            if (name == null) name = "unknown";
            // SinkReturns.Decompiled is an interface; CFR ships a
            // default implementation. We don't have the bytecode
            // generation available, so we build a small wrapper.
            byClass.put(name, new WrappedDecompiled(name, s));
        }
    }

    /** A trivial Decompiled that just returns the source. */
    private static final class WrappedDecompiled
            implements SinkReturns.Decompiled {
        private final String name;
        private final String body;
        WrappedDecompiled(String name, String body) {
            this.name = name;
            this.body = body;
        }
        @Override public String getClassName() { return name; }
        @Override public String getJava() { return body; }
        @Override public String getPackageName() {
            int i = name.lastIndexOf('.');
            return i < 0 ? "" : name.substring(0, i);
        }
    }

    private static final class LineNumberSink
            implements OutputSinkFactory.Sink<SinkReturns.LineNumberMapping> {
        private final List<SinkReturns.LineNumberMapping> list;
        LineNumberSink(List<SinkReturns.LineNumberMapping> list) { this.list = list; }
        @Override public void write(SinkReturns.LineNumberMapping m) {
            list.add(m);
        }
    }
}
