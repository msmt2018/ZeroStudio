package com.zerostudio.decompiler.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Input to a decompilation request.
 *
 * <p>Either {@code classBytes} or {@code classpathEntry} (or both) must
 * be supplied. When both are present the decompiler prefers
 * {@code classpathEntry} for cross-references (so inner classes resolve
 * correctly) and uses {@code classBytes} for the actual class under
 * analysis.
 */
public final class DecompileRequest {

    /** Fully qualified class name, e.g. {@code android.widget.Toast}. */
    @NonNull
    public final String className;

    /** Raw bytes of the primary class. May be null when classpathEntry is set. */
    @Nullable
    public final byte[] classBytes;

    /**
     * Optional classpath entry (a directory, a JAR file, or a list of
     * such) that the decompiler can use to resolve references.
     */
    @Nullable
    public final String classpathEntry;

    /**
     * Optional {@code classpath:} scheme entries for cross-references.
     * For example: {@code /path/to/android.jar}.
     */
    @NonNull
    public final List<String> additionalClasspath;

    /**
     * Options passed through to the underlying decompiler engine.
     * Keys are engine-specific; see {@link CfrOptionKeys} for the
     * canonical set.
     */
    @NonNull
    public final Map<String, String> options;

    private DecompileRequest(@NonNull String className,
                             @Nullable byte[] classBytes,
                             @Nullable String classpathEntry,
                             @NonNull List<String> additionalClasspath,
                             @NonNull Map<String, String> options) {
        this.className = className;
        this.classBytes = classBytes;
        this.classpathEntry = classpathEntry;
        this.additionalClasspath = additionalClasspath;
        this.options = options;
    }

    public static Builder builder(@NonNull String className) {
        return new Builder(className);
    }

    public static final class Builder {
        private final String className;
        private byte[] classBytes;
        private String classpathEntry;
        private List<String> additionalClasspath = Collections.emptyList();
        private Map<String, String> options = Collections.emptyMap();

        private Builder(String className) {
            this.className = Objects.requireNonNull(className);
        }

        public Builder classBytes(byte[] b) {
            this.classBytes = b;
            return this;
        }

        public Builder classpathEntry(String e) {
            this.classpathEntry = e;
            return this;
        }

        public Builder additionalClasspath(List<String> cp) {
            this.additionalClasspath = cp == null
                    ? Collections.<String>emptyList() : cp;
            return this;
        }

        public Builder options(Map<String, String> o) {
            this.options = o == null
                    ? Collections.<String, String>emptyMap() : o;
            return this;
        }

        public DecompileRequest build() {
            return new DecompileRequest(className, classBytes,
                    classpathEntry, additionalClasspath, options);
        }
    }
}
