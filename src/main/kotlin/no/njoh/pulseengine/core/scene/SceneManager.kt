package no.njoh.pulseengine.core.scene

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SpatialGrid.Companion.nextQueryId
import no.njoh.pulseengine.core.shared.primitives.SwapList
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

@Suppress("UNCHECKED_CAST")
abstract class SceneManager
{
    abstract val activeScene: Scene
    abstract val state: SceneState

    abstract fun start()
    abstract fun stop()
    abstract fun pause()
    abstract fun save(async: Boolean = false)
    abstract fun saveAs(fileName: String, async: Boolean = false)
    abstract fun saveIf(async: Boolean = false, predicate: (SceneManager) -> Boolean)
    abstract fun reload(fromClassPath: Boolean = false)
    abstract fun loadAndSetActive(fileName: String, fromClassPath: Boolean = false)
    abstract fun createEmptyAndSetActive(fileName: String)
    abstract fun transitionInto(fileName: String, fromClassPath: Boolean = false, fadeTimeMs: Long = 1000L)
    abstract fun setActive(scene: Scene)

    fun addSystem(system: SceneSystem) =
        activeScene.systems.add(system)

    fun removeSystem(system: SceneSystem) =
        activeScene.systems.remove(system)

    inline fun <reified T> getSystemOfType(): T? =
        activeScene.systems.firstOrNullFast { it is T } as? T?

    fun addEntity(entity: SceneEntity) =
        activeScene.insertEntity(entity)

    fun getEntity(id: Long): SceneEntity? =
        activeScene.entityIdMap[id]

    inline fun <reified T> getEntityOfType(id: Long): T? =
        activeScene.entityIdMap[id] as? T?

    inline fun <reified T: SceneEntity> getFirstEntityOfType(): T? =
        activeScene.entityTypeMap[T::class.simpleName]?.first() as? T?

    inline fun <reified T: SceneEntity> getAllEntitiesOfType(): SwapList<T>? =
        (activeScene.entityTypeMap[T::class.simpleName] as? SwapList<T>?)?.takeIf { it.isNotEmpty() }

    inline fun <reified T: SceneEntity> forEachEntityOfType(block: (T) -> Unit) =
        activeScene.entityTypeMap[T::class.simpleName]?.forEachFast { block(it as T) }

    inline fun forEachEntityTypeList(block: (SwapList<SceneEntity>) -> Unit) =
        activeScene.entities.forEachFast { if (it.isNotEmpty()) block(it) }

    inline fun <reified T> forEachNearbyEntityOfType(x: Float, y: Float, width: Float, height: Float, queryId: Int = nextQueryId(), block: (T) -> Unit) =
        activeScene.spatialGrid.queryType(x, y, width, height, queryId, block)

    inline fun forEachNearbyEntity(x: Float, y: Float, width: Float, height: Float, queryId: Int = nextQueryId(), block: (SceneEntity) -> Unit) =
        activeScene.spatialGrid.query(x, y, width, height, queryId, block)

    inline fun forEachEntity(block: (SceneEntity) -> Unit) =
        activeScene.entities.forEachFast { entities -> entities.forEachFast { block(it) } }
}

abstract class SceneManagerInternal : SceneManager()
{
    abstract fun init(engine: PulseEngine)
    abstract fun render()
    abstract fun update()
    abstract fun fixedUpdate()
    abstract fun cleanUp()
    abstract fun registerSystemsAndEntityClasses()
}