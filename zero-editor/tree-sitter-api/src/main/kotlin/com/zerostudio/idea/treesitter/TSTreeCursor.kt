package com.zerostudio.idea.treesitter

import dalvik.annotation.optimization.CriticalNative
import dalvik.annotation.optimization.FastNative
import java.lang.ref.Cleaner
import java.util.concurrent.atomic.AtomicBoolean


/**
 * A class that can be used to efficiently walk a [syntax tree][Tree].
 *
 * __NOTE:__ If you're targeting Android SDK level < 33,
 * you must `use` or [close] the instance to free up resources.
 */
class TSTreeCursor private constructor(
    private val self: Long,
    @JvmField internal val tree: TSTree
) : AutoCloseable {
    private val cleaner: Cleaner.Cleanable?
    private val deleted = AtomicBoolean(false)
    
    internal constructor(node: TSNode) : this(init(node), node.tree)

    init {
        cleaner = TSRegister(this, CleanAction(self))
    }
    
    /** The current node of the cursor. */
    val currentNode: TSNode
        @FastNative external get

    /**
     * The depth of the cursor's current node relative to the
     * original node that the cursor was constructed with.
     */
    @get:JvmName("getCurrentDepth")
    val currentDepth: UInt
        @FastNative external get

    /**
     * The field ID of the tree cursor's current node, or `0`.
     *
     * @see [Node.childByFieldId]
     * @see [Language.fieldIdForName]
     */
    @get:JvmName("getCurrentFieldId")
    val currentFieldId: UShort
        @FastNative external get

    /**
     * The field name of the tree cursor's current node, if available.
     *
     * @see [Node.childByFieldName]
     */
    val currentFieldName: String?
        @FastNative external get

    /**
     * The index of the cursor's current node out of all the descendants
     * of the original node that the cursor was constructed with.
     */
    @get:JvmName("getCurrentDescendantIndex")
    val currentDescendantIndex: UInt
        @FastNative external get

    /** Create a shallow copy of the tree cursor. */
    fun copy() = TSTreeCursor(copy(self), tree)

    /** Reset the cursor to start at a different node. */
    @FastNative
    external fun reset(node: TSNode)

    /** Reset the cursor to start at the same position as another cursor. */
    @FastNative
    external fun reset(cursor: TSTreeCursor)
    
    /**
     * Move the cursor to the first child of its current node.
     *
     * @return
     *  `true` if the cursor successfully moved,
     *  or `false` if there were no children.
     */
    @FastNative
    external fun gotoFirstChild(): Boolean

    /**
     * Move the cursor to the last child of its current node.
     *
     * @return
     *  `true` if the cursor successfully moved,
     *  or `false` if there were no children.
     */
    @FastNative
    external fun gotoLastChild(): Boolean

    /**
     * Move the cursor to the parent of its current node.
     *
     * @return
     *  `true` if the cursor successfully moved,
     *  or `false` if there was no parent node.
     */
    @FastNative
    external fun gotoParent(): Boolean

    /**
     * Move the cursor to the next sibling of its current node.
     *
     * @return
     *  `true` if the cursor successfully moved,
     *  or `false` if there was no next sibling node.
     */
    @FastNative
    external fun gotoNextSibling(): Boolean

    /**
     * Move the cursor to the previous sibling of its current node.
     *
     * This function may be slower than [gotoNextSibling] due to how node positions
     * are stored. In the worst case, this will need to iterate through all the
     * children up to the previous sibling node to recalculate its position.
     *
     * @return
     *  `true` if the cursor successfully moved,
     *  or `false` if there was no previous sibling node.
     */
    external fun gotoPreviousSibling(): Boolean

    /**
     * Move the cursor to the node that is the nth descendant of
     * the original node that the cursor was constructed with,
     * where `0` represents the original node itself.
     */
    @FastNative
    @JvmName("gotoDescendant")
    external fun gotoDescendant(index: UInt)
    
    @FastNative
    @JvmName("gotoFirstChildForByte")
    external fun gotoFirstChildForByte(byte: UInt): Long

    @FastNative
    @JvmName("gotoFirstChildForPoint")
    external fun gotoFirstChildForPoint(point: TSPoint): Long

    override fun toString() = "TSTreeCursor(tree=$tree)"

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
        @FastNative
        private external fun init(node: TSNode): Long

        @JvmStatic
        @CriticalNative
        private external fun copy(self: Long): Long

        @JvmStatic
        @CriticalNative
        private external fun delete(self: Long)
    }
}

