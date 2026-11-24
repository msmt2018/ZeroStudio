package com.zerostudio.language.model;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.SourceRange;

public final class Reference {
    public enum ReferenceKind {
        VARIABLE, METHOD, FIELD, CLASS, IMPORT, TYPE, PARAMETER,
        READ, CALL, EXTENDS
    }

    public final String name;
    public final SourceRange range;
    public final ReferenceKind kind;
    /** 旧 API：包含该引用的类名。 */
    public final String containingClass;
    /** 新 API：引用的 FQN（在容器内做符号解析时使用）。 */
    public final String containerFqn;
    public final String filePath;
    public final LanguageId language;

    public Reference(String name, SourceRange range, ReferenceKind kind,
                     String containingClass, String filePath, LanguageId language) {
        this(name, range, kind, containingClass, null, filePath, language);
    }
    /** 7 参数新构造（KotlinDeclExtractor / CDeclExtractor / TreeSitterCNativeParser 期望）。 */
    public Reference(String name, SourceRange range, ReferenceKind kind,
                     String container, String containerFqn, String filePath, LanguageId language) {
        this.name = name;
        this.range = range;
        this.kind = kind;
        this.containingClass = container;
        this.containerFqn = containerFqn;
        this.filePath = filePath;
        this.language = language;
    }
}
