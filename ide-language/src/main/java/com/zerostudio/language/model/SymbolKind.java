package com.zerostudio.language.model;

/** Categorisation of language symbols. */
public enum SymbolKind {
    PACKAGE,
    IMPORT,
    CLASS,
    INTERFACE,
    ENUM,
    ANNOTATION,
    METHOD,
    CONSTRUCTOR,
    FIELD,
    LOCAL_VARIABLE,
    PARAMETER,
    TYPE_PARAMETER,
    FUNCTION,         // C / C++ functions
    STRUCT,           // C struct
    UNION,            // C union
    ENUM_CONSTANT,
    NAMESPACE,        // C++ namespace
    TEMPLATE,         // C++ template
    MACRO,            // C preprocessor macro
    UNKNOWN
}
