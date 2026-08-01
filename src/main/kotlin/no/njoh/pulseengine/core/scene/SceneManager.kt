package no.njoh.pulseengine.core.scene

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineGame
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.interfaces.Spatial2D
import no.njoh.pulseengine.core.scene.SpatialGrid2D.Companion.nextQueryId
import no.njoh.pulseengine.core.scene.SceneEntityFilter.All
import no.njoh.pulseengine.core.shared.primitives.Degrees
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
     * Returns a loaded [Scene] by its [fileName], or null if it has not been loaded.
     */
    abstract fun get(fileName: String): Scene?

    /**
     * Saves the active [Scene] to disk. Uses the [Scene.fileName] and the configured Data.saveDirectory.
     * @param async When true - returns immediately and handles the save operation in a separate thread.
     */
    abstract fun save(async: Boolean = false)

    /**
     * Saves the active [Scene] to disk with the given [fileName] and retains it under the new path.
     * @param fileName Name of the file. If it is not an absolute path, the configured Data.saveDirectory will be used.
     * @param async When true - returns immediately and handles the save operation in a separate thread.
     */
    abstract fun saveAs(fileName: String, async: Boolean = false)

    /**
     * Loads and retains a [Scene] without changing [activeScene]. Repeated requests for the same
     * logical path return the same retained scene instance.
     * @param fileName Absolute file path or logical resource path. Logical paths resolve from the configured
     * Data.saveDirectory first, then from packaged resources.
     */
    abstract fun load(fileName: String): Scene?

    /**
     * Loads and retains a [Scene] on an IO thread without changing [activeScene]. Repeated requests
     * for the same logical path return the same retained scene instance.
     * @param fileName Absolute file path or logical resource path. Logical paths resolve from the configured
     * Data.saveDirectory first, then from packaged resources.
     * @param onFail Called when the scene could not be loaded.
     * @param onComplete Called when the scene has been loaded.
     */
    abstract fun loadAsync(fileName: String, onFail: () -> Unit = {}, onComplete: (Scene) -> Unit)

    /**
     * Reloads the active [Scene]. Relative paths resolve from the configured save directory first,
     * then from packaged resources.
     */
    abstract fun reload()
    
    /**
     * Removes the retained reference to a scene. The scene is loaded again on the next [load] request.
     */
    abstract fun unload(fileName: String)

    /**
     * Loads the scene with the given [fileName] and makes it the new [activeScene].
     * @param fileName Absolute file path or logical resource path. Logical paths resolve from the configured
     * Data.saveDirectory first, then from packaged resources.
     */
    abstract fun loadAndSetActive(fileName: String)

    /**
     * Creates a new empty [Scene] with the given [fileName] and makes it the active [Scene].
     * This will stop and destroy the previous active scene.
     * @param fileName Name of the file. Can be an absolute or relative path.
     */
    abstract fun createEmptyAndSetActive(fileName: String)

    /**
     * Loads the scene with the given [fileName] and makes it the new active [Scene].
     * Fades the screen to black while loading, and then back into the new [Scene] when ready.
     * @param fileName Absolute file path or logical resource path. Logical paths resolve from the configured
     * Data.saveDirectory first, then from packaged resources.
     * @param transitionTimeMs The number of milliseconds to use when fading to and from black screen.
     * @param onSceneLoaded called when the scene is loaded and ready.
     * @param onTransitionFinished called when the transition is finished.
     * @param onRender called every frame during the transition. t goes from 0.0 - 1.0 during the transition.
     */
    abstract fun transitionInto(
        fileName: String,
        transitionTimeMs: Long = 1000L,
        onSceneLoaded: ((PulseEngine) -> Unit)? = null,
        onTransitionFinished: ((PulseEngine) -> Unit)? = null,
        onRender: ((PulseEngine, Surface, t: Float) -> Unit)? = null
    )

    /**
     * Sets the given [scene] to be the new active [Scene].
     * Will stop and destroy the previous scene.
     * @param disposePrevious When true - the content of the previous scene will be disposed to free up memory.
     */
    abstract fun setActive(scene: Scene, disposePrevious: Boolean = true)

    ///////////////////////////////////////// Scene Entity Operations /////////////////////////////////////////

    /**
     * Adds the [SceneEntity] to the active [Scene].
     * @return the newly assigned ID of the given entity.
     */
    abstract fun addEntity(entity: SceneEntity): Long

    /**
     * Adds copies of entities and their children matching the [filter] from [sourceScene] to [activeScene].
     * References within the selection are remapped. References outside it are preserved when
     * copying from [activeScene], and cleared when copying from an external scene.
     * When copying from [activeScene], each copied root keeps the parent of its source entity.
     * If [targetParentId] does not exist, the added root entities are left without a parent.
     * An explicit [targetParentId] takes precedence over the source parent.
     * @param configure Called with all detached entity copies before they receive new IDs, are
     * inserted into [activeScene], and receive lifecycle callbacks. Use it to customize their properties.
     */
    abstract fun addEntitiesFrom(
        sourceScene: Scene,
        filter: SceneEntityFilter = All,
        targetParentId: Long = INVALID_ID,
        configure: (List<SceneEntity>) -> Unit = {}
    ): List<SceneEntity>?

    /**
     * Adds entities from their JSON representation to the [activeScene].
     * @param configure Called with all deserialized entities before they receive new IDs, are
     * inserted into the [activeScene], and receive lifecycle callbacks. Use it to customize their properties.
     */
    abstract fun addEntitiesFrom(
        sourceJson: String,
        targetParentId: Long = INVALID_ID,
        configure: (List<SceneEntity>) -> Unit = {}
    ): List<SceneEntity>?

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
     * Returns the first [SceneEntity] of type [T] matching the predicate.
     */
    inline fun <reified T: SceneEntity> getFirstEntityOfType(predicate: (T) -> Boolean): T? =
        activeScene.entityTypeMap[T::class.java]?.firstOrNull { predicate(it as T) } as T?

    /**
     * Returns a list of all [SceneEntity]s with type [T].
     */
    inline fun <reified T: SceneEntity> getAllEntitiesOfType(): SceneEntityList<T>? =
        (activeScene.entityTypeMap[T::class.java] as SceneEntityList<T>?)?.takeIf { it.isNotEmpty() }

    /**
     * Returns all [SceneEntity]s in type-separated lists.
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
    
    ///////////////////////////////////////// 2D Spatial Scene Entity Queries /////////////////////////////////////////

    /**
     * Performs a ray-cast into the active [Scene] and returns a [HitResult] with the first hit [SceneEntity]
     * implementing the [Spatial2D] interface.
     */
    fun getFirst2DEntityAlongRay(x: Float, y: Float, angle: Float, rayLength: Float) : HitResult<SceneEntity>? =
        activeScene.spatialGrid.queryFirstAlongRay(x, y, angle, rayLength)

    /**
     * Performs a ray-cast into the active [Scene] and returns a [HitResult] with the first hit [SceneEntity] 
     * that matches the [predicate] and implements the [Spatial2D] interface.
     */
    inline fun getFirst2DEntityAlongRay(x: Float, y: Float, angle: Float, rayLength: Float, predicate: (SceneEntity) -> Boolean) : HitResult<SceneEntity>? =
        activeScene.spatialGrid.queryFirstAlongRay(x, y, angle, rayLength, predicate)

    /**
     * Performs a ray-cast into the active [Scene] and returns a [HitResult] with the first hit [SceneEntity] 
     * of type [T] that implements the [Spatial2D] interface.
     */
    inline fun <reified T> getFirst2DEntityAlongRayOfType(x: Float, y: Float, angle: Float, rayLength: Float) : HitResult<T>? =
        activeScene.spatialGrid.queryFirstAlongRay(x, y, angle, rayLength)

    /**
     * Calls the [action] lambda for each [SceneEntity] that implements the [Spatial2D] interface and is 
     * nearby the given area.
     * @param x The center x-coordinate of the area
     * @param y The center y-coordinate of the area
     */
    inline fun forEach2DEntityNearby(x: Float, y: Float, width: Float, height: Float, queryId: Int = nextQueryId(), action: (SceneEntity) -> Unit) =
        activeScene.spatialGrid.queryAxisAlignedArea(x, y, width, height, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] of type [T] that implements the [Spatial2D] interface 
     * and is nearby the given area.
     * @param x The center x-coordinate of the area
     * @param y The center y-coordinate of the area
     */
    inline fun <reified T> forEach2DEntityNearbyOfType(x: Float, y: Float, width: Float, height: Float, queryId: Int = nextQueryId(), action: (T) -> Unit) =
        activeScene.spatialGrid.queryAxisAlignedArea(x, y, width, height, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] that implements the [Spatial2D] interface and is
     * nearby the given rotated area.
     * @param x The center x-coordinate of the area
     * @param y The center y-coordinate of the area
     * @param angle Angle in degrees.
     */
    inline fun forEach2DEntityNearby(x: Float, y: Float, width: Float, height: Float, angle: Degrees = 0f, queryId: Int = nextQueryId(), action: (SceneEntity) -> Unit) =
        activeScene.spatialGrid.queryArea(x, y, width, height, angle, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] that implements the [Spatial2D] interface, is of type [T] 
     * and is nearby the given area.
     * @param x The center x-coordinate of the area
     * @param y The center y-coordinate of the area
     * @param angle Angle in degrees.
     */
    inline fun <reified T> forEach2DEntityNearbyOfType(x: Float, y: Float, width: Float, height: Float, angle: Degrees = 0f, queryId: Int = nextQueryId(), action: (T) -> Unit) =
        activeScene.spatialGrid.queryArea(x, y, width, height, angle, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] that implements the [Spatial2D] interface and that 
     * overlaps the given point.
     * @param x The x-coordinate of the point
     * @param y The y-coordinate of the point
     */
    inline fun forEach2DEntityAtPoint(x: Float, y: Float, queryId: Int = nextQueryId(), action: (SceneEntity) -> Unit) =
        activeScene.spatialGrid.queryPosition(x, y, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] of type [T] that implements the [Spatial2D] interface 
     * and is overlapping the given point.
     * @param x The x-coordinate of the point
     * @param y The y-coordinate of the point
     */
    inline fun <reified T> forEach2DEntityAtPointOfType(x: Float, y: Float, queryId: Int = nextQueryId(), action: (T) -> Unit) =
        activeScene.spatialGrid.queryPosition<T>(x, y, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] that implements the [Spatial2D] interface and is 
     * intersecting the given ray.
     */
    inline fun forEach2DEntityAlongRay(x: Float, y: Float, angle: Float, rayLength: Float, rayWidth: Float, queryId: Int = nextQueryId(), action: (SceneEntity) -> Unit) =
        activeScene.spatialGrid.queryRay(x, y, angle, rayLength, rayWidth, queryId, action)

    /**
     * Calls the [action] lambda for each [SceneEntity] that implements the [Spatial2D] interface and is 
     * intersecting the given ray.
     */
    inline fun <reified T> forEach2DEntityAlongRayOfType(x: Float, y: Float, angle: Float, rayLength: Float, rayWidth: Float, queryId: Int = nextQueryId(), action: (T) -> Unit) =
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
    abstract fun init(engine: PulseEngine, game: PulseEngineGame)
    abstract fun render()
    abstract fun update()
    abstract fun fixedUpdate()
    abstract fun destroy()
    abstract fun registerSystemsAndEntityClasses(gameBasePackage: String)
}
