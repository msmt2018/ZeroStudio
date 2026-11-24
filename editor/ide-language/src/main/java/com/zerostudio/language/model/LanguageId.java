package com.zerostudio.language.model;
public enum LanguageId {
    JAVA, KOTLIN, CPP, C, XML, GRADLE, MARKDOWN, PYTHON, JAVASCRIPT, TYPESCRIPT, GO, RUST, UNKNOWN;

    /** 稳定的语言标识符，用于按语言选择 resolver / lexer / 索引键。 */
    public String id() {
        return name().toLowerCase();
    }
}
