package no.njoh.pulseengine.core.scene

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonIgnore
import gnu.trove.map.hash.TLongObjectHashMap
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.data.FileFormat
import no.njoh.pulseengine.core.data.FileFormat.*
import no.njoh.pulseengine.core.shared.primitives.SwapList
import no.njoh.pulseengine.core.shared.primitives.SwapList.Companion.swapListOf
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered

@JsonAutoDetect(fieldVisibility = ANY)
open class Scene(
    val name: String,
    val entities: MutableList<SwapList<SceneEntity>> = mutableListOf(),
    val systems: MutableList<SceneSystem> = mutableListOf()
) {
    @JsonIgnore
    val entityIdMap = createEntityIdMap(entities)

    @JsonIgnore
    val entityTypeMap = createEntityTypeMap(entities)

    @JsonIgnore
    internal var fileName: String = "$name.scn"

    @JsonIgnore
    internal var fileFormat: FileFormat = JSON

    @JsonIgnore
    @PublishedApi
    internal val spatialGrid = SpatialGrid(entities)

    internal var nextId = 0L

    /** Call onCreate function on all loaded entities when scene is created */
    init { entities.forEachFast { typeList -> typeList.forEachFast { it.onCreate() } } }

    fun insertEntity(entity: SceneEntity)
    {
        entity.id = nextId
        entityIdMap.put(nextId, entity)
        entityTypeMap[entity.typeName]
            ?.add(entity)
            ?: run {
                val list = swapListOf(entity)
                entityTypeMap[entity.typeName] = list
                entities.add(list)
            }
        spatialGrid.insert(entity)
        entity.onCreate()
        nextId++
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

        var deleteDeadEntities = true
        systems.forEachFiltered({ it.enabled })
        {
            if (!it.initialized)
                it.init(engine)
            if (it.handlesEntityDeletion())
                deleteDeadEntities = false
            it.onUpdate(engine)
        }

        if (deleteDeadEntities)
        {
            engine.scene.forEachEntityTypeList { typeList ->
                typeList.forEachFast { entity ->
                    if (entity.isSet(DEAD))
                        entityIdMap.remove(entity.id)
                    else
                        typeList.keep(entity)
                }
                typeList.swap()
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
        entities.removeIf { it.isEmpty() }
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

    private fun createEntityTypeMap(entities: MutableList<SwapList<SceneEntity>>) =
        HashMap<String, SwapList<SceneEntity>>(entities.size).also { map ->
            entities.forEachFast { list -> list.first()?.let { map[it.typeName] = list } }
        }

    private fun createEntityIdMap(entities: MutableList<SwapList<SceneEntity>>) =
        TLongObjectHashMap<SceneEntity>().also { map ->
            entities.forEachFast { typeList -> typeList.forEachFast { map.put(it.id, it) } }
        }
}

@Target(AnnotationTarget.CLASS)
annotation class SurfaceName(val name: String)