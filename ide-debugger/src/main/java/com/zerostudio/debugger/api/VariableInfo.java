/*
 *  ZeroStudio IDE - ide-debugger
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;

public final class VariableInfo {
    public final long id; // object id for object references; 0 for primitives
    public final String slotTag; // raw JDWP tag byte
    @NonNull public final String name;
    @NonNull public final String typeSignature;
    @NonNull public final String value;
    public final boolean isPrimitive;
    public final int slot;

    public VariableInfo(
            long id,
            String slotTag,
            @NonNull String name,
            @NonNull String typeSignature,
            @NonNull String value,
            boolean isPrimitive,
            int slot) {
        this.id = id;
        this.slotTag = slotTag;
        this.name = name;
        this.typeSignature = typeSignature;
        this.value = value;
        this.isPrimitive = isPrimitive;
        this.slot = slot;
    }
}
