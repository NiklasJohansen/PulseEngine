package no.njoh.pulseengine.modules.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.FileFormat
import no.njoh.pulseengine.data.FileFormat.*
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.data.SceneState.*
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.graphics.GraphicsInterface
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.data.SwapList
import no.njoh.pulseengine.data.SwapList.Companion.fastListOf
import kotlin.reflect.full.findAnnotation

open class Scene(
    val name: String,
    val fileName: String = "/$name.scn",
    val fileFormat: FileFormat = JSON,
    val entityTypes: MutableMap<String, SwapList<SceneEntity>> = mutableMapOf()
) {
    @JsonIgnore
    private val entityCollections = entityTypes.map { it.value }.toMutableList()

    @JsonIgnore
    @PublishedApi
    internal val spatialIndex = SpatialIndex(entityCollections, 350f, 3000f, 0.2f)

    fun addEntity(entity: SceneEntity)
    {
        spatialIndex.insert(entity)
        entityTypes[entity.typeName]
            ?.add(entity)
            ?: run {
                val list = fastListOf(entity)
                entityTypes[entity.typeName] = list
                entityCollections.add(list)
            }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: SceneEntity> getEntitiesOfType(): List<T> =
        entityTypes[T::class.simpleName]?.let { it as List<T> } ?: emptyList()

    inline fun forEachEntityInArea(x: Float, y: Float, width: Float, height: Float, block: (SceneEntity) -> Unit) =
        spatialIndex.forEachEntityInArea(x, y, width, height, block)

    fun start()
    {
        spatialIndex.recalculate()

        var index = 0
        while (index < entityCollections.size)
        {
            for (entity in entityCollections[index++])
            {
                entity.onStart()
            }
        }
    }

    fun update(engine: PulseEngine, sceneState: SceneState)
    {
        spatialIndex.update()

        var index = 0
        while (index < entityCollections.size)
        {
            val entities = entityCollections[index++]
            for (entity in entities)
            {
                if (sceneState == RUNNING)
                    entity.onUpdate(engine)

                if (entity.isNot(DEAD))
                    entities.keep(entity)
            }
            entities.swap()
        }
    }

    fun fixedUpdate(engine: PulseEngine)
    {
        var index = 0
        while (index < entityCollections.size)
        {
            for (entity in entityCollections[index++])
            {
                entity.onFixedUpdate(engine)
            }
        }
    }

    fun render(gfx: GraphicsInterface, assets: Assets, sceneState: SceneState)
    {
        spatialIndex.render(gfx.mainSurface)
        entityTypes.forEach { (type, entities) ->
            if (entities.isNotEmpty())
            {
                var surface = gfx.mainSurface
                entities[0]::class
                    .findAnnotation<SurfaceName>()
                    ?.let {
                        surface = gfx.getSurface2D(it.name)
                    }

                entities.forEach { it.onRender(surface, assets, sceneState) }
            }
        }
    }
}

@Target(AnnotationTarget.CLASS)
annotation class SurfaceName(val name: String)