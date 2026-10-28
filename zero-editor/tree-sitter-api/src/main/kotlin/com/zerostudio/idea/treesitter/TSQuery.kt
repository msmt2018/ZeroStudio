package com.zerostudio.idea.treesitter

import dalvik.annotation.optimization.CriticalNative
import dalvik.annotation.optimization.FastNative
import java.lang.ref.Cleaner
import java.util.concurrent.atomic.AtomicBoolean


/**
 * A function that is called while executing a query.
 *
 * The argument contains the current byte offset.
 *
 * If the function returns `false`, the execution will halt early.
 *
 * @since 0.25.0
 */
typealias QueryProgressCallback = (currentByteOffset: UInt) -> Boolean


/**
 * A class that represents a set of patterns which match nodes in a syntax tree.
 *
 * __NOTE:__ If you're targeting Android SDK level < 33,
 * you must `use` or [close] the instance to free up resources.
 *
 * @constructor
 *  Create a new query from a particular language and a
 *  string containing one or more S-expression patterns.
 * @throws [TSQueryError] If any error occurred while creating the query.
 */
class TSQuery @Throws(TSQueryError::class) constructor(
    private val language: TSLanguage,
    private val pattern: String
) : AutoCloseable {    
    // TSQuery pointer
    internal val self: Long = init(language.self, pattern)
    
    private val cleaner: Cleaner.Cleanable?
    private val deleted = AtomicBoolean(false)
    
    internal val predicates: List<MutableList<TSQueryPredicate>>

    private val settingList: List<MutableMap<String, String?>>

    private val assertionList: List<MutableMap<String, Pair<String?, Boolean>>>
       
    /** The number of patterns in the query. */
    @get:JvmName("getPatternCount")
    val patternCount: UInt
        get() = nativePatternCount().toUInt()

    /** The number of captures in the query. */
    @get:JvmName("getCaptureCount")
    val captureCount: UInt
        get() = nativeCaptureCount().toUInt()
    
    /**
     * The capture names used in the query.
     *
     * @since 0.25.0
     */
    val captureNames: List<String>

    /**
     * The string literals used in the query.
     *
     * @since 0.25.0
     */
    val stringValues: List<String>
    
    init {
        cleaner = TSRegister(this, CleanAction(self))
        
        predicates = List(nativePatternCount()) { mutableListOf() }
        settingList = List(nativePatternCount()) { mutableMapOf() }
        assertionList = List(nativePatternCount()) { mutableMapOf() }
        captureNames = List(nativeCaptureCount()) {
            checkNotNull(captureNameForId(it)) {
                "Failed to get capture name at index $it"
            }
        }
        stringValues = List(stringCount()) {
            checkNotNull(stringValueForId(it)) {
                "Failed to get string value at index $it"
            }
        }
        
        for (i in 0U..<patternCount) {
            val tokens = predicatesForPattern(i.toInt()) ?: continue
            val offset = startByteForPattern(i)
            val row = pattern.asSequence().withIndex()
                .takeWhile { it.index.toUInt() <= offset }
                .count { it.value == '\n' }.toUInt()
            var j = 0
            while (j < tokens.size) {
                // the steps of this predicate: from j up to its Done step (counted from j,
                // not from the first predicate of the pattern)
                var nargs = 0
                while (tokens[j + nargs].type != TSQueryPredicateStepTypeDone) ++nargs
                val t0 = tokens[j]
                if (t0.type == TSQueryPredicateStepTypeCapture) {
                    throw TSQueryError.Predicate(row, "@${captureNames[t0.value]}")
                }                  

                when (val pred = stringValues[t0.value]) {
                    "eq?", "not-eq?", "any-eq?", "any-not-eq?" -> {                        
                        if (nargs != 3) {
                            throw TSQueryError.Predicate(
                                row,
                                "#$pred expects 2 arguments, got ${nargs - 1}"
                            )
                        }
                        val t1 = tokens[j + 1]
                        if (t1.type != TSQueryPredicateStepTypeCapture) {
                            val value = stringValues[t1.value]
                            throw TSQueryError.Predicate(
                                row,
                                "first argument to #$pred must be a capture name, got \"$value\""
                            )
                        }
                        val t2 = tokens[j + 2]
                        val isPositive = pred == "eq?" || pred == "any-eq?"
                        val isAny = pred == "any-eq?" || pred == "any-not-eq?"
                        val value = if (t2.type == TSQueryPredicateStepTypeCapture) {
                            TSQueryPredicate.EqCapture(
                                pred,
                                captureNames[t1.value],
                                captureNames[t2.value],
                                isPositive,
                                isAny
                            )
                        } else {
                            TSQueryPredicate.EqString(
                                pred,
                                captureNames[t1.value],
                                stringValues[t2.value],
                                isPositive,
                                isAny
                            )
                        }
                        predicates[i] += value
                    }

                    "match?", "not-match?", "any-match?", "any-not-match?" -> {                        
                        if (nargs != 3) {
                            throw TSQueryError.Predicate(
                                row,
                                "#$pred expects 2 arguments, got ${nargs - 1}"
                            )
                        }
                        val t1 = tokens[j + 1]
                        if (t1.type != TSQueryPredicateStepTypeCapture) {
                            val value = stringValues[t1.value]
                            throw TSQueryError.Predicate(
                                row,
                                "first argument to #$pred must be a capture name, got \"$value\""
                            )
                        }
                        val t2 = tokens[j + 2]
                        if (t2.type != TSQueryPredicateStepTypeString) {
                            val value = captureNames[t1.value]
                            throw TSQueryError.Predicate(
                                row,
                                "second argument to #$pred must be a string literal, got @$value"
                            )
                        }
                        val pattern = try {
                            Regex(stringValues[t2.value])
                        } catch (cause: IllegalArgumentException) {
                            throw TSQueryError.Predicate(row, "pattern error", cause)
                        }
                        val value = TSQueryPredicate.Match(
                            pred,
                            captureNames[t1.value],
                            pattern,
                            pred == "match?" || pred == "any-match?",
                            pred == "any-match?" || pred == "any-not-match?"
                        )
                        predicates[i] += value
                    }

                    "any-of?", "not-any-of?" -> {                        
                        if (nargs < 3) {
                            throw TSQueryError.Predicate(
                                row,
                                "#$pred expects at least 2 arguments, got ${nargs - 1}"
                            )
                        }
                        val t1 = tokens[j + 1]
                        if (t1.type != TSQueryPredicateStepTypeCapture) {
                            val value = stringValues[t1.value]
                            throw TSQueryError.Predicate(
                                row,
                                "first argument to #$pred must be a capture name, got \"$value\""
                            )
                        }
                        val values = (2..<nargs).map {
                            val t = tokens[j + it]
                            if (t.type != TSQueryPredicateStepTypeString) {
                                val value = captureNames[t.value]
                                throw TSQueryError.Predicate(
                                    row,
                                    "arguments to #any-of? must be string literals, got @$value"
                                )
                            }
                            stringValues[t.value]
                        }
                        val value = TSQueryPredicate.AnyOf(
                            pred,
                            captureNames[t1.value],
                            values,
                            pred == "any-of?"
                        )
                        predicates[i] += value
                    }

                    "is?", "is-not?" -> {
                        // metadata: a shape that is not expected leaves it out, and does
                        // not fail the query (see "set!")
                        metadataArgs(tokens, j, nargs, captureNames, stringValues)?.let { (key, value) ->
                            assertionList[i][key] = Pair(value, pred == "is?")
                        }
                    }

                    "set!" -> {
                        // (#set! key), (#set! key value), and the forms of Neovim with a
                        // capture first, (#set! @capture key value), or as the value,
                        // (#set! @label url @url): metadata, which no query should fail on
                        metadataArgs(tokens, j, nargs, captureNames, stringValues)?.let { (key, value) ->
                            settingList[i][key] = value
                        }
                    }

                    else -> {
                        val args = (1..<nargs).map {
                            val t = tokens[j + it]
                            if (t.type == TSQueryPredicateStepTypeString) {
                                TSQueryPredicateArgs.Literal(stringValues[t.value])
                            } else {
                                TSQueryPredicateArgs.Capture(captureNames[t.value])
                            }
                        }
                        predicates[i] += TSQueryPredicate.Generic(pred, args)
                    }
                }

                j += nargs + 1
            }
        }        
    }
    
    /**
     * Execute the query on the given [Node].
     *
     * @since 0.25.0
     */
    @FastNative
    @JvmOverloads
    @JvmName("exec")
    operator fun invoke(
        node: TSNode, 
        progressCallback: QueryProgressCallback? = null
    ) = TSQueryCursor(this, node, progressCallback)
    
    /**
     * Get the property settings for the given pattern index.
     *
     * Properties are set using the `#set!` predicate.
     *
     * @return A map of properties with optional values.
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @JvmName("settings")
    @Throws(IndexOutOfBoundsException::class)
    fun settings(index: UInt): Map<String, String?> {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Index $index exceeds count $patternCount")
        return settingList[index]
    }

    /**
     * Get the property assertions for the given pattern index.
     *
     * Assertions are performed using the `#is?` and `#is-not?` predicates.
     *
     * @return
     *  A map of assertions, where the first item is the optional property value
     *  and the second item indicates whether the assertion was positive or negative.
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @JvmName("assertions")
    @Throws(IndexOutOfBoundsException::class)
    fun assertions(index: UInt): Map<String, Pair<String?, Boolean>> {
        if (index >= patternCount)
            throw IndexOutOfBoundsException("Index $index exceeds count $patternCount")
        return assertionList[index]
    }

    /**
     * Disable a certain pattern within a query.
     *
     * This prevents the pattern from matching and removes most of the overhead
     * associated with the pattern. Currently, there is no way to undo this.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @FastNative
    @JvmName("disablePattern")
    @Throws(IndexOutOfBoundsException::class)
    external fun disablePattern(index: UInt)

    /**
     * Disable a certain capture within a query.
     *
     * This prevents the capture from being returned in matches,
     * and also avoids most resource usage associated with recording
     * the capture. Currently, there is no way to undo this.
     */
    @FastNative
    @JvmName("disableCapture")
    external fun disableCapture(name: String)

    /**
     * Get the byte offset where the given pattern starts in the query's source.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @FastNative
    @JvmName("startByteForPattern")
    @Throws(IndexOutOfBoundsException::class)
    external fun startByteForPattern(index: UInt): UInt

    /**
     * Get the byte offset where the given pattern ends in the query's source.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     * @since 0.23.0
     */
    @FastNative
    @JvmName("endByteForPattern")
    @Throws(IndexOutOfBoundsException::class)
    external fun endByteForPattern(index: UInt): UInt

    /**
     * Check if the pattern with the given index has a single root node.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @FastNative
    @JvmName("isPatternRooted")
    @Throws(IndexOutOfBoundsException::class)
    external fun isPatternRooted(index: UInt): Boolean

    /**
     * Check if the pattern with the given index is "non-local".
     *
     * A non-local pattern has multiple root nodes and can match within a
     * repeating sequence of nodes, as specified by the grammar. Non-local
     * patterns disable certain optimizations that would otherwise be possible
     * when executing a query on a specific range of a syntax tree.
     *
     * @throws [IndexOutOfBoundsException]
     *  If the index exceeds the [pattern count][patternCount].
     */
    @FastNative
    @JvmName("isPatternNonLocal")
    @Throws(IndexOutOfBoundsException::class)
    external fun isPatternNonLocal(index: UInt): Boolean

    /**
     * Check if a pattern is guaranteed to match
     * once a given byte offset is reached.
     */
    @JvmName("isPatternGuaranteedAtStep")
    @Throws(IndexOutOfBoundsException::class)
    fun isPatternGuaranteedAtStep(offset: UInt): Boolean {
        if (offset >= pattern.length.toUInt())
            throw IndexOutOfBoundsException("Offset $offset exceeds EOF")
        return nativeIsPatternGuaranteedAtStep(offset.toInt())
    }
    

    override fun toString() = "TSQuery(language=$language, pattern=$pattern)"

    override fun close() {
        if (deleted.compareAndSet(false, true)) {
            if (cleaner != null)
                cleaner.clean()
            else 
                delete(self)
        }
    }

    /**
     * How often the capture [captureIndex] (an index into [captureNames]) occurs in the
     * pattern [patternIndex]: 0 not at all, 1 zero or one time, 2 zero or more, 3 once,
     * 4 one or more times (TSQuantifier).
     */
    fun captureQuantifier(patternIndex: UInt, captureIndex: UInt): Int =
        nativeCaptureQuantifier(patternIndex.toInt(), captureIndex.toInt())

    /** The predicates of the pattern [index], in their order. */
    fun predicatesFor(index: UInt): List<TSQueryPredicate> = predicates[index.toInt()]

    @FastNative
    private external fun nativeCaptureQuantifier(pattern: Int, capture: Int): Int

    @FastNative
    private external fun nativePatternCount(): Int

    @FastNative
    private external fun nativeCaptureCount(): Int

    @FastNative
    private external fun stringCount(): Int

    @FastNative
    private external fun captureNameForId(index: Int): String?

    @FastNative
    private external fun stringValueForId(index: Int): String?

    @FastNative
    private external fun nativeIsPatternGuaranteedAtStep(index: Int): Boolean

    private external fun predicatesForPattern(index: Int): List<IntArray>?

    @Suppress("NOTHING_TO_INLINE")
    private inline operator fun <T> List<T>.get(index: UInt) = get(index.toInt())

    /**
     * The key and the value of a metadata predicate (`#set!`, `#is?`) from its
     * [nargs] steps at [j] (the name first): the key is its first string, the value
     * what follows it, a string or a capture (as "@name"). Null if it has no key.
     */
    private fun metadataArgs(
        tokens: List<IntArray>,
        j: Int,
        nargs: Int,
        captureNames: List<String>,
        stringValues: List<String>
    ): Pair<String, String?>? {
        val args = (1..<nargs).map { tokens[j + it] }
        val keyAt = args.indexOfFirst { it.type == TSQueryPredicateStepTypeString }
        if (keyAt < 0) return null
        val key = stringValues[args[keyAt].value]
        val value = args.getOrNull(keyAt + 1)?.let { step ->
            if (step.type == TSQueryPredicateStepTypeString) stringValues[step.value]
            else "@" + captureNames[step.value]
        }
        return key to value
    }

    private inline val IntArray.value: Int
        inline get() = get(0)

    private inline val IntArray.type: Int
        inline get() = get(1)

    private class CleanAction(private val ptr: Long) : Runnable {
        override fun run() = delete(ptr)
    }

    @Suppress("ConstPropertyName")
    private companion object {
        private const val TSQueryPredicateStepTypeDone = 0

        private const val TSQueryPredicateStepTypeCapture = 1

        private const val TSQueryPredicateStepTypeString = 2

        @JvmStatic
        @Throws(TSQueryError::class)
        private external fun init(TSlanguage: Long, pattern: String): Long

        @JvmStatic
        @CriticalNative
        private external fun delete(self: Long)
    }
}
