package com.zerostudio.ide.treesitter

/**
 * @author android_zero
 *
 * 变更说明：
 * 封装 TSLanguage 原生指针，支持动态加载 SO 中的解析器函数。
 */
class TSLanguage internal constructor(val ptr: Long) {

    fun getSymbolName(symbol: Int): String? = nativeGetSymbolName(ptr, symbol)

    fun getSymbolForName(name: String, isNamed: Boolean): Int =
        nativeGetSymbolForName(ptr, name, isNamed)

    val symbolCount: Int get() = nativeGetSymbolCount(ptr)
    val stateCount: Int get() = nativeGetStateCount(ptr)
    val fieldCount: Int get() = nativeGetFieldCount(ptr)

    fun getFieldNameForId(id: Int): String? = nativeGetFieldNameForId(ptr, id)
    fun nextState(state: Int, symbol: Int): Int = nativeNextState(ptr, state, symbol)

    companion object {
        @JvmStatic
        fun load(sharedLibPath: String, functionName: String): TSLanguage {
            val ptr = nativeLoadFromSharedLib(sharedLibPath, functionName)
            if (ptr == 0L) {
                throw IllegalStateException("Failed to load TSLanguage from: $sharedLibPath ($functionName)")
            }
            return TSLanguage(ptr)
        }

        @JvmStatic
        private external fun nativeLoadFromSharedLib(libPath: String, funcName: String): Long

        @JvmStatic
        private external fun nativeGetSymbolName(ptr: Long, symbol: Int): String?

        @JvmStatic
        private external fun nativeGetSymbolForName(ptr: Long, name: String, isNamed: Boolean): Int

        @JvmStatic
        private external fun nativeGetSymbolCount(ptr: Long): Int

        @JvmStatic
        private external fun nativeGetStateCount(ptr: Long): Int

        @JvmStatic
        private external fun nativeGetFieldCount(ptr: Long): Int

        @JvmStatic
        private external fun nativeGetFieldNameForId(ptr: Long, id: Int): String?

        @JvmStatic
        private external fun nativeNextState(ptr: Long, state: Int, symbol: Int): Int
    }
}