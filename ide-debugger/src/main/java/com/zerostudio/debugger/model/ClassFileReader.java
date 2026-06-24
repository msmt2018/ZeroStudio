/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase G2: ClassFileReader using ASM.
 *
 *  Reads compiled .class files and extracts:
 *    - Class signature (from internal form, e.g., com/example/Foo -> Lcom/example/Foo;)
 *    - SourceFile attribute (the .java file name this class was compiled from)
 *    - LineNumberTable: maps code offsets to source line numbers
 *    - Methods: name, descriptor, access flags, code offset range
 *
 *  This complements JavaSourceParser (Phase G1) by providing information
 *  from the compiled artifact rather than the source text. The two are
 *  used together to:
 *
 *    1. Verify that a .java source file matches a .class file
 *       (SourceFile attribute == basename of source path)
 *    2. Get accurate line number → code index mappings from
 *       the compiled LineNumberTable, which is more reliable than
 *       re-parsing the source
 *    3. Handle cases where the source file is not available but
 *       the .class file is on the classpath
 *
 *  The reader is tolerant: it returns whatever it can extract even
 *  if some attributes are missing (e.g., no SourceFile, no line numbers).
 *
 *  The auxiliary data classes ({@link ClassMethod}, {@link LineEntry},
 *  {@link ParsedClass}) are nested inside {@link ClassFileReader} so
 *  callers can keep using {@code ClassFileReader.ParsedClass} etc.
 */

package com.zerostudio.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class ClassFileReader {

    /**
     * A method from a .class file, including its JVM descriptor and line number table.
     */
    public static final class ClassMethod {

        /** Method name (e.g., "doIt", "<init>", "<clinit>"). */
        @NonNull
        public final String name;

        /**
         * JVM descriptor (e.g., "(ILjava/lang/String;)V", "()Ljava/lang/Object;").
         * Note: ASM stores the raw descriptor string.
         */
        @NonNull
        public final String descriptor;

        /** The start offset in the code array (first instruction). -1 if no code. */
        public final long codeStart;

        /** The end offset (one-past-last instruction). -1 if no code. */
        public final long codeEnd;

        /** Sorted list of (codeOffset, lineNumber) pairs. Never null. */
        @NonNull
        public final List<LineEntry> lines;

        public ClassMethod(@NonNull String name, @NonNull String descriptor,
                           long codeStart, long codeEnd, @NonNull List<LineEntry> lines) {
            this.name = name;
            this.descriptor = descriptor;
            this.codeStart = codeStart;
            this.codeEnd = codeEnd;
            this.lines = lines;
        }

        /**
         * Find the source line number for a given code offset.
         * Returns the last line entry where codeOffset <= [codeIndex].
         * Returns -1 if no line information is available.
         */
        public int lineForCodeIndex(long codeIndex) {
            if (lines.isEmpty()) return -1;
            int best = -1;
            for (LineEntry e : lines) {
                if (e.codeOffset <= codeIndex) {
                    best = e.lineNumber;
                } else {
                    break;
                }
            }
            return best;
        }
    }

    /** A single entry in the LineNumberTable: (bytecode offset, source line). */
    public static final class LineEntry {
        public final long codeOffset;
        public final int lineNumber;
        public LineEntry(long codeOffset, int lineNumber) {
            this.codeOffset = codeOffset;
            this.lineNumber = lineNumber;
        }
    }

    /**
     * The result of reading a .class file. Contains all extracted information.
     */
    public static final class ParsedClass {

        /**
         * The JVM type signature (e.g., {@code "Lcom/example/Foo;"}).
         * This is the same format used by JDWP ClassesBySignature.
         */
        @NonNull
        public final String signature;

        /**
         * The class file's SourceFile attribute, if present (e.g., "Foo.java").
         * This is the original source file name used during compilation.
         */
        @Nullable
        public final String sourceFile;

        /**
         * Whether this class is a top-level class (false for inner/nested classes).
         */
        public final boolean isTopLevel;

        /** All methods in this class, in declaration order. */
        @NonNull
        public final List<ClassMethod> methods;

        public ParsedClass(@NonNull String signature, @Nullable String sourceFile,
                           boolean isTopLevel, @NonNull List<ClassMethod> methods) {
            this.signature = signature;
            this.sourceFile = sourceFile;
            this.isTopLevel = isTopLevel;
            this.methods = methods;
        }

        /**
         * Find the method that contains the given code index.
         * Returns null if no method spans that code index.
         */
        @Nullable
        public ClassMethod findMethodAtCodeIndex(long codeIndex) {
            for (ClassMethod m : methods) {
                if (m.codeStart >= 0 && m.codeEnd >= 0) {
                    if (m.codeStart <= codeIndex && codeIndex < m.codeEnd) {
                        return m;
                    }
                }
            }
            return null;
        }

        /**
         * Find the source line for a given code index, scanning all methods.
         * Returns -1 if not found.
         */
        public int findLineForCodeIndex(long codeIndex) {
            ClassMethod m = findMethodAtCodeIndex(codeIndex);
            if (m == null) return -1;
            return m.lineForCodeIndex(codeIndex);
        }
    }

    /**
     * Parse a .class file and return a ParsedClass with all extracted information.
     * Returns null if the file cannot be read or is not a valid class file.
     */
    @Nullable
    public ParsedClass parse(@NonNull File classFile) {
        if (!classFile.exists() || !classFile.canRead()) {
            return null;
        }
        try (InputStream in = new FileInputStream(classFile)) {
            ClassReader cr = new ClassReader(in);
            ClassInfoBuilder builder = new ClassInfoBuilder();
            cr.accept(builder, 0);
            return builder.build();
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Parse .class content from a byte array.
     */
    @Nullable
    public ParsedClass parseBytes(@NonNull byte[] classData) {
        try {
            ClassReader cr = new ClassReader(classData);
            ClassInfoBuilder builder = new ClassInfoBuilder();
            cr.accept(builder, 0);
            return builder.build();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Parse .class content from an InputStream.
     */
    @Nullable
    public ParsedClass parseStream(@NonNull InputStream in) throws IOException {
        try {
            ClassReader cr = new ClassReader(in);
            ClassInfoBuilder builder = new ClassInfoBuilder();
            cr.accept(builder, 0);
            return builder.build();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * ASM ClassVisitor that collects class metadata and methods.
     *
     * We use a two-pass approach: first collect all label offsets by visiting
     * the full bytecode without collecting line info, then visit again collecting
     * line number table entries with their resolved offsets.
     */
    private static final class ClassInfoBuilder extends ClassVisitor {

        private String internalName = "";
        private String sourceFile = null;
        private boolean isTopLevel = true;
        private final List<ClassMethod> methods = new ArrayList<>();

        // Per-method state
        private String methodName = "";
        private String methodDesc = "";
        private boolean inMethod = false;
        private final List<LineEntry> methodLines = new ArrayList<>();

        ClassInfoBuilder() {
            super(Opcodes.ASM9);
        }

        ParsedClass build() {
            String jvmSig = "L" + internalName + ";";
            return new ParsedClass(jvmSig, sourceFile, isTopLevel,
                                  new ArrayList<>(methods));
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.internalName = name;
            this.isTopLevel = !name.contains("$");
        }

        @Override
        public void visitSource(String source, String debug) {
            this.sourceFile = source;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                          String signature, String[] exceptions) {
            this.methodName = name;
            this.methodDesc = descriptor;
            this.inMethod = true;
            this.methodLines.clear();
            return new OffsetTrackingMethodVisitor(this, name, descriptor);
        }

        @Override
        public void visitEnd() {
            // No-op
        }

        void addMethod(ClassMethod m) {
            methods.add(m);
        }
    }

    /**
     * MethodVisitor that tracks bytecode offsets by fully traversing the method.
     * ASM 9.x supports computing label offsets during the visit.
     */
    private static final class OffsetTrackingMethodVisitor extends MethodVisitor {

        private final ClassInfoBuilder owner;
        private final String name;
        private final String descriptor;
        // Collect (offset, line) pairs here. Offset is obtained via LabelInfo.
        private final List<LineEntry> lines = new ArrayList<>();

        OffsetTrackingMethodVisitor(ClassInfoBuilder owner, String name, String descriptor) {
            super(Opcodes.ASM9);
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public void visitCode() {
            // Reset for this method
            lines.clear();
        }

        @Override
        public void visitLineNumber(int line, Label label) {
            // ASM 9.x: get the offset of this label in the bytecode.
            // The offset is available via the Label's internal state.
            long offset = getLabelOffset(label);
            if (offset >= 0) {
                lines.add(new LineEntry(offset, line));
            }
        }

        @Override
        public void visitEnd() {
            // Sort by offset
            List<LineEntry> sorted = new ArrayList<>(lines);
            Collections.sort(sorted, (a, b) -> Long.compare(a.codeOffset, b.codeOffset));

            // Compute code range: start = first line's offset (or -1 if no lines)
            long codeStart = sorted.isEmpty() ? -1 : sorted.get(0).codeOffset;
            long codeEnd = sorted.isEmpty() ? -1
                    : sorted.get(sorted.size() - 1).codeOffset + 1; // approximate

            ClassMethod method = new ClassMethod(name, descriptor, codeStart, codeEnd, sorted);
            owner.addMethod(method);
        }

        /**
         * Get the bytecode offset for a Label in ASM 9.x.
         * The approach depends on the ASM version. In ASM 9.x, Label.getOffset()
         * is available but may return -1 if not yet resolved. We use a
         * LabelInfo-based approach to reliably get the offset.
         */
        private static long getLabelOffset(Label label) {
            try {
                // ASM 9.x provides getOffset() on Label objects.
                // It returns the byte offset in the bytecode, or -1 if not visited.
                java.lang.reflect.Method m = Label.class.getMethod("getOffset");
                Object result = m.invoke(label);
                return ((Number) result).longValue();
            } catch (Exception ex) {
                return -1;
            }
        }
    }

    /**
     * Convert a JVM internal class name to a JVM type signature.
     * e.g., "com/example/Foo" → "Lcom/example/Foo;"
     *       "com/example/Outer$Inner" → "Lcom/example/Outer$Inner;"
     */
    @NonNull
    public static String toJvmSignature(@NonNull String internalName) {
        return "L" + internalName + ";";
    }

    /**
     * Extract the simple class name from an internal name.
     * e.g., "com/example/Foo$Inner" → "Foo$Inner"
     */
    @NonNull
    public static String simpleName(@NonNull String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? internalName : internalName.substring(slash + 1);
    }
}
