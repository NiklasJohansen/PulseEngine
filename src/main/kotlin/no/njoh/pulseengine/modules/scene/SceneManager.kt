package no.njoh.pulseengine.modules.scene

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.data.SceneState.*
import no.njoh.pulseengine.data.SwapList
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.SpatialGrid.Companion.nextQueryId
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.modules.scene.systems.default.EntityUpdateSystem
import no.njoh.pulseengine.modules.scene.systems.rendering.EntityRenderSystem
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.ReflectionUtil
import no.njoh.pulseengine.util.ReflectionUtil.getClassesFromFullyQualifiedClassNames
import no.njoh.pulseengine.util.ReflectionUtil.getClassesOfSuperType
import no.njoh.pulseengine.util.firstOrNullFast
import no.njoh.pulseengine.util.forEachFast
import kotlin.math.PI
import kotlin.math.cos
import kotlin.system.measureNanoTime

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

abstract class SceneManagerEngineInterface : SceneManager()
{
    abstract fun init(engine: PulseEngine)
    abstract fun render()
    abstract fun update()
    abstract fun fixedUpdate()
    abstract fun cleanUp()
    abstract fun registerSystemsAndEntityClasses()
}

class SceneManagerImpl : SceneManagerEngineInterface() {

    override lateinit var activeScene: Scene
    override var state: SceneState = STOPPED

    private lateinit var engine: PulseEngine
    private lateinit var fadeSurface: Surface2D

    private var nextStagedScene: Scene? = null
    private var nextSceneFileName: String? = null
    private var nextSceneFromClassPath = false

    private var transitionFade = 0f
    private var fadeTimeMs = 0L
    private var loadingScene = false

    override fun init(engine: PulseEngine)
    {
        this.engine = engine
        this.activeScene = Scene("default")
        this.activeScene.fileName = "default.scn"
        addSystem(EntityUpdateSystem())
        addSystem(EntityRenderSystem())
        registerSystemsAndEntityClasses()
    }

    override fun start()
    {
        when (state)
        {
            STOPPED ->
            {
                state = RUNNING
                activeScene.start(engine)
            }
            PAUSED -> state = RUNNING
            RUNNING -> { }
        }
    }

    override fun stop()
    {
        when (state)
        {
            PAUSED, RUNNING ->
            {
                state = STOPPED
                activeScene.stop(engine)
            }
            STOPPED -> { }
        }
    }

    override fun pause()
    {
        state = PAUSED
    }

    override fun loadAndSetActive(fileName: String, fromClassPath: Boolean)
    {
        if (fileName.isNotBlank())
        {
            engine.data.loadObject<Scene>(fileName, fromClassPath)?.let {
                it.fileName = fileName
                setActive(it)
            }
        }
        else Logger.error("Cannot load scene: ${activeScene.name} - fileName is not set!")
    }

    override fun transitionInto(fileName: String, fromClassPath: Boolean, fadeTimeMs: Long)
    {
        if (fileName != nextSceneFileName)
        {
            this.transitionFade = 1f
            this.nextSceneFileName = fileName
            this.nextSceneFromClassPath = fromClassPath
            this.fadeTimeMs = fadeTimeMs
        }
    }

    override fun setActive(scene: Scene)
    {
        if (scene != activeScene)
        {
            if (state != STOPPED)
                activeScene.stop(engine)

            activeScene.destroy(engine)

            if (scene.entities != activeScene.entities)
                activeScene.clearAll()

            // Missing system implementations gets deserialized to null and should be removed
            scene.systems.removeIf { it == null }
            activeScene = scene

            if (state != STOPPED)
                activeScene.start(engine)
        }
    }

    override fun createEmptyAndSetActive(fileName: String)
    {
        val sceneName = fileName
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .substringBefore(".")
        val scene = Scene(sceneName)
        scene.fileName = fileName
        scene.systems.add(EntityUpdateSystem())
        scene.systems.add(EntityRenderSystem())
        setActive(scene)
    }

    override fun save(async: Boolean)
    {
        if (activeScene.fileName.isNotBlank())
        {
            activeScene.optimizeCollections()

            if (async)
                engine.data.saveObjectAsync(activeScene, activeScene.fileName, activeScene.fileFormat)
            else
                engine.data.saveObject(activeScene, activeScene.fileName, activeScene.fileFormat)
        }
        else Logger.error("Cannot save scene: ${activeScene.name} - fileName is not set!")
    }

    override fun saveAs(fileName: String, async: Boolean)
    {
        activeScene.optimizeCollections()
        activeScene.fileName = fileName

        if (async)
            engine.data.saveObjectAsync(activeScene, activeScene.fileName, activeScene.fileFormat)
        else
            engine.data.saveObject(activeScene, activeScene.fileName, activeScene.fileFormat)
    }

    override fun saveIf(async: Boolean, predicate: (SceneManager) -> Boolean)
    {
        if (predicate(this))
            save(async)
    }

    override fun reload(fromClassPath: Boolean)
    {
        loadAndSetActive(activeScene.fileName, fromClassPath)
    }

    override fun update()
    {
        if (nextSceneFileName != null && nextStagedScene == null && !loadingScene)
        {
            loadingScene = true
            engine.data.loadObjectAsync<Scene>(nextSceneFileName!!, nextSceneFromClassPath, {
                loadingScene = false
                nextSceneFileName = null
                Logger.error("Failed to load scene from file: $nextSceneFileName")
            }) { scene ->
                loadingScene = false
                nextStagedScene = scene
                Logger.debug("Transitioning into scene: ${scene.name}")
            }
        }

        if (nextStagedScene != null && !loadingScene && transitionFade <= 0.5)
        {
            setActive(nextStagedScene!!)
            nextSceneFileName = null
            nextStagedScene = null
        }

        activeScene.update(engine)
    }

    override fun fixedUpdate()
    {
        if (transitionFade > 0)
        {
            transitionFade -= (1000f / fadeTimeMs / 2f) * engine.data.fixedDeltaTime
            if (loadingScene)
                transitionFade = transitionFade.coerceAtLeast(0.5f)
        }

        activeScene.fixedUpdate(engine)
    }

    override fun render()
    {
        activeScene.render(engine)

        if (transitionFade >= 0)
        {
            if (!this::fadeSurface.isInitialized)
                fadeSurface = engine.gfx.createSurface("sceneFadeSurface", zOrder = -99)

            val fade = (cos(transitionFade * PI * 2f + PI).toFloat() + 1f) / 2f
            fadeSurface.setDrawColor(0f, 0f, 0f, fade)
            fadeSurface.drawQuad(0f, 0f, fadeSurface.width.toFloat(), fadeSurface.height.toFloat())
        }
    }

    override fun registerSystemsAndEntityClasses()
    {
        measureNanoTime {
            SceneEntity.REGISTERED_TYPES.clear()
            SceneSystem.REGISTERED_TYPES.clear()

            val classes = ReflectionUtil
                .getFullyQualifiedClassNames()
                .getClassesFromFullyQualifiedClassNames()

            classes.getClassesOfSuperType(SceneEntity::class).forEach { SceneEntity.REGISTERED_TYPES.add(it.kotlin) }
            classes.getClassesOfSuperType(SceneSystem::class).forEach { SceneSystem.REGISTERED_TYPES.add(it.kotlin) }

            SceneEntity.REGISTERED_TYPES.remove(SceneEntity::class)
            SceneSystem.REGISTERED_TYPES.remove(SceneSystem::class)
        }.let {
            val entityCount = SceneEntity.REGISTERED_TYPES.size
            val systemCount = SceneSystem.REGISTERED_TYPES.size
            Logger.debug("Registered $entityCount scene entity classes and $systemCount scene system classes " +
                "in ${"%.3f".format(it / 1_000_000f)} ms. ")
        }
    }

    override fun cleanUp()
    {
        activeScene.stop(engine)
    }
}