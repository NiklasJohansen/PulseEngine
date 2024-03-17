package no.njoh.pulseengine.core.scene

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonIgnore
import gnu.trove.map.hash.TLongObjectHashMap
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.data.FileFormat
import no.njoh.pulseengine.core.data.FileFormat.*
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen

@JsonAutoDetect(fieldVisibility = ANY)
open class Scene(
    val name: String,
    val entities: MutableList<SceneEntityList> = mutableListOf(),
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
    @PublishedApi
    internal val spatialGrid = SpatialGrid(entities)

    internal var nextId = 0L

    /** Call onCreate function on all [Initiable] entities when scene is created */
    init { entities.onCreate() }

    fun insertEntity(entity: SceneEntity): Long
    {
        entity.id = nextId
        entityIdMap.put(nextId, entity)
        entityTypeMap[entity.typeName]
            ?.add(entity)
            ?: run {
                val list = SceneEntityList.of(entity)
                entityTypeMap[entity.typeName] = list
                entities.add(list)
            }
        spatialGrid.insert(entity)
        if (entity.parentId != INVALID_ID)
            entityIdMap[entity.parentId]?.addChild(entity)
        if (entity is Initiable)
            entity.onCreate()
        nextId++
        return entity.id
    }

    internal fun start(engine: PulseEngine)
    {
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
        systems.forEachFiltered({ it.initialized }) { it.onDestroy(engine) }
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

    private fun createEntityTypeMap(entities: MutableList<SceneEntityList>) =
        HashMap<String, SceneEntityList>(entities.size).also { map ->
            entities.forEachFast { list -> list.firstOrNull()?.let { map[it.typeName] = list } }
        }

    private fun createEntityIdMap(entities: MutableList<SceneEntityList>) =
        TLongObjectHashMap<SceneEntity>().also { map ->
            entities.forEachFast { typeList -> typeList.forEachFast { map.put(it.id, it) } }
        }

    private fun MutableList<SceneEntityList>.onCreate()
    {
        this.forEachFast()
        {
            if (it.firstOrNull() is Initiable) it.forEachFast { entity -> (entity as Initiable).onCreate() }
        }
    }
}