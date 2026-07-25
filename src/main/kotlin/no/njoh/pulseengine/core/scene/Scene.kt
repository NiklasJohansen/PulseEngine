package no.njoh.pulseengine.core.scene

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonIgnore
import gnu.trove.map.hash.THashMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.hash.TLongHashSet
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.data.FileFormat
import no.njoh.pulseengine.core.data.FileFormat.*
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.SceneEntityFilter.*
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen

@JsonAutoDetect(fieldVisibility = ANY)
open class Scene(
    val name: String,
    val entities: MutableList<SceneEntityList<SceneEntity>> = mutableListOf(),
    val systems: MutableList<SceneSystem> = mutableListOf()
) {
    @JsonIgnore
    val entityIdMap = createEntityIdMap(entities)

    @JsonIgnore
    val entityTypeMap = createEntityTypeMap(entities)

    @JsonIgnore
    var fileName: String = "$name.scn"

    @JsonIgnore
    internal var fileFormat: FileFormat = JSON

    @JsonIgnore
    val spatialGrid = SpatialGrid2D(entities)

    @JsonIgnore
    internal var nextId = findNextId()

    /** Call onCreate function on all [Initiable] entities when scene is created */
    init { entities.onCreate() }

    fun insertEntity(entity: SceneEntity): Long
    {
        entity.id = nextId++
        entityIdMap.put(entity.id, entity)
        val type = entity::class.java
        entityTypeMap[type]
            ?.add(entity)
            ?: run {
                val list = SceneEntityList.of(entity)
                entityTypeMap[type] = list
                entities.add(list)
            }

        spatialGrid.insert(entity)

        if (entity.parentId != INVALID_ID)
            entityIdMap[entity.parentId]?.addChild(entity)
        
        if (entity is Initiable)
            entity.onCreate()

        return entity.id 
    }

    /**
     * Gets all entities matching the [filter].
     * When [includeChildren] is true, all children of matching entities are included.
     */
    fun getEntities(filter: SceneEntityFilter = All, includeChildren: Boolean = false): List<SceneEntity>
    {
        if (filter is All)
            return buildList { entities.forEachFast { typeList -> typeList.forEachFast { add(it) } } }
        
        val selectedEntities = ArrayList<SceneEntity>()
        val selectedIds = if (includeChildren) TLongHashSet() else null
        fun collect(entity: SceneEntity)
        {
            if (selectedIds != null && !selectedIds.add(entity.id)) return
            selectedEntities.add(entity)
            if (includeChildren) entity.childIds?.forEachFast { childId -> entityIdMap[childId]?.let(::collect) }
        }

        when (filter)
        {
            is Id       -> entityIdMap[filter.id]?.let(::collect)
            is Name     -> entities.forEachFast { typeList -> typeList.forEachFast { if (it is Named && it.name == filter.name) collect(it) } }
            is Entities -> filter.entities.forEachFast { if (entityIdMap[it.id] === it) collect(it) }
            is All      -> Unit
        }

        return selectedEntities
    }
    
    internal fun start(engine: PulseEngine)
    {
        entities.forEachFiltered({ it.firstOrNull() is Initiable })
        {
            it.forEachFast { entity -> (entity as Initiable).onStart(engine) }
        }

        systems.forEachFiltered({ it.enabled })
        {
            if (!it.initialized)
                it.init(engine)

            it.onStart(engine)
        }

        spatialGrid.recalculate()
    }

    internal fun stop(engine: PulseEngine)
    {
        systems.forEachFiltered({ it.enabled }) { it.onStop(engine) }
    }

    internal fun update(engine: PulseEngine)
    {
        spatialGrid.update()

        systems.forEachFiltered({ it.enabled || it.stateChanged })
        {
            if (!it.initialized)
                it.init(engine)

            if (it.stateChanged)
            {
                it.stateChanged = false
                it.onStateChanged(engine)
            }

            if (it.enabled)
                it.onUpdate(engine)
        }

        engine.scene.forEachEntityTypeList { entityList ->
            entityList.removeDeadEntities { deadEntity ->
                entityIdMap.remove(deadEntity.id)
                if (deadEntity.parentId != INVALID_ID)
                    entityIdMap[deadEntity.parentId]?.removeChild(deadEntity)
            }
        }
    }

    internal fun fixedUpdate(engine: PulseEngine)
    {
        systems.forEachFiltered({ it.enabled && it.initialized }) { it.onFixedUpdate(engine) }
    }

    internal fun render(engine: PulseEngine)
    {
        systems.forEachFiltered({ it.enabled && it.initialized }) { it.onRender(engine) }
        spatialGrid.render(engine)
    }

    internal fun destroy(engine: PulseEngine)
    {
        systems.forEachFiltered({ it.initialized }) 
        {
            it.onDestroy(engine)
            it.initialized = false
        }
    }

    internal fun optimizeCollections()
    {
        entityTypeMap.values.removeIf { entities -> entities.isEmpty() }
        entities.removeWhen { it.isEmpty() }
        entities.forEachFast { it.fitToSize() }
    }

    internal fun clearAll()
    {
        entities.forEachFast { it.clear() }
        entities.clear()
        entityTypeMap.clear()
        entityIdMap.clear()
        systems.clear()
        spatialGrid.clear()
    }

    private fun createEntityTypeMap(entities: MutableList<SceneEntityList<SceneEntity>>) =
        THashMap<Class<*>, SceneEntityList<SceneEntity>>(entities.size).also { map ->
            entities.forEachFast { list -> list.firstOrNull()?.let { map[it::class.java] = list } }
        }

    private fun createEntityIdMap(entities: MutableList<SceneEntityList<SceneEntity>>) =
        TLongObjectHashMap<SceneEntity>().also { map ->
            entities.forEachFast { typeList -> typeList.forEachFast { map.put(it.id, it) } }
        }

    private fun MutableList<SceneEntityList<SceneEntity>>.onCreate()
    {
        this.forEachFast()
        {
            if (it.firstOrNull() is Initiable) it.forEachFast { entity -> (entity as Initiable).onCreate() }
        }
    }

    private fun findNextId(): Long
    {
        var highestId = INVALID_ID
        entities.forEachFast { typeList -> typeList.forEachFast { if (it.id > highestId) highestId = it.id } }
        return highestId + 1L
    }
}