package no.njoh.pulseengine.core.shared.primitives

import no.njoh.pulseengine.core.shared.utils.Logger

@Suppress(names = ["UNCHECKED_CAST"])
inline fun <reified T> emptyStaticList() = StaticList.EMPTY as StaticList<T>

inline fun <reified T> staticListOf(v0: T) = StaticList<T>(arrayOf(v0))
inline fun <reified T> staticListOf(v0: T, v1: T) = StaticList<T>(arrayOf(v0, v1))
inline fun <reified T> staticListOf(v0: T, v1: T, v2: T) = StaticList<T>(arrayOf(v0, v1, v2))
inline fun <reified T> staticListOf(v0: T, v1: T, v2: T, v3: T) = StaticList<T>(arrayOf(v0, v1, v2, v3))
inline fun <reified T> staticListOf(v0: T, v1: T, v2: T, v3: T, v4: T) = StaticList<T>(arrayOf(v0, v1, v2, v3, v4))
inline fun <reified T> staticListOf(vararg values: T) = StaticList<T>(arrayOf(*values))

@Suppress("UNCHECKED_CAST")
open class StaticList<T>(
    open val data: Array<Any?>,
    open val size: Int
) {
    val lastIndex; get() = size - 1

    //////////////////////////////////////////////////////////////////////// CONSTRUCT

    constructor(list: List<T>): this(list.toTypedArray(), size = list.size)

    constructor(array: Array<T>): this(array as Array<Any?>, size = array.size)

    //////////////////////////////////////////////////////////////////////// GET

    operator fun get(index: Int): T = data[index] as T

    //////////////////////////////////////////////////////////////////////// CHECK

    operator fun contains(element: T) = (indexOf(element) >= 0)

    operator fun contains(elements: List<T>): Boolean
    {
        val s = elements.size
        for (i in 0 until s)
        {
            if (indexOf(elements[i]) < 0) return false
        }
        return true
    }

    fun indexOf(element: T): Int
    {
        var i = -1
        val s = size
        val data = data
        while (++i < s)
        {
            if (data[i] == element) return i
        }
        return -1
    }

    fun lastIndexOf(element: T): Int
    {
        var i = size
        val data = data
        while (--i >= 0)
        {
            if (data[i] == element) return i
        }
        return -1
    }

    fun isEmpty() = (size == 0)

    fun isNotEmpty() = (size > 0)

    fun firstOrNull() = if (size > 0) data[0] as T else null

    fun lastOrNull() = if (size > 0) data[lastIndex] as T else null

    inline fun firstOrNull(predicate: (T) -> Boolean): T?
    {
        val s = size
        val data = data
        for (i in 0 until s)
        {
            val element = data[i] as T
            if (predicate(element)) return element
        }
        return null
    }

    inline fun lastOrNull(predicate: (T) -> Boolean): T?
    {
        var i = lastIndex
        val data = data
        while (i >= 0)
        {
            val element = data[i--] as T
            if (predicate(element)) return element
        }
        return null
    }

    //////////////////////////////////////////////////////////////////////// ITERATE

    inline fun forEach(action: (T) -> Unit)
    {
        val s = size
        val data = data
        for (i in 0 until s) action(data[i] as T)
        if (s != size)
            Logger.warn("DynamicList modified during iteration")
    }

    inline fun forEachIndexed(action: (i: Int, T) -> Unit)
    {
        val s = size
        val data = data
        for (i in 0 until s)
            action(i, data[i] as T)
        if (s != size)
            Logger.warn("DynamicList modified during iteration")
    }

    //////////////////////////////////////////////////////////////////////// TRANSFORM

    fun toList(): List<T>
    {
        val s = size
        val data = data
        val list = ArrayList<T>(s)
        for (i in 0 until s)
            list.add(data[i] as T)
        return list
    }

    //////////////////////////////////////////////////////////////////////// EQUALS, HASH CODE, TO STRING

    override fun equals(other: Any?): Boolean
    {
        if (this === other)
            return true
        if (other !is StaticList<*>)
            return false
        val s = size
        if (s != other.size)
            return false
        val data = data
        val otherData = other.data
        for (i in 0 until s)
            if (data[i] != otherData[i]) return false
        return true
    }

    override fun hashCode(): Int
    {
        val s = size
        val data = data
        var result = s
        for (i in 0 until s)
            result = 31 * result + (data[i]?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String
    {
        val s = size
        if (s == 0)
            return "[]"
        val data = data
        val sb = StringBuilder().append('[').append(data[0])
        for (i in 1 until s)
            sb.append(", ").append(data[i])
        return sb.append(']').toString()
    }

    companion object
    {
        val EMPTY = StaticList<Any?>(emptyArray(), size = 0)
    }
}