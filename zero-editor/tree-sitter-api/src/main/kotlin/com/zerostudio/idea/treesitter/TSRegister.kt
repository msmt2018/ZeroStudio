@file:JvmName("TSRegister")

package com.zerostudio.idea.treesitter

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import java.lang.ref.Cleaner

internal object TSRegister {
    val INSTANCE = if (SDK_INT >= TIRAMISU) Cleaner.create() else null

    @JvmName("register")
    operator fun invoke(obj: Any, action: Runnable): Cleaner.Cleanable? {
        return INSTANCE?.register(obj, action) ?: null
    }
}
