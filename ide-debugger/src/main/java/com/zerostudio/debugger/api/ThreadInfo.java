/*
 *  ZeroStudio IDE - ide-debugger
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;

public final class ThreadInfo {
    public final long id;
    @NonNull public final String name;
    public final int status;
    public final boolean suspended;

    public ThreadInfo(long id, @NonNull String name, int status, boolean suspended) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.suspended = suspended;
    }
}
