package no.njoh.pulseengine.data

import com.fasterxml.jackson.annotation.JsonIgnore

class SwapList<T>(
    @PublishedApi
    internal var items: Array<Any?> = Array(10) { null }
) : Iterable<T> {

    @JsonIgnore
    var size: Int = items.count { it != null }

    @JsonIgnore
    private var itemsToKeep = Array<Any?>(items.size) { null }

    @JsonIgnore
    private var keepIndex = 0

    @JsonIgnore
    private var iterator = ListIterator()

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

    operator fun get(index: Int): T = items[index] as T

    fun isNotEmpty() = size != 0

    fun isEmpty() = size == 0

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

    @Suppress("UNCHECKED_CAST")
    inline fun forEachFast(block: (T) -> Unit) {
        var i = 0
        while (i < size) block(items[i++] as T)
    }

    override fun iterator(): Iterator<T> =
        iterator.also { it.index = 0 }

    inner class ListIterator(
        var index: Int = 0
    ) : Iterator<T> {
        @Suppress("UNCHECKED_CAST")
        override fun next(): T = items[index++] as T
        override fun hasNext() = index < size
    }

    companion object
    {
        fun <T> swapListOf(item: T) =
            SwapList<T>().also { it.add(item) }
    }
}