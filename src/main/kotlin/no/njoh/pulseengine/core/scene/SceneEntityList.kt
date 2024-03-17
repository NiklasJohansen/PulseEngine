package no.njoh.pulseengine.core.scene

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD

class SceneEntityList(
    @PublishedApi
    @JsonAlias("items")
    internal var entities: Array<SceneEntity?> = Array(10) { null }
) {
    @JsonIgnore
    var size: Int = entities.count { it != null }

    @JsonIgnore
    fun isEmpty() = (size == 0)

    @JsonIgnore
    fun isNotEmpty() = (size != 0)

    @JsonIgnore
    fun firstOrNull() = if (size > 0) entities[0] else null

    inline fun forEachFast(action: (SceneEntity) -> Unit)
    {
        val size = size
        val entities = entities
        var i = 0
        while (i < size) action(entities[i++]!!)
    }

    inline fun forEachReversed(action: (SceneEntity) -> Unit)
    {
        val entities = entities
        var i = size - 1
        while (i > -1) action(entities[i--]!!)
    }

    fun add(entity: SceneEntity)
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

    inline fun removeDeadEntities(onRemoved: (SceneEntity) -> Unit)
    {
        // Find first dead entity
        var srcIndex = 0
        val size = size
        val entities = entities
        while (true)
        {
            if (srcIndex == size)
                return
            if (entities[srcIndex]!!.isSet(DEAD))
                break
            srcIndex++
        }

        // Skip dead entities and move the rest forward to compact the list
        var dstIndex = srcIndex
        while (++srcIndex < size)
        {
            val entity = entities[srcIndex]
            if (entity!!.isSet(DEAD)) onRemoved(entity) else entities[dstIndex++] = entity
        }

        // Null out the rest of the list
        this.size = dstIndex
        while (dstIndex < size)
            entities[dstIndex++] = null
    }

    companion object
    {
        fun of(entity: SceneEntity) = SceneEntityList().also { it.add(entity) }
    }
}