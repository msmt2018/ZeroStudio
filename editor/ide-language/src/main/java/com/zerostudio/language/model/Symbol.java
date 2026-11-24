package com.zerostudio.language.model;
public final class Symbol {
    public final String name;
    public final String fqn;
    public final SymbolKind kind;
    /** 旧 API：声明所在类。 */
    public final String declaringClass;
    /** 新 API：声明所在容器（类 / 命名空间 / 函数）的 FQN。 */
    public final String containerName;
    public final String sourcePath;
    /** 新 API：声明在源文件中的位置。 */
    public final SourceRange range;
    /** 新 API：所属语言。 */
    public final LanguageId language;

    /** 旧 5 参数构造（向后兼容）。 */
    public Symbol(String name, String fqn, SymbolKind kind, String declaringClass, String sourcePath) {
        this(name, fqn, kind, declaringClass, sourcePath, null, null);
    }
    /** 新 7 参数构造（按 CDeclExtractor / KotlinDeclExtractor / TreeSitterCNativeParser 期望）。 */
    public Symbol(String name, String fqn, SymbolKind kind, String containerName,
                  String sourcePath, SourceRange range, LanguageId language) {
        this.name = name; this.fqn = fqn; this.kind = kind;
        this.declaringClass = containerName;
        this.containerName = containerName;
        this.sourcePath = sourcePath;
        this.range = range;
        this.language = language;
    }
}
