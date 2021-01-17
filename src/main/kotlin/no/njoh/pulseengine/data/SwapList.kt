package no.njoh.pulseengine.data

import com.fasterxml.jackson.annotation.JsonIgnore

class SwapList<T>(
    var items: Array<Any?> = Array(10) { null }
) : Iterable<T> {

    @JsonIgnore
    var size: Int = items.count { it != null }

    @JsonIgnore
    private var itemsToKeep = Array<Any?>(items.size) { null }

    @JsonIgnore
    private var keepIndex = 0

    @JsonIgnore
    private var iterator = ListIterator()

    @JsonIgnore
    fun isNotEmpty() = size > 0

    fun add(item: T)
    {
        if (size >= items.size)
            resize()
        items[size++] = item
    }

    fun keep(item: T)
    {
        items[keepIndex] = null
        itemsToKeep[keepIndex++] = item
    }

    fun swap()
    {
        val temp = items
        items = itemsToKeep
        itemsToKeep = temp
        size = keepIndex
        keepIndex = 0
    }

    fun fitToSize()
    {
        items = Array(size) { i -> items[i] }
        itemsToKeep = Array(size) { }
    }

    operator fun get(index: Int): T =
        items[index] as T

    override fun iterator(): Iterator<T> =
        iterator.also { it.index = 0 }

    fun clear()
    {
        items.fill(null)
        itemsToKeep.fill(null)
        size = 0
        keepIndex = 0
    }

    private fun resize()
    {
        val capacity = ((items.size + 2) * 1.5f).toInt()
        items = Array(capacity) { i -> if (i < items.size) items[i] else null }
        itemsToKeep = Array(capacity) { }
    }

    inner class ListIterator(
        var index: Int = 0
    ) : Iterator<T> {
        @Suppress("UNCHECKED_CAST")
        override fun next(): T = items[index++] as T
        override fun hasNext() = index < size
    }

    companion object
    {
        fun <T> fastListOf(item: T) =
            SwapList<T>().also { it.add(item) }
    }
}