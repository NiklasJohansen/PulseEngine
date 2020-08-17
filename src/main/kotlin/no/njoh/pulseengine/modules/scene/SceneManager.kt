package no.njoh.pulseengine.modules.scene

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.DataInterface
import no.njoh.pulseengine.modules.graphics.GraphicsInterface
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.util.Logger
import kotlin.math.PI
import kotlin.math.cos

interface SceneManager
{
    fun start()
    fun stop()
    fun save()
    fun saveAsync()
    fun loadAndSetActive(fileName: String)
    fun createEmptyAndSetActive(fileName: String)
    fun transitionInto(fileName: String, fadeTimeMs: Long = 1000L)
    fun setActive(fileName: String, scene: Scene)
    val activeScene: Scene?
    val isRunning: Boolean
}

interface SceneManagerEngineInterface : SceneManager
{
    fun init(assets: Assets, data: DataInterface)
    fun render(gfx: GraphicsInterface)
    fun update(engine: PulseEngine)
    fun fixedUpdate(engine: PulseEngine)
}

class SceneManagerImpl : SceneManagerEngineInterface {

    override var activeScene: Scene? = null
    override var isRunning: Boolean = false

    private lateinit var assets: Assets
    private lateinit var data: DataInterface
    private lateinit var fadeSurface: Surface2D

    private var nextStagedScene: Scene? = null
    private var nextSceneFileName: String? = null
    private var activeSceneFileName: String? = null

    private var transitionFade = 0f
    private var fadeTimeMs = 0L
    private var loadingScene = false

    override fun init(assets: Assets, data: DataInterface)
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
            if (!isRunning)
            {
                save()
                isRunning = true
                activeScene?.start()
            }
        }
    }

    override fun stop()
    {
        if (isRunning)
            activeSceneFileName?.let { loadAndSetActive(it) }

        isRunning = false
    }

    override fun loadAndSetActive(fileName: String)
    {
        data.loadState<Scene>(fileName)?.let {
            setActive(fileName, it)
        }
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

    override fun setActive(fileName: String, scene: Scene)
    {
        activeScene = null
        System.gc()
        activeSceneFileName = fileName
        activeScene = scene

        if (isRunning)
            activeScene?.start()
    }

    override fun createEmptyAndSetActive(fileName: String)
    {
        val sceneName = fileName
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .substringBefore(".")
        val scene = Scene(sceneName, mutableListOf(SceneLayer("default_layer", mutableListOf())))
        setActive(fileName, scene)
        save()
    }

    override fun save()
    {
        activeScene?.let { scene ->
            activeSceneFileName?.let { fileName ->
                data.saveState(scene, fileName)
            }
        }
    }

    override fun saveAsync()
    {
        activeScene?.let { scene ->
            activeSceneFileName?.let { fileName ->
                data.saveStateAsync(scene, fileName)
            }
        }
    }

    override fun update(engine: PulseEngine)
    {
        if (nextSceneFileName != null && nextStagedScene == null && !loadingScene)
        {
            loadingScene = true
            data.loadStateAsync<Scene>(nextSceneFileName!!, false, {
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
            setActive(nextSceneFileName!!, nextStagedScene!!)
            nextSceneFileName = null
            nextStagedScene = null
        }

        if (isRunning)
            activeScene?.update(engine)
    }

    override fun fixedUpdate(engine: PulseEngine)
    {
        if (transitionFade > 0)
        {
            transitionFade -= (1000f / fadeTimeMs / 2f) * engine.data.fixedDeltaTime
            if (loadingScene)
                transitionFade = transitionFade.coerceAtLeast(0.5f)
        }

        if (isRunning)
            activeScene?.fixedUpdate(engine)
    }

    override fun render(gfx: GraphicsInterface)
    {
        activeScene?.render(gfx, assets, isRunning)

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

