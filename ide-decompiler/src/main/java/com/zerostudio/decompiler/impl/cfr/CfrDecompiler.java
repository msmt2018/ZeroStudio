package com.zerostudio.decompiler.impl.cfr;

import com.zerostudio.decompiler.api.*;
import com.zerostudio.decompiler.cache.CachingDecompiler;
import org.benf.cfr.reader.api.*;
import java.io.*;
import java.util.*;
import java.util.jar.*;

public final class CfrDecompiler implements Decompiler {
    public static final String NAME = "cfr";
    private final Map<String, String> defaultOptions;

    public CfrDecompiler() {
        this.defaultOptions = new HashMap<>();
        defaultOptions.put("hideutf", "true");
        defaultOptions.put("hidebanner", "true");
        defaultOptions.put("usenametable", "true");
        defaultOptions.put("trackbytecodeloc", "true");
        defaultOptions.put("comments", "false");
    }

    @Override public String name() { return NAME; }
    @Override public String version() { return "0.152"; }

    @Override
    public DecompileResult decompile(DecompileRequest req) {
        Map<String, String> opts = new HashMap<>(defaultOptions);
        if (req.options != null) opts.putAll(req.options);
        
        String analysePath = null;
        File tempClassFile = null;
        try {
            if (req.classBytes != null) {
                tempClassFile = materialiseClassFile(req.className, req.classBytes);
                analysePath = tempClassFile.getAbsolutePath();
            } else if (req.classpathEntry != null) {
                analysePath = resolveInClasspath(req.classpathEntry, req.className);
            } else {
                return DecompileResult.fail(req.className, "No classBytes or classpathEntry provided");
            }

            CapturingSink sink = new CapturingSink(req.className);
            CfrDriver driver = new CfrDriver.Builder()
                    .withOutputSink(sink)
                    .withOptions(opts).build();
            driver.analyse(Collections.singletonList(analysePath));

            SinkReturns.Decompiled dec = sink.byClass.get(req.className);
            if (dec == null) {
                return DecompileResult.fail(req.className, "CFR produced no output for: " + req.className);
            }

            Map<Integer, Long> lineMap = new HashMap<>();
            for (SinkReturns.LineNumberMapping m : sink.lineMappings) {
                NavigableMap<Integer, Integer> mappings = m.getMappings();
                for (Map.Entry<Integer, Integer> e : mappings.entrySet()) {
                    lineMap.put(e.getKey(), e.getValue().longValue());
                }
            }
            return DecompileResult.ok(req.className, dec.getJava(), lineMap);
        } catch (Exception e) {
            return DecompileResult.fail(req.className, e.getClass().getName() + ": " + e.getMessage());
        } finally {
            if (tempClassFile != null && tempClassFile.getParentFile() != null) {
                deleteRecursively(tempClassFile.getParentFile());
            }
        }
    }

    private File materialiseClassFile(String className, byte[] bytes) throws IOException {
        File tmp = new File("/tmp", "ide-cfr-" + System.nanoTime());
        tmp.mkdirs();
        String fileName = className.replace('.', File.separatorChar) + ".class";
        File out = new File(tmp, fileName);
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(bytes);
        }
        return out;
    }

    private String resolveInClasspath(String classpathEntry, String className) {
        String relative = className.replace('.', '/') + ".class";
        File f = new File(classpathEntry);
        if (f.isDirectory()) {
            File target = new File(f, relative);
            if (target.exists()) return target.getAbsolutePath();
        } else if (f.isFile()) {
            try (JarFile jf = new JarFile(f)) {
                JarEntry entry = jf.getJarEntry(relative);
                if (entry != null) return f.getAbsolutePath();
            } catch (IOException e) { /* ignore */ }
        }
        return f.exists() ? f.getAbsolutePath() : classpathEntry;
    }

    private void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }

    private static final class CapturingSink implements OutputSinkFactory {
        final Map<String, SinkReturns.Decompiled> byClass = new LinkedHashMap<>();
        final List<SinkReturns.LineNumberMapping> lineMappings = new ArrayList<>();
        private final String targetClass;

        CapturingSink(String targetClass) { this.targetClass = targetClass; }

        @Override
        public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
            List<SinkClass> out = new ArrayList<>();
            if (sinkType == SinkType.JAVA) {
                if (available.contains(SinkClass.DECOMPILED)) out.add(SinkClass.DECOMPILED);
                else if (available.contains(SinkClass.STRING)) out.add(SinkClass.STRING);
            } else if (sinkType == SinkType.LINENUMBER && available.contains(SinkClass.LINE_NUMBER_MAPPING)) {
                out.add(SinkClass.LINE_NUMBER_MAPPING);
            } else if (sinkType == SinkType.EXCEPTION && available.contains(SinkClass.EXCEPTION_MESSAGE)) {
                out.add(SinkClass.EXCEPTION_MESSAGE);
            }
            return out;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
            if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                return (Sink<T>) new DecompiledSink();
            } else if (sinkType == SinkType.JAVA && sinkClass == SinkClass.STRING) {
                return (Sink<T>) new DecompiledSink();
            } else if (sinkType == SinkType.LINENUMBER && sinkClass == SinkClass.LINE_NUMBER_MAPPING) {
                return (Sink<T>) new LineNumberSink();
            }
            return (Sink<T>) new NullSink();
        }

        private final class DecompiledSink implements Sink<SinkReturns.Decompiled> {
            @Override
            public void write(SinkReturns.Decompiled value) {
                byClass.put(targetClass, value);
            }
        }

        private final class LineNumberSink implements Sink<SinkReturns.LineNumberMapping> {
            @Override
            public void write(SinkReturns.LineNumberMapping value) {
                lineMappings.add(value);
            }
        }

        private static final class NullSink implements Sink<Object> {
            @Override public void write(Object value) {}
        }
    }
}