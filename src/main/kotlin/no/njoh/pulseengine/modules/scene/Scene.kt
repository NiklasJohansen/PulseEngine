package no.njoh.pulseengine.modules.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.FileFormat
import no.njoh.pulseengine.data.FileFormat.*
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.graphics.GraphicsInterface
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.util.SpatialIndex
import kotlin.reflect.full.findAnnotation

open class Scene(
    val name: String,
    val fileName: String = "/$name.scn",
    val fileFormat: FileFormat = JSON,
    val entities: MutableList<SceneEntity> = mutableListOf()
) {
    @JsonIgnore
    @PublishedApi
    internal val typeMap: MutableMap<String, MutableList<SceneEntity>> = mutableMapOf()

    @JsonIgnore
    @PublishedApi
    internal val spatialIndex = SpatialIndex(entities, 350f, 3000f, 0.2f)

    @JsonIgnore
    private val removeList = mutableListOf<SceneEntity>()

    init
    {
        for (entity in entities)
        {
            typeMap[entity.typeName]
                ?.add(entity)
                ?: run { typeMap[entity.typeName] = mutableListOf(entity) }
        }
    }

    fun addEntities(entities: List<SceneEntity>)
    {
        this.entities.addAll(entities)
        for (entity in entities)
        {
            spatialIndex.insert(entity)
            typeMap[entity.typeName]
                ?.add(entity)
                ?: run { typeMap[entity.typeName] = mutableListOf(entity) }
        }
    }

    fun addEntity(entity: SceneEntity)
    {
        entities.add(entity)
        spatialIndex.insert(entity)
        typeMap[entity.typeName]
            ?.add(entity)
            ?: run { typeMap[entity.typeName] = mutableListOf(entity) }
    }

    fun removeEntity(entity: SceneEntity)
    {
        entities.remove(entity)
        spatialIndex.remove(entity)
        typeMap[entity.typeName]?.remove(entity)
    }

    fun removeEntities(entities: List<SceneEntity>)
    {
        this.entities.removeAll(entities)
        for (entity in entities)
        {
            spatialIndex.remove(entity)
            typeMap[entity.typeName]?.remove(entity)
        }
    }

    fun replaceEntity(oldEntity: SceneEntity, newEntity: SceneEntity)
    {
        val index = entities.indexOf(oldEntity)
        if (index >= 0 && index < entities.size)
            entities[index] = newEntity
        else
            entities.add(newEntity)

        spatialIndex.remove(oldEntity)
        spatialIndex.insert(newEntity)

        if (oldEntity.typeName != newEntity.typeName)
        {
            typeMap[oldEntity.typeName]?.remove(oldEntity)
            typeMap[newEntity.typeName]?.add(newEntity)
        }
        else
        {
            typeMap[oldEntity.typeName]?.let {
                val i = it.indexOf(oldEntity)
                if (i >= 0 && i < it.size)
                    it[i] = newEntity
                else
                    it.add(newEntity)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: SceneEntity> getEntitiesOfType(): List<T> =
        typeMap[T::class.simpleName]?.let { it as List<T> } ?: emptyList()

    inline fun forEachEntityInArea(x: Float, y: Float, width: Float, height: Float, block: (SceneEntity) -> Unit) =
        spatialIndex.forEachEntityInArea(x, y, width, height, block)

    fun start()
    {
        spatialIndex.recalculate()
        for (entity in entities)
            entity.onStart()
    }

    fun update(engine: PulseEngine)
    {
        spatialIndex.update()

        for (entity in entities)
        {
            entity.onUpdate(engine)
            if (entity.isSet(DEAD))
                removeList.add(entity)
        }

        if (removeList.isNotEmpty())
        {
            removeEntities(removeList)
            removeList.clear()
        }
    }

    fun fixedUpdate(engine: PulseEngine)
    {
        var i = 0
        while (i < entities.size)
        {
            entities[i].onFixedUpdate(engine)
            i++
        }
    }

    fun render(gfx: GraphicsInterface, assets: Assets, sceneState: SceneState)
    {
        spatialIndex.render(gfx.mainSurface)
        typeMap.forEach { (type, entities) ->

            if (entities.isNotEmpty())
            {
                var surface = gfx.mainSurface
                entities
                    .first()::class
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