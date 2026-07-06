/*
 *  ZeroStudio IDE - ide-debugger
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public final class StackFrameInfo {
    public final long frameId;
    public final long threadId;
    public final long classId;
    public final long methodId;
    public final long codeIndex;
    public final int lineNumber;
    @NonNull public final String methodName;
    @NonNull public final String classSignature;
    @NonNull public final String sourceFile;
    @NonNull public final List<VariableInfo> variables;

    public StackFrameInfo(
            long frameId,
            long threadId,
            long classId,
            long methodId,
            long codeIndex,
            int lineNumber,
            @NonNull String methodName,
            @NonNull String classSignature,
            @NonNull String sourceFile,
            @Nullable List<VariableInfo> variables) {
        this.frameId = frameId;
        this.threadId = threadId;
        this.classId = classId;
        this.methodId = methodId;
        this.codeIndex = codeIndex;
        this.lineNumber = lineNumber;
        this.methodName = methodName;
        this.classSignature = classSignature;
        this.sourceFile = sourceFile;
        this.variables = variables == null ? Collections.emptyList() : variables;
    }
}
