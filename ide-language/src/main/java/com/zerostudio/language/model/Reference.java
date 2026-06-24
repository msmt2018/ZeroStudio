package com.zerostudio.language.model;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.SourcePosition;

public final class Reference {
    public enum ReferenceKind { VARIABLE, METHOD, FIELD, CLASS, IMPORT, TYPE, PARAMETER }

    public final String name;
    public final SourceRange range;
    public final ReferenceKind kind;
    public final String containingClass;
    public final String filePath;
    public final LanguageId language;

    public Reference(String name, SourceRange range, ReferenceKind kind,
                     String containingClass, String filePath, LanguageId language) {
        this.name = name;
        this.range = range;
        this.kind = kind;
        this.containingClass = containingClass;
        this.filePath = filePath;
        this.language = language;
    }
}