package no.njoh.pulseengine.core.scene

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneState.*
import no.njoh.pulseengine.core.scene.systems.EntityUpdater
import no.njoh.pulseengine.core.scene.systems.EntityRendererImpl
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.ReflectionUtil
import no.njoh.pulseengine.core.shared.utils.ReflectionUtil.getClassesFromFullyQualifiedClassNames
import no.njoh.pulseengine.core.shared.utils.ReflectionUtil.getClassesOfSuperType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.system.measureNanoTime

open class SceneManagerImpl : SceneManagerInternal()
{
    override lateinit var activeScene: Scene
    override var state: SceneState = STOPPED

    private lateinit var engine: PulseEngine

    private var nextStagedScene: Scene? = null
    private var nextSceneFileName: String? = null
    private var nextSceneFromClassPath = false

    private var transitionFade = 0f
    private var fadeTimeMs = 0L
    private var loadingScene = false

    override fun init(engine: PulseEngine)
    {
        Logger.info("Initializing scene (${this::class.simpleName})")

        this.engine = engine
        this.activeScene = Scene("default")
        this.activeScene.fileName = "default.scn"
        addSystem(EntityUpdater())
        addSystem(EntityRendererImpl())
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

    override fun continueScene()
    {
        state = RUNNING
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
        if (scene !== activeScene)
        {
            if (state != STOPPED)
                activeScene.stop(engine)

            activeScene.destroy(engine)

            if (scene.entities !== activeScene.entities)
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
        scene.systems.add(EntityUpdater())
        scene.systems.add(EntityRendererImpl())
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

    override fun saveAs(fileName: String, async: Boolean, updateActiveScene: Boolean)
    {
        activeScene.optimizeCollections()

        if (updateActiveScene)
            activeScene.fileName = fileName

        if (async)
            engine.data.saveObjectAsync(activeScene, fileName, activeScene.fileFormat)
        else
            engine.data.saveObject(activeScene, fileName, activeScene.fileFormat)
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
            transitionFade -= (1000f / fadeTimeMs * 0.5f) * engine.data.fixedDeltaTime
            // Don't go past 0.5 before scene is loaded
            if (loadingScene)
                transitionFade = max(transitionFade, 0.5f)
        }

        activeScene.fixedUpdate(engine)
    }

    override fun render()
    {
        activeScene.render(engine)

        if (transitionFade > 0f)
        {
            val fade = (cos(transitionFade * PI * 2f + PI).toFloat() + 1f) / 2f
            val surface = engine.gfx.getSurface("scene_transition")
                ?: engine.gfx.createSurface("scene_transition", zOrder = -99)

            surface.setDrawColor(0f, 0f, 0f, fade)
            surface.drawQuad(0f, 0f, surface.width.toFloat(), surface.height.toFloat())
        }
    }

    override fun registerSystemsAndEntityClasses()
    {
        measureNanoTime {
            SceneEntity.REGISTERED_TYPES.clear()
            SceneSystem.REGISTERED_TYPES.clear()

            val classes = ReflectionUtil.getFullyQualifiedClassNames()
                .getClassesFromFullyQualifiedClassNames()

            classes.getClassesOfSuperType(SceneEntity::class).forEach { SceneEntity.REGISTERED_TYPES.add(it.kotlin) }
            classes.getClassesOfSuperType(SceneSystem::class).forEach { SceneSystem.REGISTERED_TYPES.add(it.kotlin) }

            SceneEntity.REGISTERED_TYPES.remove(SceneEntity::class)
            SceneSystem.REGISTERED_TYPES.remove(SceneSystem::class)
        }.let { nanoTime ->
            val entityCount = SceneEntity.REGISTERED_TYPES.size
            val systemCount = SceneSystem.REGISTERED_TYPES.size
            Logger.debug("Registered $entityCount SceneEntity classes and $systemCount SceneSystem " +
                "classes in ${"%.3f".format(nanoTime / 1_000_000f)} ms. ")
        }
    }

    override fun cleanUp()
    {
        Logger.info("Cleaning up scene (${this::class.simpleName})")
        activeScene.stop(engine)
    }
}