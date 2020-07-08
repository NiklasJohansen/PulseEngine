package no.njoh.pulseengine.modules.entity

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.util.Logger
import kotlin.collections.ArrayList

typealias EntityId = Int

// Exposed to game and engine
abstract class EntityManagerBase
{
    abstract fun registerSystems(vararg systems: ComponentSystem)
    abstract fun get(id: EntityId): Entity?
    abstract val count: Int
    abstract fun createWith(vararg componentTypes: ComponentType<*>): Entity?
}

// Exposed to game engine
abstract class EntityManagerEngineBase : EntityManagerBase()
{
    abstract fun fixedUpdate(engine: PulseEngine)
    abstract fun render(engine: PulseEngine)
}

class EntityManager(
    private val maxEntities: Int = 200_000
) : EntityManagerEngineBase() {
    private val logicSystems = ArrayList<ComponentSystem>()
    private val renderSystems = ArrayList<ComponentSystem>()
    private val entities = arrayOfNulls<Entity>(maxEntities)
    private val freeIndexes = IntArray(maxEntities)
    private val indexesToUpdate = IntArray(maxEntities)

    private var entitiesHead = -1
    private var freeIndexesHead = -1
    override var count: Int = 0

    override fun registerSystems(vararg systems: ComponentSystem)
    {
        logicSystems.addAll(systems.filterIsInstance<LogicSystem>())
        renderSystems.addAll(systems.filterIsInstance<RenderSystem>())

        logicSystems.forEach { initSystem(it) }
        renderSystems.forEach { initSystem(it) }

        logicSystems.sortBy { it.componentSignature }
        renderSystems.sortBy { it.componentSignature }
    }

    private fun initSystem(system: ComponentSystem)
    {
        system.updateComponentSignature()
        Logger.info("Registered ${system::class.java.simpleName} to handle entities with components [${system.componentTypes.joinToString { it.type.simpleName }}]")
    }

    override fun createWith(vararg componentTypes: ComponentType<out Component>): Entity?
    {
        if (count >= maxEntities)
            return null

        var signature = 0L
        val components = arrayOfNulls<Component>(ComponentType.count)
        for (type in componentTypes) {
            signature = signature or (1L shl type.index)
            components[type.index] = type.getInstance()
        }

        val index = if (freeIndexesHead >= 0) freeIndexes[freeIndexesHead--] else ++entitiesHead
        val entity = Entity(index, true, signature, components)

        count++
        entities[index] = entity
        return entity
    }

    override fun get(id: EntityId): Entity? = if (id > -1 && id <= entitiesHead) entities[id] else null

    override fun fixedUpdate(engine: PulseEngine)
    {
        tickSystems(engine, logicSystems)
        removeDeadEntities()
    }

    override fun render(engine: PulseEngine)
    {
        tickSystems(engine, renderSystems)
    }

    private fun tickSystems(engine: PulseEngine, systems: ArrayList<ComponentSystem>)
    {
        var lastSignature = -1L
        var entityCount = 0
        for (system in systems)
        {
            val signature = system.componentSignature
            if (signature == 0L)
            {
                // An empty entity list is passed to systems that dont require any components
                system.tick(engine, EMPTY_COLLECTION)
                continue
            }

            // Gathers all entities containing components required by the system
            if (signature != lastSignature)
            {
                lastSignature = signature
                entityCount = 0
                val last = entitiesHead
                for (i in 0 .. last)
                {
                    val entity = entities[i]
                    if (entity != null && (signature and entity.signature) == signature)
                        indexesToUpdate[entityCount++] = i
                }
            }

            // Updates the system with the gathered entities
            system.tick(engine, EntityCollection(indexesToUpdate, entities, entityCount))
        }
    }

    private fun removeDeadEntities()
    {
        val last = entitiesHead
        for (i in last downTo 0)
        {
            val entity = entities[i]
            if (entity != null && !entity.alive)
            {
                count--
                entities[i] = null
                if (i == entitiesHead)
                    entitiesHead--
                else
                    freeIndexes[++freeIndexesHead] = i
            }
        }
    }

    companion object
    {
        private val EMPTY_COLLECTION = EntityCollection(IntArray(0), emptyArray(), 0)
    }
}

class EntityCollection(
    private val entityIndexes: IntArray,
    private val entities: Array<Entity?>,
    private val count: Int
): Iterator<Entity> {
    private var index = 0
    override fun hasNext(): Boolean = index < count
    override fun next(): Entity = entities[entityIndexes[index++]]!!
}