/*
 *  ZeroStudio IDE - ide-debugger
 *  Symbol & DWARF Manager (Phase 20)
 *
 *  (module, address, offset) 三元组,用于 Native 帧 / 符号断点。
 *  module: .so 短名 (libfoo.so);
 *  address: 进程空间绝对地址 (long);
 *  offset:  相对模块加载基址的偏移,便于反符号化 (relocation-safe)。
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class NativeAddress {

    @NonNull public final String module;
    public final long address;
    public final long offset;
    @Nullable public final String functionName;

    public NativeAddress(@NonNull String module, long address, long offset,
                         @Nullable String functionName) {
        this.module = module;
        this.address = address;
        this.offset = offset;
        this.functionName = functionName;
    }

    @NonNull
    @Override
    public String toString() {
        return "NativeAddress{" + module + "+0x" + Long.toHexString(offset)
                + (functionName != null ? " (" + functionName + ")" : "")
                + " abs=0x" + Long.toHexString(address) + "}";
    }
}
