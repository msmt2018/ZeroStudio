package com.zerostudio.language.parser;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;

/**
 * Generic AST node. Each parser produces a tree of these on top of its
 * language-specific root (kept in {@link ParsedFile#nativeAst}).
 */
public final class AstNode {
    public final String type;             // e.g. "class_declaration"
    public final String text;             // snippet covered by this node
    public final SourceRange range;
    public final AstNode parent;
    public final java.util.List<AstNode> children;

    public AstNode(String type,
                   String text,
                   SourceRange range,
                   AstNode parent,
                   java.util.List<AstNode> children) {
        this.type = type;
        this.text = text;
        this.range = range;
        this.parent = parent;
        this.children = children == null
                ? java.util.Collections.emptyList() : children;
    }

    /** Find the deepest node whose range contains the given position. */
    public AstNode findAt(SourcePosition pos) {
        if (!range.contains(pos)) return null;
        for (AstNode child : children) {
            AstNode hit = child.findAt(pos);
            if (hit != null) return hit;
        }
        return this;
    }
}
