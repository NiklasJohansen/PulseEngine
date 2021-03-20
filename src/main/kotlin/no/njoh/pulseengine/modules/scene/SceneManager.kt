package no.njoh.pulseengine.modules.scene

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.data.SceneState.*
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.Data
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.util.Logger
import kotlin.math.PI
import kotlin.math.cos

interface SceneManager
{
    fun start()
    fun stop()
    fun pause()
    fun save(async: Boolean = false)
    fun saveIf(async: Boolean = false, predicate: (SceneManager) -> Boolean)
    fun reload(fromClassPath: Boolean = false)
    fun loadAndSetActive(fileName: String, fromClassPath: Boolean = false)
    fun createEmptyAndSetActive(fileName: String)
    fun transitionInto(fileName: String, fromClassPath: Boolean = false, fadeTimeMs: Long = 1000L)
    fun setActive(scene: Scene)
    val activeScene: Scene
    val state: SceneState
}

interface SceneManagerEngineInterface : SceneManager
{
    fun init(assets: Assets, data: Data)
    fun render(engine: PulseEngine)
    fun update(engine: PulseEngine)
    fun fixedUpdate(engine: PulseEngine)
}

class SceneManagerImpl : SceneManagerEngineInterface {

    override lateinit var activeScene: Scene
    override var state: SceneState = STOPPED

    private lateinit var assets: Assets
    private lateinit var data: Data
    private lateinit var fadeSurface: Surface2D

    private var nextStagedScene: Scene? = null
    private var nextSceneFileName: String? = null
    private var nextSceneFromClassPath = false

    private var transitionFade = 0f
    private var fadeTimeMs = 0L
    private var loadingScene = false

    override fun init(assets: Assets, data: Data)
    {
        this.assets = assets
        this.data = data
        this.activeScene = Scene("default")
        this.activeScene.fileName = "default.scn"
        SceneEntity.autoRegisterEntityTypes()
    }

    override fun start()
    {
        when (state)
        {
            STOPPED -> {
                state = RUNNING
                activeScene.start()
            }
            PAUSED -> state = RUNNING
            RUNNING -> {  }
        }
    }

    override fun stop()
    {
        state = STOPPED
    }

    override fun pause()
    {
        state = PAUSED
    }

    override fun loadAndSetActive(fileName: String, fromClassPath: Boolean)
    {
        if (fileName.isNotBlank())
        {
            data.loadObject<Scene>(fileName, fromClassPath)?.let {
                it.fileName = fileName
                setActive(it)
            }
        }
        else Logger.error("Cannot load scene: ${activeScene.name} - fileName to is not set!")
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
            if (scene.entities != activeScene.entities)
            {
                activeScene.entities.forEach { it.value.clear() }
                activeScene.entities.clear()
                System.gc()
            }

            activeScene = scene
            if (state != STOPPED)
                activeScene.start()
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
        setActive(scene)
    }

    override fun save(async: Boolean)
    {
        if (activeScene.fileName.isNotBlank())
        {
            activeScene.entities.values.removeIf { it.isEmpty() }
            activeScene.entities.values.forEach { it.fitToSize() }
            activeScene.entityCollections.removeIf { it.isEmpty() }
            if (async)
                data.saveObjectAsync(activeScene, activeScene.fileName, activeScene.fileFormat)
            else
                data.saveObject(activeScene, activeScene.fileName, activeScene.fileFormat)
        }
        else Logger.error("Cannot save scene: ${activeScene.name} - fileName is not set!")
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

    override fun update(engine: PulseEngine)
    {
        if (nextSceneFileName != null && nextStagedScene == null && !loadingScene)
        {
            loadingScene = true
            data.loadObjectAsync<Scene>(nextSceneFileName!!, nextSceneFromClassPath, {
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

    override fun fixedUpdate(engine: PulseEngine)
    {
        if (transitionFade > 0)
        {
            transitionFade -= (1000f / fadeTimeMs / 2f) * engine.data.fixedDeltaTime
            if (loadingScene)
                transitionFade = transitionFade.coerceAtLeast(0.5f)
        }

        if (state == RUNNING)
            activeScene.fixedUpdate(engine)
    }

    override fun render(engine: PulseEngine)
    {
        activeScene.render(engine)

        if (transitionFade >= 0)
        {
            if (!this::fadeSurface.isInitialized)
                fadeSurface = engine.gfx.createSurface2D("sceneFadeSurface", zOrder = 99)

            val fade = (cos(transitionFade * PI * 2f + PI).toFloat() + 1f) / 2f
            fadeSurface.setDrawColor(0f, 0f, 0f, fade)
            fadeSurface.drawQuad(0f, 0f, fadeSurface.width.toFloat(), fadeSurface.height.toFloat())
        }
    }
}

