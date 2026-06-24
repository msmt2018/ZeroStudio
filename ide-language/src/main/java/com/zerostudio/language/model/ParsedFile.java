package com.zerostudio.language.model;
import java.util.List;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.Reference;

public final class ParsedFile {
    public final String path;
    public final LanguageId language;
    public final String packageName;
    public final List<Reference> references;
    public final String rawText;
    public ParsedFile(String path, LanguageId language, String packageName,
                      List<Reference> references, String rawText) {
        this.path = path; this.language = language; this.packageName = packageName;
        this.references = references; this.rawText = rawText;
    }
}