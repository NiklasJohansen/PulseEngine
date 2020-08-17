package no.njoh.pulseengine.modules.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.graphics.GraphicsInterface
import no.njoh.pulseengine.util.SpatialIndex
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.full.findAnnotation

@JsonDeserialize(using = SceneDeserializer::class)
open class Scene(
    val name: String = "scene",
    val layers: MutableList<SceneLayer>,
    val entities: MutableList<SceneEntity> = mutableListOf()
) {
    @JsonIgnore
    val layerMap: MutableMap<String, SceneLayer> = mutableMapOf()

    @JsonIgnore
    val typeMap: MutableMap<String?, MutableList<SceneEntity>> = mutableMapOf()

    @JsonIgnore
    val spatialIndex: SpatialIndex
    
    init
    {
        var xMin = Float.POSITIVE_INFINITY
        var xMax = Float.NEGATIVE_INFINITY
        var yMin = Float.POSITIVE_INFINITY
        var yMax = Float.NEGATIVE_INFINITY
        for (layer in layers)
        {
            for (entity in layer.entities)
            {
                xMax = max(entity.x, xMax)
                xMin = min(entity.x, xMin)
                yMax = max(entity.y, yMax)
                yMin = min(entity.y, yMin)

                typeMap[entity.className]
                    ?.add(entity)
                    ?: run { typeMap[entity.className] = mutableListOf(entity) }
            }
            layerMap[layer.name] = layer
            entities.addAll(layer.entities)
        }

        if (layers.isNotEmpty() && layers.first().entities.isNotEmpty())
        {
            val width = (xMax - xMin) + 1000
            val height =  (yMax - yMin) + 1000
            val xCenter = (xMin + xMax) / 2f
            val yCenter = (yMin + yMax) / 2f

            spatialIndex = SpatialIndex(xCenter - width / 2f, yCenter - height / 2f, width, height, 350f)

            for (layer in layers)
                for (entity in layer.entities)
                    spatialIndex.insert(entity)
        }
        else spatialIndex = SpatialIndex(2500f, 2500f, 5000f, 5000f, 250f)
    }

    fun addLayer(layer: SceneLayer)
    {
        layerMap[layer.name] = layer
        layer.entities.forEach {
            typeMap[it.className]?.add(it) ?: run { typeMap[it.className] = mutableListOf(it) }
        }
    }

    fun addEntities(layerName: String, entities: List<SceneEntity>)
    {
        layerMap[layerName]?.let { layer ->
            for (entity in entities)
            {
                layer.addEntity(entity)
                typeMap[entity.className]
                    ?.add(entity)
                    ?: run { typeMap[entity.className] = mutableListOf(entity) }
            }
        }
    }

    fun addEntity(layerName: String, entity: SceneEntity)
    {
        layerMap[layerName]
            ?.let { layer ->
                layer.addEntity(entity)
                typeMap[entity.className]
                    ?.add(entity)
                    ?: run { typeMap[entity.className] = mutableListOf(entity) }
            }
    }

    fun removeEntity(layerName: String, entity: SceneEntity)
    {
        layerMap[layerName]?.let { layer ->
            layer.entities.remove(entity)
            typeMap[entity.className]?.remove(entity)
        }
    }

    fun removeEntities(layerName: String, entities: List<SceneEntity>)
    {
        layerMap[layerName]?.let { layer ->
            layer.entities.removeAll(entities)
            for (entity in entities)
                typeMap[entity.className]?.remove(entity)
        }
    }

    fun replaceEntity(layerName: String, oldEntity: SceneEntity, newEntity: SceneEntity)
    {
        layerMap[layerName]?.entities?.let {
            val index = it.indexOf(oldEntity)
            if (index >= 0 && index < it.size)
                it[index] = newEntity
            else
                it.add(newEntity)
        }
        if (oldEntity.className != newEntity.className)
        {
            removeEntity(layerName, oldEntity)
            addEntity(layerName, newEntity)
        }
        else
        {
            typeMap[oldEntity.className]?.let {
                val index = it.indexOf(oldEntity)
                if (index >= 0 && index < it.size)
                    it[index] = newEntity
                else
                    it.add(newEntity)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: SceneEntity> getEntitiesOfType(): List<T> =
        typeMap[T::class.simpleName]?.let { it as List<T> } ?: emptyList()

    fun start()
    {
        for (layer in layers)
            layer.start()
    }

    fun update(engine: PulseEngine)
    {
        for (layer in layers)
            layer.update(engine)
    }

    fun fixedUpdate(engine: PulseEngine)
    {
        for (layer in layers)
            layer.fixedUpdate(engine)
    }

    fun render(gfx: GraphicsInterface, assets: Assets, isRunning: Boolean)
    {
        spatialIndex.render(gfx.mainSurface)

        typeMap.forEach { type, entities ->

            if (entities.isNotEmpty())
            {
                var surface = gfx.mainSurface
                entities
                    .first()::class
                    .findAnnotation<SurfaceName>()
                    ?.let {
                        surface = gfx.getSurface2D(it.name)
                    }

                entities.forEach { it.onRender(surface, assets, isRunning) }
            }
        }
    }
}

@Target(AnnotationTarget.CLASS)
annotation class SurfaceName(val name: String)


