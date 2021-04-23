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
import kotlin.reflect.KClass

@JsonAutoDetect(fieldVisibility = ANY)
open class Scene(
    val name: String,
    private val internalEntities: MutableMap<String, SwapList<SceneEntity>> = mutableMapOf(),
    private val internalSystems: MutableList<SceneSystem> = mutableListOf()
) {
    ////////// Publicly exposed properties //////////

    @JsonIgnore
    val systems: List<SceneSystem> = internalSystems

    @JsonIgnore
    val entities: Map<String, SwapList<SceneEntity>> = internalEntities

    @JsonIgnore
    val entityCollections = internalEntities.map { it.value }.toMutableList()

    ////////// Internal engine properties //////////

    @JsonIgnore
    internal var fileName: String = "$name.scn"

    @JsonIgnore
    internal var fileFormat: FileFormat = JSON

    @JsonIgnore
    @PublishedApi
    internal val spatialGrid = SpatialGrid(entityCollections, 350f, 3000, 100_000, 100_000, 0.2f)

    @JsonIgnore
    @PublishedApi
    internal val entityIdMap = TLongObjectHashMap<SceneEntity>().also { map -> forEachEntity { map.put(it.id, it) } }
    private var nextId = 0L

    @JsonIgnore
    private val systemsToRegister = internalSystems.toMutableList()

    fun addEntity(entity: SceneEntity)
    {
        entity.id = nextId
        entityIdMap.put(nextId++, entity)

        internalEntities[entity.typeName]
            ?.add(entity)
            ?: run {
                val list = swapListOf(entity)
                internalEntities[entity.typeName] = list
                entityCollections.add(list)
            }

        spatialGrid.insert(entity)
    }

    fun killEntity(entity: SceneEntity)
    {
        entity.set(DEAD)
        entityIdMap.remove(entity.id)
    }

    fun registerSystem(system: SceneSystem, callOnCreate: Boolean = true)
    {
        if (callOnCreate)
            systemsToRegister.add(system)
        else
            internalSystems.add(system)
    }

    fun unregisterSystem(system: SceneSystem)
    {
        internalSystems.remove(system)
    }

    inline fun <reified T: SceneEntity> getEntity(id: Long): T? =
        entityIdMap[id] as? T

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: SceneEntity> getEntitiesOfType(): SwapList<T>? =
        entities[T::class.simpleName] as? SwapList<T>?

    @Suppress("UNCHECKED_CAST")
    fun <T: SceneEntity> getEntitiesOfType(type: KClass<T>): SwapList<T>? =
        entities[type.simpleName] as? SwapList<T>?

    inline fun forEachEntityInArea(x: Float, y: Float, width: Float, height: Float, block: (SceneEntity) -> Unit) =
        spatialGrid.query(x, y, width, height, block)

    inline fun forEachEntity(block: (SceneEntity) -> Unit) =
        entityCollections.forEachFast { entities -> entities.forEachFast { block(it) } }

    ////////////////////////////////////// USED BY ENGINE //////////////////////////////////////////////////

    internal fun start(engine: PulseEngine)
    {
        internalSystems.forEachFiltered({ it.enabled }) { it.onStart(this, engine) }
        spatialGrid.recalculate()
    }

    internal fun stop(engine: PulseEngine)
    {
        internalSystems.forEachFiltered({ it.enabled }) { it.onStop(this, engine) }
    }

    internal fun update(engine: PulseEngine)
    {
        systemsToRegister.forEachFast { system ->
            system.onCreate(this, engine)
            if (system !in internalSystems)
                internalSystems.add(system)
        }
        systemsToRegister.clear()

        spatialGrid.update()
        internalSystems.forEachFiltered({ it.enabled }) { it.onUpdate(this, engine) }
    }

    internal fun fixedUpdate(engine: PulseEngine)
    {
        internalSystems.forEachFiltered({ it.enabled }) { it.onFixedUpdate(this, engine) }
    }

    internal fun render(engine: PulseEngine)
    {
        spatialGrid.render(engine.gfx.mainSurface)
        internalSystems.forEachFiltered({ it.enabled }) { it.onRender(this, engine) }
    }

    internal fun optimizeCollections()
    {
        internalEntities.values.removeIf { entities -> entities.isEmpty()}
        entityCollections.removeIf { it.isEmpty() }
        entities.values.forEach { it.fitToSize() }
    }

    internal fun clearAll()
    {
        entityCollections.forEachFast { it.clear() }
        entityCollections.clear()
        internalEntities.clear()
        internalSystems.clear()
        spatialGrid.clear()
    }
}

@Target(AnnotationTarget.CLASS)
annotation class SurfaceName(val name: String)