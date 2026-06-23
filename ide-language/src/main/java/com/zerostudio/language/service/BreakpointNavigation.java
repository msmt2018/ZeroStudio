package com.zerostudio.language.service;

import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Cursor-jump helpers used by the editor's "next / previous breakpoint"
 * actions.
 *
 * <p>This is intentionally decoupled from the debugger's
 * {@code BreakpointStore}: the editor does not know which breakpoints are
 * currently installed in the running app. The user marks the desired
 * breakpoints in the gutter; this service orders them by line within the
 * current file and returns the next/previous one relative to the cursor.
 *
 * <p>The breakpoints are identified by their gutter ranges (the lines
 * themselves). When a more accurate list is required, the caller can pass
 * a list of {@code SourceRange} objects instead of using the gutter model.
 */
public final class BreakpointNavigation {

    private BreakpointNavigation() {}

    /**
     * @param file the parsed file
     * @param cursor the cursor position
     * @return the line (0-based) of the next breakpoint strictly after the
     *         cursor in the same file, or -1 if there is none.
     */
    public static int nextBreakpointLine(ParsedFile file, SourcePosition cursor) {
        Objects.requireNonNull(file);
        Objects.requireNonNull(cursor);
        int best = -1;
        for (SourceRange b : markers(file)) {
            int line = b.start.line;
            if (line > cursor.line && (best < 0 || line < best)) best = line;
        }
        return best;
    }

    /**
     * @param file the parsed file
     * @param cursor the cursor position
     * @return the line (0-based) of the previous breakpoint strictly before
     *         the cursor in the same file, or -1 if there is none.
     */
    public static int previousBreakpointLine(ParsedFile file, SourcePosition cursor) {
        Objects.requireNonNull(file);
        Objects.requireNonNull(cursor);
        int best = -1;
        for (SourceRange b : markers(file)) {
            int line = b.start.line;
            if (line < cursor.line && (best < 0 || line > best)) best = line;
        }
        return best;
    }

    /**
     * Returns all "logical" breakpoint lines for the file - i.e. the start
     * line of every top-level declaration and method body. The editor's
     * gutter will render markers at these lines when the user toggles a
     * breakpoint.
     */
    public static List<Integer> candidateBreakpointLines(ParsedFile file) {
        Objects.requireNonNull(file);
        List<Integer> out = new ArrayList<>();
        for (var s : file.symbols) {
            if (s.range == null || !s.range.isValid()) continue;
            int line = s.range.start.line;
            if (!out.contains(line)) out.add(line);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static List<SourceRange> markers(ParsedFile file) {
        List<SourceRange> out = new ArrayList<>();
        for (var s : file.symbols) {
            if (s.range != null && s.range.isValid()) out.add(s.range);
        }
        return out;
    }
}
