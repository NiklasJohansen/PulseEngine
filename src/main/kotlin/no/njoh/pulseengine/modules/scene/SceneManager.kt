package no.njoh.pulseengine.modules.scene

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.data.SceneState.*
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.Data
import no.njoh.pulseengine.modules.graphics.Graphics
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.util.Logger
import kotlin.math.PI
import kotlin.math.cos

interface SceneManager
{
    fun start()
    fun stop()
    fun pause()
    fun save()
    fun saveAsync()
    fun reload()
    fun loadAndSetActive(fileName: String)
    fun createEmptyAndSetActive(fileName: String)
    fun transitionInto(fileName: String, fadeTimeMs: Long = 1000L)
    fun setActive(scene: Scene)
    val activeScene: Scene?
    val sceneState: SceneState
}

interface SceneManagerEngineInterface : SceneManager
{
    fun init(assets: Assets, data: Data)
    fun render(gfx: Graphics)
    fun update(engine: PulseEngine)
    fun fixedUpdate(engine: PulseEngine)
}

class SceneManagerImpl : SceneManagerEngineInterface {

    override var activeScene: Scene? = null
    override var sceneState: SceneState = STOPPED

    private lateinit var assets: Assets
    private lateinit var data: Data
    private lateinit var fadeSurface: Surface2D

    private var nextStagedScene: Scene? = null
    private var nextSceneFileName: String? = null

    private var transitionFade = 0f
    private var fadeTimeMs = 0L
    private var loadingScene = false

    override fun init(assets: Assets, data: Data)
    {
        this.assets = assets
        this.data = data
        SceneEntity.autoRegisterEntityTypes()
    }

    override fun start()
    {
        if (activeScene == null)
            Logger.error("No active scene to start")
        else
        {
            when (sceneState)
            {
                STOPPED -> {
                    sceneState = RUNNING
                    activeScene?.start()
                }
                PAUSED -> sceneState = RUNNING
                RUNNING -> {  }
            }
        }
    }

    override fun stop()
    {
        sceneState = STOPPED
    }

    override fun pause()
    {
        sceneState = PAUSED
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

    override fun transitionInto(fileName: String, fadeTimeMs: Long)
    {
        if (fileName != nextSceneFileName)
        {
            this.transitionFade = 1f
            this.nextSceneFileName = fileName
            this.fadeTimeMs = fadeTimeMs
        }
    }

    override fun setActive(scene: Scene)
    {
        if (scene != activeScene)
        {
            activeScene = null
            System.gc()

            activeScene = scene
            if (sceneState != STOPPED)
                activeScene?.start()
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

    override fun save()
    {
        if (activeScene.fileName.isNotBlank())
        {
            activeScene.entities.values.forEach { it.fitToSize() }
            data.saveObject(activeScene, activeScene.fileName, activeScene.fileFormat)
        }
        else Logger.error("Cannot save scene: ${activeScene.name} - fileName is not set!")
    }

    override fun saveIf(predicate: (SceneManager) -> Boolean)
    {
        if (predicate(this))
            save()
    }

    override fun save()
    {
        activeScene?.let { scene ->
            scene.entityTypes.values.forEach { it.fitToSize() }
            data.saveState(scene, scene.fileName, scene.fileFormat)
        }
    }

    override fun reload()
    {
        activeScene?.let { loadAndSetActive(it.fileName) }
    }

    override fun saveAsync()
    {
        data.saveObjectAsync(activeScene, activeScene.fileName, activeScene.fileFormat)
    }

    override fun update(engine: PulseEngine)
    {
        if (nextSceneFileName != null && nextStagedScene == null && !loadingScene)
        {
            loadingScene = true
            data.loadObjectAsync<Scene>(nextSceneFileName!!, false, { // TODO: support class path loading
                loadingScene = false
                nextSceneFileName = null
                Logger.error("Failed to load scene from file: $nextSceneFileName")
            }) { scene ->
                loadingScene = false
                nextStagedScene = scene
                Logger.debug("Transitioning to scene: ${scene.name}")
            }
        }

        if (nextStagedScene != null && !loadingScene && transitionFade <= 0.5)
        {
            setActive(nextStagedScene!!)
            nextSceneFileName = null
            nextStagedScene = null
        }

        activeScene?.update(engine, sceneState)
    }

    override fun fixedUpdate(engine: PulseEngine)
    {
        if (transitionFade > 0)
        {
            transitionFade -= (1000f / fadeTimeMs / 2f) * engine.data.fixedDeltaTime
            if (loadingScene)
                transitionFade = transitionFade.coerceAtLeast(0.5f)
        }

        if (sceneState == RUNNING)
            activeScene?.fixedUpdate(engine)
    }

    override fun render(gfx: Graphics)
    {
        activeScene?.render(gfx, assets, sceneState)

        if (transitionFade >= 0)
        {
            if (!this::fadeSurface.isInitialized)
                fadeSurface = gfx.createSurface2D("sceneFadeSurface", zOrder = 99)

            val fade = (cos(transitionFade * PI * 2f + PI).toFloat() + 1f) / 2f
            fadeSurface.setDrawColor(0f, 0f, 0f, fade)
            fadeSurface.drawQuad(0f, 0f, fadeSurface.width.toFloat(), fadeSurface.height.toFloat())
        }
    }
}

