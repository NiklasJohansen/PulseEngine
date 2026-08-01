package no.njoh.pulseengine.core.scene

import gnu.trove.set.hash.TLongHashSet
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineGame
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SELECTED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.core.scene.SceneState.*
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.ReflectionUtil
import no.njoh.pulseengine.core.shared.utils.ReflectionUtil.forEachClassWithSupertype
import no.njoh.pulseengine.core.shared.utils.ReflectionUtil.findPropertyAnnotation
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.system.measureNanoTime

open class SceneManagerImpl : SceneManagerInternal()
{
    override lateinit var activeScene: Scene
    override var state: SceneState = STOPPED

    private lateinit var engine: PulseEngine

    private val loadedScenes = ConcurrentHashMap<String, Scene>()
    private val sceneKeys = ConcurrentHashMap<String, String>()

    private var nextStagedScene: Scene? = null
    private var nextSceneFileName: String? = null

    private var loadingScene = false
    private var transitionFade = 0f
    private var transitionTimeMs = 0L
    private var onSceneLoaded: ((PulseEngine) -> Unit)? = null
    private var onTransitionFinished: ((PulseEngine) -> Unit)? = null
    private var onRender: ((PulseEngine, Surface, Float) -> Unit)? = null

    override fun init(engine: PulseEngine, game: PulseEngineGame)
    {
        Logger.info { "Initializing scene (SceneManagerImpl)" }

        this.engine = engine
        this.activeScene = Scene("default")
        this.activeScene.fileName = "default.scn"
        registerSystemsAndEntityClasses(gameBasePackage = game::class.java.packageName.substringBefore("."))
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

    override fun loadAndSetActive(fileName: String)
    {
        load(fileName)?.let { setActive(it) }
    }

    override fun load(fileName: String): Scene?
    {
        if (fileName.isBlank())
        {
            Logger.error { "Cannot load scene: fileName is not set!" }
            return null
        }

        val key = sceneKey(fileName)
        loadedScenes[key]?.let { return it }

        val scene = engine.data.loadObject<Scene>(fileName) ?: return null
        scene.fileName = fileName

        // Return the scene that was already loaded if another thread loaded it in the meantime
        return loadedScenes.putIfAbsent(key, scene) ?: scene
    }

    override fun loadAsync(fileName: String, onFail: () -> Unit, onComplete: (Scene) -> Unit)
    {
        if (fileName.isBlank())
        {
            Logger.error { "Cannot load scene: fileName is not set!" }
            onFail()
            return
        }

        val key = sceneKey(fileName)
        loadedScenes[key]?.let() 
        {
            onComplete(it)
            return
        }

        engine.data.loadObjectAsync<Scene>(fileName, onFail)
        {
            it.fileName = fileName
            // Return the scene that was already loaded if another thread loaded it in the meantime
            onComplete(loadedScenes.putIfAbsent(key, it) ?: it)
        }
    }

    override fun get(fileName: String): Scene?
    {
        return if (fileName.isBlank()) null else loadedScenes[sceneKey(fileName)]
    }

    override fun unload(fileName: String)
    {
        if (fileName.isNotBlank()) loadedScenes.remove(sceneKey(fileName))
    }

    override fun transitionInto(
        fileName: String,
        transitionTimeMs: Long,
        onSceneLoaded: ((PulseEngine) -> Unit)?,
        onTransitionFinished: ((PulseEngine) -> Unit)?,
        onRender: ((PulseEngine, Surface, t: Float) -> Unit)?
    ) {
        if (fileName != nextSceneFileName)
        {
            this.nextSceneFileName = fileName
            this.transitionTimeMs = transitionTimeMs
            this.transitionFade = 1f
            this.onSceneLoaded = onSceneLoaded
            this.onTransitionFinished = onTransitionFinished
            this.onRender = onRender
        }
    }

    override fun setActive(scene: Scene, disposePrevious: Boolean)
    {
        if (scene !== activeScene)
        {
            val previousScene = activeScene
            if (state != STOPPED)
                previousScene.stop(engine)

            previousScene.destroy(engine)

            if (disposePrevious)
            {
                loadedScenes.entries.removeIf { it.value === previousScene }
                if (scene.entities !== previousScene.entities)
                    previousScene.clearAll()
            }

            if (disposePrevious)
                System.gc()

            // Missing system implementations gets deserialized to null and should be removed
            scene.systems.removeWhen { it == null }
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
        setActive(scene)
    }

    override fun save(async: Boolean)
    {
        if (activeScene.fileName.isNotBlank())
        {
            val scene = activeScene
            scene.optimizeCollections()

            if (async)
                engine.data.saveObjectAsync(scene, scene.fileName, scene.fileFormat)
            else
                engine.data.saveObject(scene, scene.fileName, scene.fileFormat)
        }
        else Logger.error { "Cannot save scene: ${activeScene.name} - fileName is not set!" }
    }

    override fun saveAs(fileName: String, async: Boolean)
    {
        val scene = activeScene
        scene.fileName = fileName
        scene.optimizeCollections()

        loadedScenes.entries.removeIf { it.value === scene }
        loadedScenes[sceneKey(fileName)] = scene

        if (async)
            engine.data.saveObjectAsync(scene, fileName, scene.fileFormat)
        else
            engine.data.saveObject(scene, fileName, scene.fileFormat)
    }

    override fun reload()
    {
        val fileName = activeScene.fileName
        unload(fileName)
        loadAndSetActive(fileName)
    }

    override fun addEntity(entity: SceneEntity): Long
    {
        val scene = activeScene
        val id = scene.insertEntity(entity)
        if (state == RUNNING)
            scene.startEntity(engine, entity)
        return id
    }

    override fun addEntitiesFrom(sourceScene: Scene, filter: SceneEntityFilter, targetParentId: Long, configure: (List<SceneEntity>) -> Unit): List<SceneEntity>? =
        addEntitiesFrom(
            sourceName = sourceScene.name,
            sourceEntities = sourceScene.getEntities(filter, includeChildren = true),
            copyingWithinActiveScene = sourceScene === activeScene,
            targetParentId = targetParentId,
            configure = configure
        )

    @Suppress("UNCHECKED_CAST")
    override fun addEntitiesFrom(sourceJson: String, targetParentId: Long, configure: (List<SceneEntity>) -> Unit): List<SceneEntity>?
    {
        val deserialized = engine.data.deserializeFromJson(sourceJson, ArrayList::class.java) ?: return null
        if (deserialized.anyMatches { it !is SceneEntity })
        {
            Logger.error { "Cannot copy entities: clipboard JSON does not contain scene entities" }
            return null
        }
        return addEntitiesFrom(
            sourceName = "JSON scene",
            sourceEntities = deserialized as List<SceneEntity>,
            copyingWithinActiveScene = false,
            targetParentId = targetParentId,
            configure = configure
        )
    }

    override fun update()
    {
        if (nextSceneFileName != null && nextStagedScene == null && !loadingScene)
        {
            loadingScene = true
            val fileName = nextSceneFileName!!
            loadAsync(
                fileName = fileName,
                onComplete = { scene ->
                    loadingScene = false
                    nextStagedScene = scene
                    scene.fileName = fileName
                    Logger.debug { "Transitioning into scene: $fileName" }
                },
                onFail = {
                    loadingScene = false
                    nextSceneFileName = null
                    Logger.error { "Failed to load scene from file: $fileName" }
                }
            )
        }

        if (nextStagedScene != null && !loadingScene && transitionFade <= 0.5)
        {
            setActive(nextStagedScene!!)
            onSceneLoaded?.invoke(engine)
            onSceneLoaded = null
            nextSceneFileName = null
            nextStagedScene = null
        }

        activeScene.update(engine)
    }

    override fun fixedUpdate()
    {
        if (transitionFade > 0)
        {
            transitionFade -= (1000f / transitionTimeMs * 0.5f) * engine.data.fixedDeltaTime
            // Don't go past 0.5 before scene is loaded
            if (loadingScene)
                transitionFade = max(transitionFade, 0.5f)
        }
        else if (onTransitionFinished != null)
        {
            onTransitionFinished?.invoke(engine)
            onTransitionFinished = null
        }

        activeScene.fixedUpdate(engine)
    }

    override fun render()
    {
        activeScene.render(engine)

        if (transitionFade > 0f)
        {
            val surface = engine.gfx.getSurface("scene_transition")
                ?: engine.gfx.createSurface("scene_transition", zOrder = -99)

            if (onRender != null)
            {
                onRender?.invoke(engine, surface, (1f - transitionFade).coerceIn(0f, 1f))
            }
            else
            {
                val fade = 0.5f * (1f + cos(PI + transitionFade * PI * 2f).toFloat())
                surface.setDrawColor(0f, 0f, 0f, fade)
                surface.drawQuad(0f, 0f, surface.config.width.toFloat(), surface.config.height.toFloat())
            }
        }
        else onRender = null
    }

    override fun registerSystemsAndEntityClasses(gameBasePackage: String)
    {
        measureNanoTime {
            SceneEntity.REGISTERED_TYPES.clear()
            SceneSystem.REGISTERED_TYPES.clear()

            val classes = ReflectionUtil.getClassesInPackages(gameBasePackage, "no.njoh.pulseengine.modules")
            classes.forEachClassWithSupertype<SceneEntity> { SceneEntity.REGISTERED_TYPES.add(it.kotlin) }
            classes.forEachClassWithSupertype<SceneSystem> { SceneSystem.REGISTERED_TYPES.add(it.kotlin) }

            SceneEntity.REGISTERED_TYPES.remove(SceneEntity::class)
            SceneSystem.REGISTERED_TYPES.remove(SceneSystem::class)
        }.let { nanoTime ->
            val entityCount = SceneEntity.REGISTERED_TYPES.size
            val systemCount = SceneSystem.REGISTERED_TYPES.size
            Logger.debug { "Registered $entityCount SceneEntity classes and $systemCount SceneSystem classes in ${"%.3f".format(nanoTime / 1_000_000f)} ms." }
        }
    }

    override fun destroy()
    {
        Logger.info { "Destroying scene (${this::class.simpleName})" }
        activeScene.stop(engine)
        loadedScenes.clear()
        sceneKeys.clear()
    }

    private fun sceneKey(fileName: String): String
    {
        sceneKeys[fileName]?.let { return it }

        val file = File(fileName)
        val resolvedFile = if (file.isAbsolute) file else File(engine.config.saveDirectory, fileName)
        val key = runCatching { resolvedFile.canonicalPath }.getOrElse { resolvedFile.absolutePath }
        return sceneKeys.putIfAbsent(fileName, key) ?: key
    }

    private fun addEntitiesFrom(
        sourceName: String,
        sourceEntities: List<SceneEntity>,
        copyingWithinActiveScene: Boolean,
        targetParentId: Long,
        configure: (List<SceneEntity>) -> Unit
    ): List<SceneEntity>? {

        if (sourceEntities.isEmpty())
            return emptyList()

        val selectedIds = TLongHashSet(sourceEntities.size)
        sourceEntities.forEachFast()
        {
            if (it.id == INVALID_ID)
            {
                Logger.warn { "Copying entity with an invalid source ID from '$sourceName'" }
            }
            else if (!selectedIds.add(it.id))
            {
                Logger.warn { "Copying entities with duplicate source ID ${it.id} from '$sourceName'" }
            }
        }

        val validTargetParentId = if (targetParentId != INVALID_ID && activeScene.entityIdMap[targetParentId] == null)
        {
            Logger.warn { "Target parent $targetParentId was not found in scene '${activeScene.name}', creating without a parent" }
            INVALID_ID
        }
        else targetParentId

        val clones = ArrayList<SceneEntity>(sourceEntities.size)
        for (entity in sourceEntities)
        {
            val clone = engine.data.copyObject(entity)
                ?: return null.also { Logger.error { "Cannot create entity ${entity.id} from '$sourceName': cloning failed" } }
            clones.add(clone)
        }

        val rootSourceIds = TLongHashSet()
        sourceEntities.forEachFast { if (it.parentId !in selectedIds) rootSourceIds.add(it.id) }

        try { configure(clones) } catch (e: Exception)
        {
            Logger.error(e) { "Cannot create entity from '$sourceName': configuration failed" }
            return null
        }

        try
        {
            val idMapping = LinkedHashMap<Long, Long>(clones.size)

            clones.forEachIndexed { index, clone ->
                val sourceEntity = sourceEntities[index]
                clone.id = INVALID_ID
                clone.parentId = INVALID_ID
                clone.childIds = null
                clone.setNot(DEAD or SELECTED or POSITION_UPDATED or ROTATION_UPDATED or SIZE_UPDATED)
                val newId = activeScene.insertEntity(clone)
                if (sourceEntity.id != INVALID_ID)
                    idMapping.putIfAbsent(sourceEntity.id, newId)
            }

            clones.forEachIndexed { index, clone ->
                val sourceEntity = sourceEntities[index]
                val parentId = when
                {
                    sourceEntity.id in rootSourceIds && targetParentId != INVALID_ID -> validTargetParentId
                    sourceEntity.id in rootSourceIds && copyingWithinActiveScene -> sourceEntity.parentId
                    sourceEntity.id in rootSourceIds -> INVALID_ID
                    sourceEntity.parentId in idMapping -> idMapping.getValue(sourceEntity.parentId)
                    else -> INVALID_ID
                }
                if (parentId != INVALID_ID) activeScene.entityIdMap[parentId]?.addChild(clone)
                remapEntityReferences(clone, idMapping, preserveExternalReferences = copyingWithinActiveScene)
            }

            if (engine.scene.state == RUNNING)
                clones.forEachFast { activeScene.startEntity(engine, it) }

            return clones
        }
        catch (e: Exception)
        {
            Logger.error(e) { "Failed to insert entities from '$sourceName'" }
            return null
        }
    }

    private fun remapEntityReferences(entity: SceneEntity, idMapping: Map<Long, Long>, preserveExternalReferences: Boolean)
    {
        val properties = entityReferenceProperties.getOrPut(entity.javaClass)
        {
            entity::class.memberProperties
                .filterIsInstance<KMutableProperty1<Any, Any?>>()
                .filter { it.name != SceneEntity::parentId.name }
                .filter { entity::class.findPropertyAnnotation<EntityRef>(it.name) != null }
        }

        for (property in properties)
        {
            when (val value = property.get(entity))
            {
                is Long      -> property.set(entity, remapReference(value, idMapping, preserveExternalReferences))
                is LongArray -> property.set(entity, LongArray(value.size) { remapReference(value[it], idMapping, preserveExternalReferences) })
                null         -> Unit
                else         -> Logger.error { "@EntityRef property ${entity::class.simpleName}.${property.name} must be Long or LongArray" }
            }
        }
    }

    private fun remapReference(sourceId: Long, idMapping: Map<Long, Long>, preserveExternalReferences: Boolean): Long
    {
        if (sourceId == INVALID_ID) return INVALID_ID
        return idMapping[sourceId] ?: if (preserveExternalReferences) sourceId else INVALID_ID
    }

    companion object
    {
        private val entityReferenceProperties = ConcurrentHashMap<Class<out SceneEntity>, List<KMutableProperty1<Any, Any?>>>()
    }
}
