package com.zerostudio.idea.editor.core.buffer.model

class IntList(initialCapacity: Int = 32) {
    private var data: IntArray = IntArray(if (initialCapacity > 0) initialCapacity else 1)
    var size: Int = 0
        private set

    fun isEmpty(): Boolean = size == 0
    fun isNotEmpty(): Boolean = size > 0

    operator fun get(index: Int): Int {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index: $index, Size: $size")
        }
        return data[index]
    }

    operator fun set(index: Int, value: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index: $index, Size: $size")
        }
        data[index] = value
    }

    fun add(value: Int) {
        ensureCapacity(size + 1)
        data[size++] = value
    }

    fun addAll(other: IntList, fromIndex: Int = 0, delta: Int = 0) {
        if (fromIndex >= other.size) return
        val count = other.size - fromIndex
        ensureCapacity(size + count)
        if (delta == 0) {
            System.arraycopy(other.data, fromIndex, data, size, count)
        } else {
            for (i in 0 until count) {
                data[size + i] = other.data[fromIndex + i] + delta
            }
        }
        size += count
    }

    fun removeLast(): Int {
        if (size == 0) throw NoSuchElementException("IntList is empty")
        return data[--size]
    }

    fun last(): Int = get(size - 1)

    fun clear() {
        size = 0
    }

    fun toIntArray(): IntArray = data.copyOf(size)

    fun copy(): IntList {
        val copy = IntList(size)
        System.arraycopy(data, 0, copy.data, 0, size)
        copy.size = size
        return copy
    }

    private fun ensureCapacity(required: Int) {
        if (required <= data.size) return
        var newCap = data.size + (data.size shr 1) + 1
        if (newCap < required) newCap = required
        data = data.copyOf(newCap)
    }
}