package com.zerostudio.idea.treesitter

import dalvik.annotation.optimization.CriticalNative
import dalvik.annotation.optimization.FastNative
import java.lang.ref.Cleaner
import java.util.concurrent.atomic.AtomicBoolean


/**
 * A function to retrieve a chunk of text at a given byte offset and point.
 *
 * The function should return `null` to indicate the end of the document.
 */
typealias ParseReadCallback = (byte: UInt, point: TSPoint) -> ByteArray

/**
 * A function that is called during parsing.
 *
 * The first argument contains the current byte offset and the second
 * argument indicates whether the parser has encountered an error.
 *
 * If the function returns `false`, parsing will halt early.
 *
 * @since 0.25.0
 */
typealias ParseProgressCallback = (currentByteOffset: UInt, hasError: Boolean) -> Boolean


/**
 * A function that logs parsing results.
 *
 * The first argument is the log type and the second argument is the message.
 */
typealias LogFunction = (type: TSParser.LogType, message: String) -> Unit

/**
 * A class that is used to produce a [syntax tree][Tree] from source code.
 *
 * @constructor Create a new instance with a certain [language], or `null` if empty.
 */
class TSParser(language: TSLanguage) : AutoCloseable {  
    /* The TSParser pointer */
    private val self: Long = init()
    
    private val cleaner: Cleaner.Cleanable?
    private val deleted = AtomicBoolean(false)
     
    /**
     * The language that the parser will use for parsing.
     *
     * Parsing cannot be performed while the language is `null`.
     */
    var language: TSLanguage? = null
        @FastNative external set
        
    init {       
        this.language = language       
        cleaner = TSRegister(this, CleanAction(self))
    }
    
    /**
     * The ranges of text that the parser will include when parsing.
     *
     * By default, the parser will always include entire documents.
     * Setting this property allows you to parse only a _portion_ of a
     * document but still return a syntax tree whose ranges match up with
     * the document as a whole. You can also pass multiple disjoint ranges.
     *
     * @throws [IllegalArgumentException] If the ranges overlap or are not in ascending order.
     */
    @set:Throws(IllegalArgumentException::class)
    @set:JvmName("setIncludedRanges")
    var includedRanges: List<TSRange> = emptyList()
        external set
    
    /**
     * The logger that the parser will use during parsing.
     *
     * #### Example
     *
     * ```
     * import android.util.Log;
     *
     * parser.logger = { type, msg ->
     *     Log.d("${type.name}", msg)
     * }
     * ```
     */
    @set:JvmName("setLogger")
    @get:Deprecated("The logger can't be called directly.", level = DeprecationLevel.HIDDEN)
    var logger: LogFunction? = null
        @FastNative external set
    
    /**
     * Parse a source code string and create a syntax tree.
     *
     * If you have already parsed an earlier version of this document and the document
     * has since been edited, pass the previous syntax tree to [oldTree] so that the
     * unchanged parts of it can be reused. This will save time and memory. For this
     * to work correctly, you must have already edited the old syntax tree using the
     * [Tree.edit] method in a way that exactly matches the source code changes.
     *
     * @throws [IllegalStateException]
     *  If the parser does not have a [language] assigned or if parsing was halted.
     */
    @Throws(IllegalStateException::class)
    fun parse(oldTree: TSTree?, source: String) = parse(
        oldTree, TSInputEncoding.UTF_16LE, source.toByteArray(Charsets.UTF_16LE)
    )
    
    @Throws(IllegalStateException::class)
    external fun parse(oldTree: TSTree?, encoding: TSInputEncoding, bytes: ByteArray): TSTree
    
    /**
     * Parse source code from a callback and create a syntax tree.
     *
     * If you have already parsed an earlier version of this document and the document
     * has since been edited, pass the previous syntax tree to [oldTree] so that the
     * unchanged parts of it can be reused. This will save time and memory. For this
     * to work correctly, you must have already edited the old syntax tree using the
     * [Tree.edit] method in a way that exactly matches the source code changes.
     *
     * @throws [IllegalStateException]
     *  If the parser does not have a [language] assigned.
     */
    @Throws(IllegalStateException::class)
    fun parse(
        oldTree: TSTree?, 
        readCallback: ParseReadCallback
    ) = parse(oldTree, TSInputEncoding.UTF_16LE, null, readCallback)
    
    @Throws(IllegalStateException::class)
    fun parse(
        oldTree: TSTree?, 
        progressCallback: ParseProgressCallback?,
        readCallback: ParseReadCallback
    ) = parse(oldTree, TSInputEncoding.UTF_16LE, progressCallback, readCallback)
    
    @Throws(IllegalStateException::class)
    external fun parse(
        oldTree: TSTree?, 
        encoding: TSInputEncoding, 
        progressCallback: ParseProgressCallback?,
        readCallback: ParseReadCallback
    ): TSTree
    
    /**
     * Instruct the parser to start the next [parse] from the beginning.
     *
     * If the parser previously failed because of a [timeout][timeoutMicros],
     * then by default, it will resume where it left off. If you don't
     * want to resume, and instead intend to use this parser to parse
     * some other document, you must call this method first.
     */
    @FastNative
    external fun reset()
    
    external fun dotGraphs(pathname: String)

    override fun toString() = "TSParser(language=$language)"

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
    
    /* Get the parser log type */
    enum class LogType { PARSE, LEX }
    
    private companion object {        
        @JvmStatic
        @CriticalNative
        private external fun init(): Long

        @JvmStatic
        @FastNative
        private external fun delete(self: Long)
    }
}

