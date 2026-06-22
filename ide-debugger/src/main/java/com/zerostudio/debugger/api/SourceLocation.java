/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Decoded information about a source location in the target program.
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;

public final class SourceLocation {
    public final long classId;
    public final long methodId;
    public final long codeIndex;
    public final int lineNumber;
    @NonNull public final String sourceFile;

    public SourceLocation(
            long classId, long methodId, long codeIndex, int lineNumber, @NonNull String sourceFile) {
        this.classId = classId;
        this.methodId = methodId;
        this.codeIndex = codeIndex;
        this.lineNumber = lineNumber;
        this.sourceFile = sourceFile;
    }

    @Override
    public String toString() {
        return "SourceLocation{cls=" + classId
                + ", m=" + methodId
                + ", ci=" + codeIndex
                + ", line=" + lineNumber
                + ", file=" + sourceFile
                + '}';
    }
}
