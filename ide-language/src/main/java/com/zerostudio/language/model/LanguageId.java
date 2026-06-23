package com.zerostudio.language.model;

/**
 * Supported language identifiers.
 *
 * <p>This enum is the single source of truth for which languages the IDE can
 * analyze. New languages are added here AND registered in
 * {@link com.zerostudio.language.service.LanguageRegistry}.
 */
public enum LanguageId {
    JAVA("java", ".java"),
    KOTLIN("kotlin", ".kt"),
    C("c", ".c"),
    CPP("cpp", ".cpp"),
    JAVA_PROPERTIES("properties", ".properties"),
    XML("xml", ".xml");

    private final String id;
    private final String extension;

    LanguageId(String id, String extension) {
        this.id = id;
        this.extension = extension;
    }

    public String id() { return id; }

    public String extension() { return extension; }

    /**
     * Best-effort language detection by file extension. Returns {@code null}
     * for unknown extensions.
     */
    public static LanguageId fromExtension(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = filename.substring(dot).toLowerCase();
        for (LanguageId lang : values()) {
            if (lang.extension.equalsIgnoreCase(ext)) {
                return lang;
            }
        }
        return null;
    }
}
