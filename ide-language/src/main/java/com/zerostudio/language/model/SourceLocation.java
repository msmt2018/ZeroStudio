package com.zerostudio.language.model;

import java.util.Objects;

/**
 * A request from the editor: "the user is at this position in this file;
 * which symbol are they pointing at?". Used by Go-to-Definition, hover,
 * find-references.
 */
public final class SourceLocation {
    public final String file;
    public final SourcePosition position;

    public SourceLocation(String file, SourcePosition position) {
        this.file = Objects.requireNonNull(file);
        this.position = Objects.requireNonNull(position);
    }

    public SourceLocation(String file, int line, int column) {
        this(file, new SourcePosition(line, column));
    }
}
