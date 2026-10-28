package com.zerostudio.idea.treesitter

import dalvik.annotation.optimization.CriticalNative
import dalvik.annotation.optimization.FastNative
import java.lang.ref.Cleaner
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A class that is used to look up valid symbols in a specific parse state.
 *
 * Lookahead iterators can be useful to generate suggestions and improve syntax
 * error diagnostics. To get symbols valid in an `ERROR` node, use the lookahead
 * iterator on its first leaf node state. For `MISSING` nodes, a lookahead
 * iterator created on the previous non-extra leaf node may be appropriate.
 *
 * __NOTE:__ If you're targeting Android SDK level < 33,
 * you must `use` or [close] the instance to free up resources.
 */
class TSLookaheadIterator @Throws(IllegalArgumentException::class) internal constructor(
    language: TSLanguage,
    private val state: UShort
) : AbstractIterator<TSLookaheadIterator.Symbol>(), AutoCloseable {
    private val self: Long = init(language.self, state).takeIf { it > 0L }
        ?: throw IllegalArgumentException("State $state is not valid for $language")
    
    private val cleaner: Cleaner.Cleanable?
    private val deleted = AtomicBoolean(false)
    
    init {
        cleaner = TSRegister(this, CleanAction(self))
    }

    /** The current language of the lookahead iterator. */
    val language: TSLanguage
        @FastNative external get

    /**
     * The current symbol ID.
     *
     * The ID of the `ERROR` symbol is equal to `UShort.MAX_VALUE`.
     */
    @get:JvmName("getCurrentSymbol")
    val currentSymbol: UShort
        @FastNative external get

    /**
     * The current symbol name.
     *
     * Newly created lookahead iterators will contain the `ERROR` symbol.
     */
    val currentSymbolName: String
        @FastNative external get

    /** Iterate over the symbol IDs. */
    fun symbols(): Sequence<UShort> {
        reset(state)
        return sequence {
            while (nativeNext())
                yield(currentSymbol)
        }
    }

    /** Iterate over the symbol names. */
    fun symbolNames(): Sequence<String> {
        reset(state)
        return sequence {
            while (nativeNext())
                yield(currentSymbolName)
        }
    }
    
    /** Advance the lookahead iterator to the next symbol. */
    override fun next() = super.next()
   
    override fun computeNext() = if (nativeNext()) {
        setNext(Symbol(currentSymbol, currentSymbolName))
    } else {
        done()
    }

    operator fun iterator() = apply { reset(state, null) }

    override fun close() {
        if (deleted.compareAndSet(false, true)) {
            if (cleaner != null)
                cleaner.clean()
            else 
                delete(self)
        }
    }
    
    /** A class that pairs a symbol ID with its name. */
    @JvmRecord
    data class Symbol(val id: UShort, val name: String)

    private class CleanAction(private val ptr: Long) : Runnable {
        override fun run() = delete(ptr)
    }
    
    /**
     * Reset the lookahead iterator the given [state] and, optionally, another [language].
     *
     * @return `true` if the iterator was reset successfully or `false` if it failed.
     */
    @FastNative
    @JvmName("reset")
    external fun reset(state: UShort, language: TSLanguage? = null): Boolean
    
    @FastNative
    private external fun nativeNext(): Boolean

    private companion object {
        @JvmStatic
        @JvmName("init")
        @CriticalNative
        private external fun init(language: Long, state: UShort): Long

        @JvmStatic
        @CriticalNative
        private external fun delete(self: Long)
    }
}

