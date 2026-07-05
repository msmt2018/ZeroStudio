/*
 *  ZeroStudio IDE - ide-debugger
 *  Symbol & DWARF Manager (Phase 20)
 *
 *  解码后的源码位置 (含 R8/ProGuard 反混淆 + DWARF 还原)。
 *  用于把 JDI/Symbol 层上报的混淆名 / Native 地址翻译回源码
 *  真实位置 (file:line + 原方法/字段名)。
 *
 *  4 种 kind:
 *    JAVA          - 普通 Java 源码
 *    JAVA_OBF      - Java 反混淆后的 (R8 mapping)
 *    NATIVE_C      - C/C++ 源码 (DWARF 还原)
 *    NATIVE_UNKNOWN - Native 但无 DWARF 信息
 *
 *  字段都是不可变的。缺少时为 null / 0。
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class MappedSourceLocation {

    public enum Kind {
        JAVA,
        JAVA_OBFUSCATED,
        NATIVE_C,
        NATIVE_UNKNOWN
    }

    /** 原始混淆名 / 内部名 (来自 JDI/栈帧)。 */
    @NonNull public final String rawClassName;
    @Nullable public final String rawMethodName;
    @Nullable public final String rawFieldName;

    /** 映射后 (反混淆) 真实类名 (含包名 Lcom/example/Foo; -> com.example.Foo)。 */
    @NonNull public final String originalClassName;
    @Nullable public final String originalMethodName;
    @Nullable public final String originalFieldName;

    /** 源码文件 (相对项目根)。 */
    @Nullable public final String sourceFile;
    /** 1-based 行号;0 表示未知。 */
    public final int sourceLine;
    /** Native 函数地址 (Native_C / Native_Unknown 时非 0)。 */
    public final long nativeAddress;
    /** 所属 .so 模块名。 */
    @Nullable public final String nativeModule;

    @NonNull public final Kind kind;

    /** True if any mapping was applied (raw != original). */
    public final boolean remapped;

    public MappedSourceLocation(
            @NonNull String rawClassName,
            @Nullable String rawMethodName,
            @Nullable String rawFieldName,
            @NonNull String originalClassName,
            @Nullable String originalMethodName,
            @Nullable String originalFieldName,
            @Nullable String sourceFile,
            int sourceLine,
            long nativeAddress,
            @Nullable String nativeModule,
            @NonNull Kind kind) {
        this.rawClassName = rawClassName;
        this.rawMethodName = rawMethodName;
        this.rawFieldName = rawFieldName;
        this.originalClassName = originalClassName;
        this.originalMethodName = originalMethodName;
        this.originalFieldName = originalFieldName;
        this.sourceFile = sourceFile;
        this.sourceLine = sourceLine;
        this.nativeAddress = nativeAddress;
        this.nativeModule = nativeModule;
        this.kind = kind;
        this.remapped = !(rawClassName.equals(originalClassName)
                && eq(rawMethodName, originalMethodName)
                && eq(rawFieldName, originalFieldName));
    }

    private static boolean eq(@Nullable String a, @Nullable String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /** 用于 UI 展示的"短名"——取最后一段包路径。 */
    @NonNull
    public String shortClassName() {
        int idx = originalClassName.lastIndexOf('/');
        if (idx < 0) idx = originalClassName.lastIndexOf('.');
        return idx < 0 ? originalClassName : originalClassName.substring(idx + 1);
    }

    @NonNull
    @Override
    public String toString() {
        return "MappedSrc{cls=" + originalClassName
                + (originalMethodName != null ? "." + originalMethodName : "")
                + (sourceFile != null ? " @" + sourceFile + ":" + sourceLine : "")
                + (kind == Kind.NATIVE_C ? " (native @" + Long.toHexString(nativeAddress) + ")" : "")
                + (remapped ? " [remapped from " + rawClassName + "]" : "")
                + '}';
    }
}
