package com.zerostudio.decompiler.impl.procyon;

import com.zerostudio.decompiler.api.*;

import java.io.*;
import java.util.*;
import java.util.jar.*;

public final class ProcyonDecompiler implements Decompiler {
    public static final String NAME = "procyon";

    @Override public String name() { return NAME; }
    @Override public String version() { return "0.5.36"; }

    @Override
    public DecompileResult decompile(DecompileRequest req) {
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

            String source = doDecompile(analysePath, req.className);
            if (source == null || source.trim().isEmpty()) {
                return DecompileResult.fail(req.className, "Procyon produced no output");
            }
            return DecompileResult.ok(req.className, source, Collections.emptyMap());
        } catch (Exception e) {
            return DecompileResult.fail(req.className, e.getClass().getName() + ": " + e.getMessage());
        } finally {
            if (tempClassFile != null && tempClassFile.getParentFile() != null) {
                deleteRecursively(tempClassFile.getParentFile());
            }
        }
    }

    private String doDecompile(String path, String className) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> decompilerClass = loader.loadClass("com.strobel.decompiler.Decompiler");
            Class<?> settingsClass = loader.loadClass("com.strobel.decompiler.DecompilerSettings");
            Object settings = settingsClass.getDeclaredConstructor().newInstance();
            settingsClass.getMethod("setForceExplicitImports", boolean.class).invoke(settings, true);
            settingsClass.getMethod("setIncludeNestedTypes", boolean.class).invoke(settings, true);
            settingsClass.getMethod("setIncludeMetadata", boolean.class).invoke(settings, false);
            settingsClass.getMethod("setRetainRedundantCasts", boolean.class).invoke(settings, false);
            settingsClass.getMethod("setRetainUnusedVariables", boolean.class).invoke(settings, false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            java.io.PrintWriter writer = new java.io.PrintWriter(baos);
            decompilerClass.getMethod("decompile", String.class, settingsClass, java.io.PrintWriter.class)
                    .invoke(null, className, settings, writer);
            writer.flush();
            return baos.toString("UTF-8");
        } catch (ClassNotFoundException e) {
            return fallbackDecompile(className);
        }
    }

    private String fallbackDecompile(String className) {
        StringBuilder sb = new StringBuilder();
        String pkg = className.contains(".") ? className.substring(0, className.lastIndexOf('.')) : "";
        String simple = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
        if (!pkg.isEmpty()) {
            sb.append("package ").append(pkg).append(";\n\n");
        }
        sb.append("public class ").append(simple).append(" {\n");
        sb.append("    // Procyon decompiler not available\n");
        sb.append("    // Install procyon-compilertools to enable full decompilation\n");
        sb.append("}\n");
        return sb.toString();
    }

    private File materialiseClassFile(String className, byte[] bytes) throws IOException {
        File tmp = new File("/tmp", "ide-procyon-" + System.nanoTime());
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
}
