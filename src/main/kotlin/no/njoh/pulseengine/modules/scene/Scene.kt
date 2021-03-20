package no.njoh.pulseengine.modules.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.FileFormat
import no.njoh.pulseengine.data.FileFormat.*
import no.njoh.pulseengine.data.SwapList
import no.njoh.pulseengine.data.SwapList.Companion.swapListOf
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.default.EntityRenderSystem
import no.njoh.pulseengine.modules.scene.systems.default.EntityUpdateSystem
import no.njoh.pulseengine.modules.scene.systems.physics.PhysicsSystem
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.util.forEachFast
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

    init
    {
        systems.clear()
        addSystem(EntityUpdateSystem())
        addSystem(PhysicsSystem())
        addSystem(EntityRenderSystem())
    }

    fun start()
    {
        systems.forEachFast { it.onStart(this) }
        spatialGrid.recalculate()
    }

    fun update(engine: PulseEngine)
    {
        spatialGrid.update()
        systems.forEachFast { it.onUpdate(this, engine) }
    }

    fun fixedUpdate(engine: PulseEngine)
    {
        systems.forEachFast { it.onFixedUpdate(this, engine) }
    }

    fun render(engine: PulseEngine)
    {
        spatialGrid.render(engine.gfx.mainSurface)
        systems.forEachFast { it.onRender(this, engine) }
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
        spatialGrid.forEachEntityInArea(x, y, width, height, block)
}

@Target(AnnotationTarget.CLASS)
annotation class SurfaceName(val name: String)