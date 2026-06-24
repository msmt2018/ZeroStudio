package com.zerostudio.decompiler.api;

public final class DecompileRequest {
    public final String className;
    public final byte[] classBytes;
    public final String classpathEntry;
    public final java.util.List<String> additionalClasspath;
    public final java.util.Map<String, String> options;

    private DecompileRequest(String className, byte[] classBytes, String classpathEntry,
            java.util.List<String> additionalClasspath, java.util.Map<String, String> options) {
        this.className = className;
        this.classBytes = classBytes;
        this.classpathEntry = classpathEntry;
        this.additionalClasspath = additionalClasspath;
        this.options = options;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String className;
        private byte[] classBytes;
        private String classpathEntry;
        private java.util.List<String> additionalClasspath = new java.util.ArrayList<>();
        private java.util.Map<String, String> options = new java.util.HashMap<>();

        public Builder className(String v) { this.className = v; return this; }
        public Builder classBytes(byte[] v) { this.classBytes = v; return this; }
        public Builder classpathEntry(String v) { this.classpathEntry = v; return this; }
        public Builder additionalClasspath(java.util.List<String> v) { this.additionalClasspath = v; return this; }
        public Builder option(String key, String value) { this.options.put(key, value); return this; }
        public DecompileRequest build() {
            return new DecompileRequest(className, classBytes, classpathEntry, additionalClasspath, options);
        }
    }
}