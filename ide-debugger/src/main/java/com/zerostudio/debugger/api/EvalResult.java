/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Result of evaluating an expression. The string [displayValue] is what
 *  the UI shows in the watches / variables list; [typeSignature] is the
 *  JVM-style signature of the result (e.g. "I", "Ljava/lang/String;").
 *
 *  If evaluation failed, [error] is non-null and [displayValue] is null.
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class EvalResult {

    public enum Tag {
        VOID, BOOLEAN, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE,
        OBJECT, ARRAY, STRING
    }

    @NonNull public final Tag tag;
    @NonNull public final String typeSignature;
    @Nullable public final String displayValue;
    @Nullable public final String error;
    public final long objectId; // valid for OBJECT / ARRAY / STRING (after toString)

    private EvalResult(
            @NonNull Tag tag,
            @NonNull String typeSig,
            @Nullable String displayValue,
            @Nullable String error,
            long objectId) {
        this.tag = tag;
        this.typeSignature = typeSig;
        this.displayValue = displayValue;
        this.error = error;
        this.objectId = objectId;
    }

    public boolean isError() { return error != null; }

    public static EvalResult of(@NonNull Tag tag, @NonNull String typeSig,
                                 @NonNull String displayValue) {
        return new EvalResult(tag, typeSig, displayValue, null, 0L);
    }

    public static EvalResult object(long objectId, @NonNull String typeSig) {
        return new EvalResult(Tag.OBJECT, typeSig, "<object>", null, objectId);
    }

    public static EvalResult string(long objectId, @NonNull String displayValue) {
        return new EvalResult(Tag.STRING, "Ljava/lang/String;", displayValue, null, objectId);
    }

    public static EvalResult error(@NonNull String message) {
        return new EvalResult(Tag.OBJECT, "Ljava/lang/Object;", null, message, 0L);
    }
}
