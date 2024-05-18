package no.njoh.pulseengine.core.scene

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD

@Suppress("UNCHECKED_CAST")
class SceneEntityList<T : SceneEntity>(
    @PublishedApi
    @JsonAlias("items")
    internal var entities: Array<SceneEntity?> = Array(10) { null }
): Iterable<T> {

    @JsonIgnore
    var size: Int = entities.count { it != null }

    @JsonIgnore
    private var iterator = ListIterator()

    @JsonIgnore
    fun isEmpty() = (size == 0)

    @JsonIgnore
    fun isNotEmpty() = (size != 0)

    @JsonIgnore
    fun firstOrNull(): T? = if (size > 0) entities[0] as T else null

    inline fun firstOrNull(predicate: (T) -> Boolean): T?
    {
        val size = size
        val entities = entities
        var i = 0
        while (i < size)
        {
            val entity = entities[i++] as T
            if (predicate(entity))
                return entity
        }
        return null
    }

    inline fun forEachFast(action: (T) -> Unit)
    {
        val size = size
        val entities = entities
        var i = 0
        while (i < size) action(entities[i++] as T)
    }

    inline fun forEachReversed(action: (T) -> Unit)
    {
        val entities = entities
        var i = size - 1
        while (i > -1) action(entities[i--] as T)
    }

    fun add(entity: T)
    {
        if (size >= entities.size)
        {
            val newCapacity = ((entities.size + 2) * 1.5f).toInt()
            entities = Array(newCapacity) { i -> if (i < entities.size) entities[i] else null }
        }
        entities[size++] = entity
    }

    fun clear()
    {
        entities.fill(null)
        size = 0
    }

    fun fitToSize()
    {
        entities = Array(size) { i -> entities[i] }
    }

    inline fun removeDeadEntities(onRemoved: (T) -> Unit)
    {
        // Find first dead entity
        var srcIndex = 0
        val size = size
        val entities = entities
        while (true)
        {
            if (srcIndex == size)
                return
            val entity = entities[srcIndex]!!
            if (entity.isSet(DEAD))
            {
                onRemoved(entity as T)
                break
            }
            srcIndex++
        }

        // Skip dead entities and move the rest forward to compact the list
        var dstIndex = srcIndex
        while (++srcIndex < size)
        {
            val entity = entities[srcIndex]!!
            if (entity.isSet(DEAD)) onRemoved(entity as T) else entities[dstIndex++] = entity
        }

        // Null out the rest of the list
        this.size = dstIndex
        while (dstIndex < size)
            entities[dstIndex++] = null
    }

    override fun iterator() = iterator.also { it.index = 0 }

    inner class ListIterator(var index: Int = 0) : Iterator<T>
    {
        override fun next(): T = entities[index++] as T
        override fun hasNext() = index < size
    }

    companion object
    {
        fun <T: SceneEntity> of(entity: T) = SceneEntityList<T>().also { it.add(entity) }
    }
}