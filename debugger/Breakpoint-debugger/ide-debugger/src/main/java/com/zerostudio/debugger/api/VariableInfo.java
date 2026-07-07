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
    /**
     * PR-D8.2: 求值失败标记。{@code true} 表示该变量的 value 是错误信息
     * (例如 "求值失败" / "IO 错误"),UI 可据此用 colorError 高亮。
     */
    public final boolean isError;

    public VariableInfo(
            long id,
            String slotTag,
            @NonNull String name,
            @NonNull String typeSignature,
            @NonNull String value,
            boolean isPrimitive,
            int slot) {
        this(id, slotTag, name, typeSignature, value, isPrimitive, slot, false);
    }

    public VariableInfo(
            long id,
            String slotTag,
            @NonNull String name,
            @NonNull String typeSignature,
            @NonNull String value,
            boolean isPrimitive,
            int slot,
            boolean isError) {
        this.id = id;
        this.slotTag = slotTag;
        this.name = name;
        this.typeSignature = typeSignature;
        this.value = value;
        this.isPrimitive = isPrimitive;
        this.slot = slot;
        this.isError = isError;
    }
}
