package no.njoh.pulseengine.core.scene

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.SpatialGrid.Companion.nextQueryId
import no.njoh.pulseengine.core.shared.primitives.HitResult
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

abstract class SceneManager
{
    /** Reference to the currently active Scene. */
    abstract val activeScene: Scene

    /** Holds the current state of the scene. */
    abstract val state: SceneState

    ///////////////////////////////////////// Scene State Operations /////////////////////////////////////////

    /**
     * Transitions the active [Scene] into the [SceneState.RUNNING] state.
     * Calls the [SceneSystem.onStart] function on all enabled systems in the [Scene].
     */
    abstract fun start()

    /**
     * Transitions the active [Scene] into the [SceneState.STOPPED] state.
     * Calls the [SceneSystem.onStop] function on all enabled systems in the [Scene].
     */
    abstract fun stop()

    /**
     * Transitions the active [Scene] into the [SceneState.PAUSED] state.
     */
    abstract fun pause()

    /**
     * Transitions the active [Scene] into the [SceneState.RUNNING] state.
     * Scene will not be restarted, but continue from the state it was paused.
     */
    abstract fun continueScene()

    /**
     * Saves the active [Scene] to disk. Uses the [Scene.fileName] and the configured Data.saveDirectory.
     * @param async When true - returns immediately and handles the save operation in a separate thread.
     */
    abstract fun save(async: Boolean = false)

    /**
     * Saves the active [Scene] to disk with the given [fileName].
     * @param fileName Name of file. If it is not an absolute path, the configured Data.saveDirectory will be used.
     * @param async When true - returns immediately and handles the save operation in a separate thread.
     * @param updateActiveScene Updates the active [Scene] with the new [fileName] if set true.
     */
    abstract fun saveAs(fileName: String, async: Boolean = false, updateActiveScene: Boolean = false)

    /**
     * Reloads the active [Scene] from disk.
     * @param fromClassPath True if the file should be loaded from classpath.
     */
    abstract fun reload(fromClassPath: Boolean = false)

    /**
     * Loads the scene with the given [fileName] from disk and makes it the new active [Scene].
     * @param fileName Name of file. If it is not an absolute path, the configured Data.saveDirectory will be used.
     */
    abstract fun loadAndSetActive(fileName: String, fromClassPath: Boolean = false)

    /**
     * Creates a new empty [Scene] with the given [fileName] and makes it the active [Scene].
     * This will stop and destroy the previous active scene.
     * @param fileName Name of file. Can be an absolute or relative path.
     */
    abstract fun createEmptyAndSetActive(fileName: String)

    /**
     * Loads the scene with the given [fileName] from disk and makes it the new active [Scene].
     * Fades the screen to black while loading, and then back into the new [Scene] when ready.
     * @param fileName Name of file. If it is not an absolute path, the configured Data.saveDirectory will be used.
     * @param fromClassPath True if the file should be loaded from classpath.
     * @param fadeTimeMs The number of milliseconds to use when fading to and from black screen.
     */
    abstract fun transitionInto(fileName: String, fromClassPath: Boolean = false, fadeTimeMs: Long = 1000L)

    /**
     * Sets the given [scene] to be the new active [Scene].
     * Will stop and destroy the previous scene.
     */
    abstract fun setActive(scene: Scene)

    ///////////////////////////////////////// Scene Entity Operations /////////////////////////////////////////

    /**
     * Adds the [SceneEntity] to the active [Scene].
     * @return the newly assigned ID of the given entity.
     */
    fun addEntity(entity: SceneEntity) =
        activeScene.insertEntity(entity)

    /**
     * Returns the [SceneEntity] with the given [id].
     */
    fun getEntity(id: Long): SceneEntity? =
        if (id != INVALID_ID) activeScene.entityIdMap[id] else null

    /**
     * Returns the [SceneEntity] with the given [id] that is of type [T].
     */
    inline fun <reified T> getEntityOfType(id: Long): T? =
        activeScene.entityIdMap[id] as? T?

    /**
     * Returns the first [SceneEntity] of type [T].
     */
    inline fun <reified T: SceneEntity> getFirstEntityOfType(): T? =
        activeScene.entityTypeMap[T::class.java]?.firstOrNull() as T?

    /**
     * Performs a ray-cast into the active [Scene] and returns a [HitResult] with the first hit [SceneEntity].
     */
    fun getFirstEntityAlongRay(x: Float, y: Float, angle: Float, rayLength: Float) : HitResult<SceneEntity>? =
        activeScene.spatialGrid.queryFirstAlongRay(x, y, angle, rayLength)

    /**
     * Performs a ray-cast into the active [Scene] and returns a [HitResult] with the first hit [SceneEntity] of type [T].
     */
    inline fun <reified T> getFirstEntityAlongRayOfType(x: Float, y: Float, angle: Float, rayLength: Float) : HitResult<T>? =
        activeScene.spatialGrid.queryFirstAlongRay(x, y, angle, rayLength)

    /**
     * Returns a list of all [SceneEntity]s with type [T].
     */
    inline fun <reified T: SceneEntity> getAllEntitiesOfType(): SceneEntityList<T>? =
        (activeScene.entityTypeMap[T::class.java] as SceneEntityList<T>?)?.takeIf { it.isNotEmpty() }

    /**
     * Returns all [SceneEntity]s in type separated lists.
     */
    fun getAllEntitiesByType(): List<SceneEntityList<SceneEntity>> = activeScene.entities

    /**
     * Calls the [action] lambda for each [SceneEntity] in the [Scene].
     */
    inline fun forEachEntity(action: (SceneEntity) -> Unit) =
        activeScene.entities.forEachFast { entities -> entities.forEachFast { action(it) } }

    /**
     * Calls the [action] lambda for each [SceneEntity] of type [T].
     */
    inline fun <reified T> forEachEntityOfType(action: (T) -> Unit) =
        activeScene.entities.forEachFast { list -> if (list.firstOrNull() is T) list.forEachFast { action(it as T) } }

    /**
     * Calls the [action] lambda for each list of [SceneEntity]s with the same type.
     */
    inline fun forEachEntityTypeList(action: (SceneEntityList<SceneEntity>) -> Unit) =
        activeScene.entities.forEachFast { if (it.isNotEmpty()) action(it) }

    /**
     * Calls the [action] lambda for each [SceneEntity] nearby the given area.
     * @param x The center x-coordinate of the area
     * @param y The center y-coordinate of the area
     */
    inline fun forEachEntityNearby(x: Float, y: Float, width: Float, height: Float, queryId: Int = nextQueryId(), action: (SceneEntity) -> Unit) =
        activeScene.spatialGrid.queryAxisAlignedArea(x, y, width, height, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] of type [T] nearby the given area.
     * @param x The center x-coordinate of the area
     * @param y The center y-coordinate of the area
     */
    inline fun <reified T> forEachEntityNearbyOfType(x: Float, y: Float, width: Float, height: Float, queryId: Int = nextQueryId(), action: (T) -> Unit) =
        activeScene.spatialGrid.queryAxisAlignedArea(x, y, width, height, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] nearby the given rotated area.
     * @param x The center x-coordinate of the area
     * @param y The center y-coordinate of the area
     * @param rotation Angle in degrees.
     */
    inline fun forEachEntityNearby(x: Float, y: Float, width: Float, height: Float, rotation: Float = 0f, queryId: Int = nextQueryId(), action: (SceneEntity) -> Unit) =
        activeScene.spatialGrid.queryArea(x, y, width, height, rotation, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] of type [T] nearby the given area.
     * @param x The center x-coordinate of the area
     * @param y The center y-coordinate of the area
     * @param rotation Angle in degrees.
     */
    inline fun <reified T> forEachEntityNearbyOfType(x: Float, y: Float, width: Float, height: Float, rotation: Float = 0f, queryId: Int = nextQueryId(), action: (T) -> Unit) =
        activeScene.spatialGrid.queryArea(x, y, width, height, rotation, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] intersecting the given ray.
     */
    inline fun forEachEntityAlongRay(x: Float, y: Float, angle: Float, rayLength: Float, rayWidth: Float, queryId: Int = nextQueryId(), action: (SceneEntity) -> Unit) =
        activeScene.spatialGrid.queryRay(x, y, angle, rayLength, rayWidth, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] intersecting the given ray.
     */
    inline fun <reified T> forEachEntityAlongRayOfType(x: Float, y: Float, angle: Float, rayLength: Float, rayWidth: Float, queryId: Int = nextQueryId(), action: (T) -> Unit) =
        activeScene.spatialGrid.queryRay(x, y, angle, rayLength, rayWidth, queryId, action)

    ///////////////////////////////////////// Scene System Operations /////////////////////////////////////////

    /**
     * Adds the [SceneSystem] to the active [Scene].
     */
    fun addSystem(system: SceneSystem) =
        activeScene.systems.add(system)

    /**
     * Removes the [SceneSystem] from the active [Scene].
     */
    fun removeSystem(system: SceneSystem) =
        activeScene.systems.remove(system)

    /**
     * Returns the first [SceneSystem] of type [T].
     */
    inline fun <reified T> getSystemOfType(): T? =
        activeScene.systems.firstOrNullFast { it is T } as? T?
}

/**
 * Internal functionality required by the engine.
 */
abstract class SceneManagerInternal : SceneManager()
{
    abstract fun init(engine: PulseEngine)
    abstract fun render()
    abstract fun update()
    abstract fun fixedUpdate()
    abstract fun cleanUp()
    abstract fun registerSystemsAndEntityClasses()
}