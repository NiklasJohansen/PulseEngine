package no.njoh.pulseengine.modules.scene

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonIgnore
import gnu.trove.map.hash.TLongObjectHashMap
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.FileFormat
import no.njoh.pulseengine.data.FileFormat.*
import no.njoh.pulseengine.data.SwapList
import no.njoh.pulseengine.data.SwapList.Companion.swapListOf
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.entities.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.util.forEachFast
import no.njoh.pulseengine.util.forEachFiltered

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
    internal val spatialGrid = SpatialGrid(entities, 350f, 3000, 100_000, 100_000, 0.2f)

    internal var nextId = 0L

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
        nextId++
    }

    internal fun start(engine: PulseEngine)
    {
        systems.forEachFiltered({ it.enabled }) {
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
        systems.forEachFiltered({ it.enabled }) { it.onFixedUpdate(engine) }
    }

    internal fun render(engine: PulseEngine)
    {
        spatialGrid.render(engine.gfx.mainSurface)
        systems.forEachFiltered({ it.enabled }) { it.onRender(engine) }
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

    internal fun copy(fileName: String? = null): Scene
    {
        val copy = Scene(name, entities, systems)
        copy.fileName = fileName ?: this.fileName
        copy.fileFormat = fileFormat
        copy.nextId = nextId
        return copy
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