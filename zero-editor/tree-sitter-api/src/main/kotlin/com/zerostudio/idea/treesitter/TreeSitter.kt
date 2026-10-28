package com.zerostudio.idea.treesitter


// NOTE: don't forget to bump these when necessary

/**
 * The latest ABI version that is supported by the current version of the library.
 *
 * The Tree-sitter library is generally backwards-compatible with languages
 * generated using older CLI versions, but is not forwards-compatible.
 */
const val LANGUAGE_VERSION: UInt = 15U

/** The earliest ABI version that is supported by the current version of the library. */
const val MIN_COMPATIBLE_LANGUAGE_VERSION: UInt = 13U

/* Get the tree sitter language symbol type */
enum class TSSymbolType {
    REGULAR,
    ANONYMOUS,
    AUXILIARY
}

/**
 * A [Node] that was captured with a certain capture [name].
 *
 * @property node The captured node.
 * @property name The name of the capture.
 */
data class TSQueryCapture internal constructor(
    @get:JvmName("node") val node: TSNode,
    @get:JvmName("name") val name: String
) {
    override fun toString() = "TSQueryCapture(name=$name, node=$node)"
}

/**
 * A match that corresponds to a certain pattern in the query.
 *
 * @property patternIndex The index of the pattern.
 * @property captures The captures contained in the pattern.
 */
class TSQueryMatch internal constructor(
    // the pattern index, with the top bit set when the predicates were decided natively
    raw: Int,
    val captures: List<TSQueryCapture>
) {
    @get:JvmName("getPatternIndex") val patternIndex: UInt = (raw and Int.MAX_VALUE).toUInt()

    /**
     * Whether the predicates of the pattern were all decided natively and hold (see
     * TSQueryCursor.setText): they are not evaluated again.
     */
    val predicatesDecided: Boolean = raw < 0

    var predicateResult: Boolean = false
    /** Get the nodes that are captured by the given [capture] name. */
    operator fun get(capture: String): List<TSNode> =
        captures.mapNotNull { if (it.name == capture) it.node else null }

    override fun toString() = "TSQueryMatch(patternIndex=$patternIndex, captures=$captures)"
}

/**
 * An argument to a [TSQueryPredicate].
 *
 * @property value The value of the argument.
 */
sealed interface TSQueryPredicateArgs {
    val value: String

    /** A capture argument (`@value`). */
    @JvmInline
    value class Capture(override val value: String) : TSQueryPredicateArgs {
        override fun toString() = "@$value"
    }

    /** A literal string argument (`"value"`). */
    @JvmInline
    value class Literal(override val value: String) : TSQueryPredicateArgs {
        override fun toString() = "\"$value\""
    }
}

/**
 * A position in a text document in terms of rows and columns.
 *
 * @property row The zero-based row of the document.
 * @property column The zero-based column of the document.
 */
/**
 * A position in a text document in terms of rows and columns.
 *
 * @property row The zero-based row of the document.
 * @property column The zero-based column of the document.
 */
data class TSPoint(
    @get:JvmName("row") val row: UInt,
    @get:JvmName("column") val column: UInt
) : Comparable<TSPoint> {
    override operator fun compareTo(other: TSPoint): Int {
        val rowDiff = row.compareTo(other.row)
        if (rowDiff != 0) return rowDiff
        return column.compareTo(other.column)
    }

    companion object {
        /** The minimum value a [Point] can have. */
        @JvmField
        val MIN = TSPoint(UInt.MIN_VALUE, UInt.MIN_VALUE)

        /** The maximum value a [Point] can have. */
        @JvmField
        val MAX = TSPoint(UInt.MAX_VALUE, UInt.MAX_VALUE)
    }
}

/** An edit to a text document. */
data class TSInputEdit(
    @get:JvmName("startByte") val startByte: UInt,
    @get:JvmName("oldEndByte") val oldEndByte: UInt,
    @get:JvmName("newEndByte") val newEndByte: UInt,
    @get:JvmName("startPoint") val startPoint: TSPoint,
    @get:JvmName("oldEndPoint") val oldEndPoint: TSPoint,
    @get:JvmName("newEndPoint") val newEndPoint: TSPoint
)

/**
 * A range of positions in a text document,
 * both in terms of bytes and of row-column points.
 *
 * @constructor
 * @throws [IllegalArgumentException]
 *  If the end point is smaller than the start point,
 *  or the end byte is smaller than the start byte.
 */
data class TSRange @Throws(IllegalArgumentException::class) constructor(
    @get:JvmName("startPoint") val startPoint: TSPoint,
    @get:JvmName("endPoint") val endPoint: TSPoint,
    @get:JvmName("startByte") val startByte: UInt,
    @get:JvmName("endByte") val endByte: UInt
) {
    init {
        require(startPoint <= endPoint) { "Invalid point range: $startPoint to $endPoint" }
        require(startByte <= endByte) { "Invalid byte range: $startByte to $endByte" }
    }
}
