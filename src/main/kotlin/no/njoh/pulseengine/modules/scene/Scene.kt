package no.njoh.pulseengine.modules.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.FileFormat
import no.njoh.pulseengine.data.FileFormat.*
import no.njoh.pulseengine.data.SwapList
import no.njoh.pulseengine.data.SwapList.Companion.swapListOf
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.util.forEachFast
import no.njoh.pulseengine.util.forEachFiltered
import kotlin.reflect.KClass

open class Scene(
    val name: String,
    val entities: MutableMap<String, SwapList<SceneEntity>> = mutableMapOf(),
    val systems: MutableList<SceneSystem> = mutableListOf()
) {
    @JsonIgnore
    var fileName: String = "$name.scn"

    @JsonIgnore
    var fileFormat: FileFormat = JSON

    @JsonIgnore
    val entityCollections = entities.map { it.value }.toMutableList()

    @JsonIgnore
    @PublishedApi
    internal val spatialGrid = SpatialGrid(entityCollections, 350f, 3000f, 0.2f)

    fun start(engine: PulseEngine)
    {
        systems.forEachFiltered({ it.enabled }) { it.onStart(this, engine) }
        spatialGrid.recalculate()
    }

    fun update(engine: PulseEngine)
    {
        spatialGrid.update()
        systems.forEachFiltered({ it.enabled }) { it.onUpdate(this, engine) }
    }

    fun fixedUpdate(engine: PulseEngine)
    {
        systems.forEachFiltered({ it.enabled }) { it.onFixedUpdate(this, engine) }
    }

    fun render(engine: PulseEngine)
    {
        spatialGrid.render(engine.gfx.mainSurface)
        systems.forEachFiltered({ it.enabled }) { it.onRender(this, engine) }
    }

    fun stop(engine: PulseEngine)
    {
        systems.forEachFiltered({ it.enabled }) { it.onStop(this, engine) }
    }

    fun addEntity(entity: SceneEntity)
    {
        spatialGrid.insert(entity)
        entities[entity.typeName]
            ?.add(entity)
            ?: run {
                val list = swapListOf(entity)
                entities[entity.typeName] = list
                entityCollections.add(list)
            }
    }

    fun addSystem(system: SceneSystem)
    {
        systems.add(system)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: SceneEntity> getEntitiesOfType(): Iterable<T>? =
        entities[T::class.simpleName] as Iterable<T>?

    @Suppress("UNCHECKED_CAST")
    fun <T: SceneEntity> getEntitiesOfType(type: KClass<T>): Iterable<T>? =
        entities[type.simpleName] as Iterable<T>?

    inline fun forEachEntityInArea(x: Float, y: Float, width: Float, height: Float, block: (SceneEntity) -> Unit) =
        spatialGrid.query(x, y, width, height, block)

    inline fun forEachEntity(block: (SceneEntity) -> Unit) =
        entityCollections.forEachFast { entities -> entities.forEachFast { block(it) } }
}

@Target(AnnotationTarget.CLASS)
annotation class SurfaceName(val name: String)