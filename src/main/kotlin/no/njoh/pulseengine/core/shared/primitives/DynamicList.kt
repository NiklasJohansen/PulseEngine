package no.njoh.pulseengine.core.shared.primitives

import java.lang.System.arraycopy

inline fun <reified T> dynamicListOf() = DynamicList<T>()
inline fun <reified T> dynamicListOf(v0: T) = DynamicList<T>(arrayOf(v0))
inline fun <reified T> dynamicListOf(v0: T, v1: T) = DynamicList<T>(arrayOf(v0, v1))
inline fun <reified T> dynamicListOf(v0: T, v1: T, v2: T) = DynamicList<T>(arrayOf(v0, v1, v2))
inline fun <reified T> dynamicListOf(v0: T, v1: T, v2: T, v3: T) = DynamicList<T>(arrayOf(v0, v1, v2, v3))
inline fun <reified T> dynamicListOf(v0: T, v1: T, v2: T, v3: T, v4: T) = DynamicList<T>(arrayOf(v0, v1, v2, v3, v4))
inline fun <reified T> dynamicListOf(vararg values: T) = DynamicList<T>(arrayOf(*values))

@Suppress("UNCHECKED_CAST")
class DynamicList<T>(
    override var data: Array<Any?>,
    override var size: Int
): StaticList<T>(data, size) {

    //////////////////////////////////////////////////////////////////////// CONSTRUCT

    constructor(): this(DEFAULT_CAPACITY)

    constructor(capacity: Int): this(arrayOfNulls(capacity), size = 0)

    constructor(list: List<T>): this(list.toTypedArray(), size = list.size)

    constructor(array: Array<T>): this(array as Array<Any?>, size = array.size)

    //////////////////////////////////////////////////////////////////////// ADD

    operator fun plusAssign(element: T)
    {
        val s = size
        if (s == data.size)
            grow(s + 1)
        data[s] = element
        size = s + 1
    }

    operator fun plusAssign(newElements: List<T>)
    {
        val numNew = newElements.size
        if (numNew == 0)
            return
        var data = data
        val s = size
        if (numNew > data.size - s)
            data = grow(s + numNew)
        listCopy(newElements, 0, data, s, numNew)
        size = s + numNew
        return
    }

    operator fun plusAssign(newElements: DynamicList<T>)
    {
        val numNew = newElements.size
        if (numNew == 0)
            return
        var data = data
        val s = size
        if (numNew > data.size - s)
            data = grow(s + numNew)
        arraycopy(newElements.data, 0, data, s, numNew)
        size = s + numNew
    }

    fun addAt(index: Int, element: T)
    {
        val s = size
        var data = data
        if (s == data.size)
            data = grow(s + 1)
        arraycopy(data, index, data, index + 1, s - index)
        data[index] = element
        size = s + 1
    }

    //////////////////////////////////////////////////////////////////////// SET

    operator fun set(index: Int, element: T)
    {
        data[index] = element
    }

    //////////////////////////////////////////////////////////////////////// REMOVE

    operator fun minusAssign(element: T)
    {
        val index = indexOf(element)
        if (index == lastIndex) removeLastOrNull() else if (index >= 0) removeAtOrNull(index)
    }

    operator fun minusAssign(elements: List<T>)
    {
        removeIf { elements.contains(it) }
    }

    operator fun minusAssign(elements: DynamicList<T>)
    {
        removeIf { elements.contains(it) }
    }

    fun removeLastOrNull(): T?
    {
        val data = data
        var s = size
        if (s == 0)
            return null
        val element = data[--s] as T
        data[s] = null
        size = s
        return element
    }

    fun removeFirstOrNull(): T?
    {
        return removeAtOrNull(0)
    }

    fun removeAtOrNull(index: Int): T?
    {
        val s = size
        if (index >= s || index < 0)
            return null
        val data = data
        val oldValue = data[index]
        val newSize = s - 1
        if (newSize > index)
            arraycopy(data, index + 1, data, index, newSize - index)
        data[newSize] = null
        size = newSize
        return oldValue as T
    }

    inline fun removeIf(predicate: (T) -> Boolean): Boolean
    {
        // Find first element to remove
        var headIndex = 0
        var s = size
        val data = data
        while (true)
        {
            if (headIndex == s)
                return false
            if (predicate(data[headIndex] as T))
                break
            headIndex++
        }

        // Skip elements to remove and copy the rest
        var tailIndex = headIndex
        while (++headIndex < s)
        {
            val value = data[headIndex] as T
            if (!predicate(value))
                data[tailIndex++] = value
        }

        // Null out the rest of the list
        while (s > tailIndex) data[--s] = null

        this.size = s
        return true
    }

    fun clear()
    {
        val s = size
        val data = data
        for (i in 0 until s)
            data[i] = null
        size = 0
    }

    //////////////////////////////////////////////////////////////////////// COPY & GROW

    private fun listCopy(src: List<T>, srcPos: Int, dest: Array<Any?>, destPos: Int, length: Int)
    {
        for (i in 0 until length) dest[destPos + i] = src[srcPos + i]
    }

    private fun grow(minCapacity: Int): Array<Any?>
    {
        val oldCapacity = data.size
        val newCapacity = Math.max(Math.max(oldCapacity * 2, minCapacity), DEFAULT_CAPACITY)
        val newData = arrayOfNulls<Any?>(newCapacity)
        if (size > 0)
            arraycopy(data, 0, newData, 0, size)
        data = newData
        return newData
    }

    companion object
    {
        private const val DEFAULT_CAPACITY = 10
    }
}