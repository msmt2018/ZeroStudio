package com.zerostudio.idea.treesitter

import dalvik.annotation.optimization.CriticalNative
import dalvik.annotation.optimization.FastNative
import java.lang.ref.Cleaner
import java.util.concurrent.atomic.AtomicBoolean


/**
 * A class that represents a syntax tree.
 *
 * __NOTE:__ If you're targeting Android SDK level < 33,
 * you must `use` or [close] the instance to free up resources.
 */
class TSTree internal constructor(
    private val self: Long,
    private val source: String?,
    val language: TSLanguage
): AutoCloseable {
    
    private val cleaner: Cleaner.Cleanable?
    private val deleted = AtomicBoolean(false)
    
    init {
        cleaner = TSRegister(this, CleanAction(self))
    }
    
    /** The root node of the syntax tree. */
    @get:JvmName("getRootNode")
    val rootNode: TSNode
        @FastNative external get
        
    /** The included ranges that were used to parse the syntax tree. */
    @get:JvmName("includedRanges")
    val includedRanges: List<TSRange>
        @FastNative external get
    /**
     * Get the root node of the syntax tree, but with
     * its position shifted forward by the given offset.
     */
    @FastNative
    @JvmName("rootNodeWithOffset")
    external fun rootNodeWithOffset(offset: UInt, point: TSPoint): TSNode?

    /**
     * Edit the syntax tree to keep it in sync
     * with source code that has been modified.
     * for UTF-16 encoding requires start and end byte * 2
     * for UTF-16 encoding requires start and end point.column * 2
     */
    @FastNative
    external fun edit(edit: TSInputEdit)
    
    @FastNative
    external fun dotGraph(pathname: String)
    
    /**
     * Create a shallow copy of the syntax tree.
     *
     * You need to copy a syntax tree in order to use it on multiple
     * threads or coroutines, as syntax trees are not thread safe.
     */
    fun copy() = TSTree(copy(self), source, language).also { it.textReader = textReader }

    /** Create a new tree cursor starting from the node of the tree. */
    fun walk() = TSTreeCursor(rootNode)
    
    /** Get the source code of the syntax tree, if available. */
    fun text(): CharSequence? = source

    /**
     * Reads the text of the tree where it has none of its own: a tree parsed from a
     * callback (see [TSParser.parse]) keeps no copy of the text. Set by whoever
     * parsed it, from where the text is (an editor's buffer, say); must read the
     * text the tree was parsed from, so a tree that is reused after an edit has to
     * be parsed again (or edited) first.
     */
    @Volatile
    var textReader: TextReader? = null

    /** Whether the text of the tree can be had: its own, or through [textReader]. */
    fun hasText(): Boolean = source != null || textReader != null

    /** Reads the chars from [start] (inclusive) to [end] (exclusive) of the text of a tree. */
    fun interface TextReader {
        fun read(start: Int, end: Int): CharSequence
    }
    /**
     * Compare an old edited syntax tree to a new
     * syntax tree representing the same document.
     *
     * For this to work correctly, this tree must have been
     * edited such that its ranges match up to the new tree.
     *
     * @return A list of ranges whose syntactic structure has changed.
     */
    external fun changedRanges(newTree: TSTree): List<TSRange>

    override fun toString() = "TSTree(id=0x${self.toString(16)}, language=$language)"

    override fun close() {
        if (deleted.compareAndSet(false, true)) {
            if (cleaner != null)
                cleaner.clean()
            else 
                delete(self)
        }
    }

    private class CleanAction(private val ptr: Long) : Runnable {
        override fun run() = delete(ptr)
    }

    private companion object {
        @JvmStatic
        @CriticalNative
        private external fun copy(self: Long): Long

        @JvmStatic
        @CriticalNative
        private external fun delete(self: Long)
    }
}

