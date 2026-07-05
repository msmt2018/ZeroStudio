/*
 *  ZeroStudio IDE - ide-debugger
 *  Phase 20: 内存读取结果
 *
 *  包装 Debugger.readMemoryAsync 返回的字节 + 错误信息。
 *  简单 POJO,线程不可变。
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class MemoryReadResult {

    @NonNull public final byte[] bytes;
    @Nullable public final String error;

    private MemoryReadResult(@NonNull byte[] bytes, @Nullable String error) {
        this.bytes = bytes;
        this.error = error;
    }

    public boolean isError() { return error != null; }

    public static MemoryReadResult of(@NonNull byte[] bytes) {
        return new MemoryReadResult(bytes, null);
    }

    public static MemoryReadResult error(@NonNull String msg) {
        return new MemoryReadResult(new byte[0], msg);
    }
}
