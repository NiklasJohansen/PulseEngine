package no.njoh.pulseengine.modules.scene.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContextInternal
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.Scene
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntityFilter
import no.njoh.pulseengine.core.scene.SceneEntityFilter.All
import no.njoh.pulseengine.core.scene.SceneEntityFilter.Entities
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Renderable2D
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.scene.interfaces.Scalable3D
import no.njoh.pulseengine.core.scene.interfaces.Spatial2D
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.annotations.EntityNameRef
import no.njoh.pulseengine.core.shared.annotations.SceneRef
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.ResourceResolver
import no.njoh.pulseengine.core.shared.utils.Extensions.quickSort
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.scene.systems.Scene3DLightSource
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.File
import java.util.Comparator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Icon("COPY", hexColor = "#79b8d1")
@Name("Prefab")
open class Prefab : SceneEntity(), Named, Initiable, Renderable2D, Spatial2D, Spatial3D, Scene3DRenderable, Scene3DLightSource
{
    @Prop(i = -1) override var name = "Prefab"

    @Prop("Source", i = 0)
    @SceneRef
    var sceneFile = ""

    @Prop("Source", i = 1)
    @EntityNameRef("sceneFile")
    var entityName = ""

    @Prop("Transform", i=1) override var position = Vector3f(0f)
    @Prop("Transform", i=2) override var rotation = Vector3f(0f)
    @Prop("Transform", i=3) override var scale    = Vector3f(1f)

    @get:JsonIgnore @Prop(hidden = true) override var x get() = position.x; set(v) { position.x = v }
    @get:JsonIgnore @Prop(hidden = true) override var y get() = position.y; set(v) { position.y = v }
    @get:JsonIgnore @Prop(hidden = true) override var z get() = position.z; set(v) { position.z = v }

    @get:JsonIgnore @set:JsonIgnore @Prop(hidden = true) override var zRotation get() = rotation.z; set(v) { rotation.z = v }

    @get:JsonIgnore @Prop(hidden = true) override var width  
        get() = baseWidth  * abs(scale.x)
        set(v) { if (baseWidth > MIN_SIZE) scale.x = v / baseWidth }
    
    @get:JsonIgnore @Prop(hidden = true) override var height 
        get() = baseHeight * abs(scale.y)
        set(v) { if (baseHeight > MIN_SIZE) scale.y = v / baseHeight }

    @Volatile private var requestedSceneKey = ""
    @Volatile private var failedSceneKey = ""

    private var previewSource: Scene? = null
    private var previewSceneKey = ""
    private var previewEntityName = ""
    private var previewCenterX = 0f
    private var previewCenterY = 0f
    private var previewCenterZ = 0f
    private val previewEntries = ArrayList<PreviewEntry>()
    private val previewRenderables2D = ArrayList<Renderable2D>()
    private var baseWidth = DEFAULT_PREVIEW_SIZE
    private var baseHeight = DEFAULT_PREVIEW_SIZE
    private var previewFrame = Long.MIN_VALUE
    private var lastErrorSignature = ""
    private var expansionChain = emptySet<String>()
    private var cachedSceneFile = ""
    private var cachedSceneKey = ""

    init { setNot(DISCOVERABLE) }

    override fun onStart(engine: PulseEngine)
    {
        if (sceneFile.isBlank()) return

        val sceneKey = sceneKey()
        if (sceneKey in expansionChain)
        {
            reportError("Recursive scene reference detected: ${(expansionChain + sceneKey).joinToString(" -> ")}")
            return
        }

        val sourceScene = engine.scene.load(sceneFile) ?: run()
        {
            reportError("Could not load scene '$sceneFile'")
            return
        }

        val selection = resolveSelection(sourceScene) ?: return
        val childExpansionChain = expansionChain + sceneKey

        val inserted = engine.scene.addEntitiesFrom(
            sourceScene = sourceScene,
            filter = selection.filter,
            targetParentId = parentId,
            configure = { copies ->
                copies.forEachFast { copy ->
                    PrefabTransform.apply(copy, this, selection.xCenter, selection.yCenter, selection.zCenter)
                    if (copy is Prefab)
                        copy.expansionChain = childExpansionChain
                }
            }
        )

        if (inserted != null)
            set(DEAD or HIDDEN)
        else
            reportError("Could not insert entities from '$sceneFile'")
    }

    // Renderable2D
    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        if (!updatePreview(engine)) return

        previewRenderables2D.forEachFiltered({ (it as SceneEntity).isNot(HIDDEN or DEAD) })
        {
            it.onRender(engine, surface)
        }
    }

    // Scene3DRenderable
    override fun onRender(engine: PulseEngine, context: SceneRenderContext)
    {
        render3D(engine, context, id, setRenderId = true)
    }

    // Scene3DLightSource
    override fun onRenderLight(engine: PulseEngine, context: SceneRenderContext)
    {
        if (!updatePreview(engine)) return

        previewEntries.forEachFiltered({ it.preview.isNot(HIDDEN or DEAD) })
        {
            if (it.preview is Scene3DLightSource) it.preview.onRenderLight(engine, context)
        }
    }

    private fun render3D(engine: PulseEngine, context: SceneRenderContext, renderId: Long, setRenderId: Boolean)
    {
        if (!updatePreview(engine)) return

        val context = context as SceneRenderContextInternal
        val overrideRenderId = setRenderId && renderId != INVALID_ID
        if (overrideRenderId) context.pushRenderIdOverride(renderId)

        previewEntries.forEachFiltered({ it.preview.isNot(HIDDEN or DEAD) })
        {
            when (val entity = it.preview)
            {
                is Prefab -> entity.render3D(engine, context, renderId, setRenderId = false)
                is Scene3DRenderable -> entity.onRender(engine, context)
            }
        }

        if (overrideRenderId) context.popRenderIdOverride()
    }


    private fun updatePreview(engine: PulseEngine): Boolean
    {
        if (engine.scene.state != SceneState.STOPPED || sceneFile.isBlank())
            return false

        val key = sceneKey()
        val source = engine.scene.get(sceneFile)
        if (previewSource != null && (previewSource !== source || previewSceneKey != key))
        {
            clearPreview()
            requestedSceneKey = ""
        }

        if (source == null)
        {
            if (requestedSceneKey != key && failedSceneKey != key)
            {
                requestedSceneKey = key
                engine.scene.loadAsync(
                    fileName = sceneFile,
                    onComplete = { failedSceneKey = "" },
                    onFail = {
                        failedSceneKey = key
                        reportError("Could not load scene '$sceneFile'")
                    }
                )
            }
            return false
        }

        val frameNumber = engine.data.frameNumber
        val previewWasInactive = previewFrame != Long.MIN_VALUE && previewFrame < frameNumber - 1L
        if (previewSource !== source || previewSceneKey != key || previewEntityName != entityName || previewWasInactive)
        {
            rebuildPreview(engine, source, key)
            previewFrame = Long.MIN_VALUE
        }

        if (previewFrame != frameNumber)
        {
            previewEntries.forEachFast()
            {
                PrefabTransform.restore(it.source, it.preview)
                PrefabTransform.apply(it.preview, this, previewCenterX, previewCenterY, previewCenterZ)
            }
            previewRenderables2D.quickSort(BackToFrontComparator)
            previewFrame = frameNumber
        }

        return true
    }

    private fun rebuildPreview(engine: PulseEngine, source: Scene, key: String)
    {
        val selection = resolveSelection(source)
        if (selection == null)
        {
            clearPreview()
            previewSource = source
            previewSceneKey = key
            previewEntityName = entityName
            return
        }

        previewEntries.clear()
        previewRenderables2D.clear()
        val childExpansionChain = expansionChain + sceneKey()
        for (index in 0 until selection.entities.size)
        {
            val sourceEntity = selection.entities[index]
            val copy = engine.data.copyObject(sourceEntity)
            if (copy == null)
            {
                reportError("Could not copy ${sourceEntity::class.simpleName} from '${source.name}'")
                previewEntries.clear()
                previewRenderables2D.clear()
                break
            }

            copy.id = previewEntityId(id, sourceEntity.id, index)
            copy.setNot(DEAD or SELECTED or EDITABLE or DISCOVERABLE)

            if (copy is Prefab) 
                copy.expansionChain = childExpansionChain

            if (copy is Initiable) 
                copy.onCreate()
 
            previewEntries += PreviewEntry(sourceEntity, copy)

            if (copy is Renderable2D)
                previewRenderables2D += copy
        }

        previewSource = source
        previewSceneKey = key
        previewEntityName = entityName
        previewCenterX = selection.xCenter
        previewCenterY = selection.yCenter
        previewCenterZ = selection.zCenter
        baseWidth = selection.width
        baseHeight = selection.height
    }

    private fun resolveSelection(source: Scene): ResolvedSelection?
    {
        val filter: SceneEntityFilter
        val entities: List<SceneEntity>

        if (entityName.isBlank())
        {
            filter = All
            entities = source.getEntities().filter { it.isNot(HIDDEN) }
        }
        else
        {
            val matches = source.getEntities().filter { it.isNot(HIDDEN) && it is Named && it.name == entityName }
            if (matches.size != 1)
            {
                reportError(
                    if (matches.isEmpty()) "No entity named '$entityName' in '${source.name}'"
                    else "Multiple entities named '$entityName' in '${source.name}'"
                )
                return null
            }
            filter = Entities(matches)
            entities = source.getEntities(filter, includeChildren = true).filter { it.isNot(HIDDEN) }
        }

        var xMin = Float.POSITIVE_INFINITY
        var yMin = Float.POSITIVE_INFINITY
        var zMin = Float.POSITIVE_INFINITY
        var xMax = Float.NEGATIVE_INFINITY
        var yMax = Float.NEGATIVE_INFINITY
        var zMax = Float.NEGATIVE_INFINITY
        var foundPosition = false

        var xMin2D = Float.POSITIVE_INFINITY
        var yMin2D = Float.POSITIVE_INFINITY
        var xMax2D = Float.NEGATIVE_INFINITY
        var yMax2D = Float.NEGATIVE_INFINITY
        var found2DBounds = false

        entities.forEachFast()
        {
            val entity = it
            if (entity is Translatable3D)
            {
                if (entity.position.x < xMin) xMin = entity.position.x
                if (entity.position.x > xMax) xMax = entity.position.x
                if (entity.position.y < yMin) yMin = entity.position.y
                if (entity.position.y > yMax) yMax = entity.position.y
                if (entity.position.z < zMin) zMin = entity.position.z
                if (entity.position.z > zMax) zMax = entity.position.z
                foundPosition = true
            }

            if (entity is Spatial2D)
            {
                val angle = entity.zRotation * PI.toFloat() / 180f
                val cos = abs(cos(angle))
                val sin = abs(sin(angle))
                val halfWidth = abs(entity.width) * 0.5f
                val halfHeight = abs(entity.height) * 0.5f
                val xExtent = cos * halfWidth + sin * halfHeight
                val yExtent = sin * halfWidth + cos * halfHeight
                val left = entity.x - xExtent
                val right = entity.x + xExtent
                val bottom = entity.y - yExtent
                val top = entity.y + yExtent

                if (entity !is Translatable3D)
                {
                    if (left < xMin)     xMin = left
                    if (right > xMax)    xMax = right
                    if (bottom < yMin)   yMin = bottom
                    if (top > yMax)      yMax = top
                    if (entity.z < zMin) zMin = entity.z
                    if (entity.z > zMax) zMax = entity.z
                    foundPosition = true
                }

                if (left < xMin2D)   xMin2D = left
                if (right > xMax2D)  xMax2D = right
                if (bottom < yMin2D) yMin2D = bottom
                if (top > yMax2D)    yMax2D = top
                
                found2DBounds = true
            }
        }

        val xCenter = if (foundPosition) (xMin + xMax) * 0.5f else 0f
        val yCenter = if (foundPosition) (yMin + yMax) * 0.5f else 0f
        val zCenter = if (foundPosition) (zMin + zMax) * 0.5f else 0f
        val width   = if (found2DBounds) max(DEFAULT_PREVIEW_SIZE, xMax2D - xMin2D) else DEFAULT_PREVIEW_SIZE
        val height  = if (found2DBounds) max(DEFAULT_PREVIEW_SIZE, yMax2D - yMin2D) else DEFAULT_PREVIEW_SIZE

        lastErrorSignature = ""
        return ResolvedSelection(filter, entities, xCenter, yCenter, zCenter, width, height)
    }

    private fun clearPreview()
    {
        previewSource = null
        previewSceneKey = ""
        previewEntityName = ""
        previewCenterX = 0f
        previewCenterY = 0f
        previewCenterZ = 0f
        previewEntries.clear()
        previewRenderables2D.clear()
        previewFrame = Long.MIN_VALUE
        baseWidth = DEFAULT_PREVIEW_SIZE
        baseHeight = DEFAULT_PREVIEW_SIZE
    }

    private fun reportError(message: String)
    {
        val signature = "${sceneKey()}|$entityName|$message"
        if (signature != lastErrorSignature)
        {
            Logger.error { "Prefab '$name': $message" }
            lastErrorSignature = signature
        }
    }

    private fun sceneKey(): String
    {
        if (cachedSceneFile == sceneFile) return cachedSceneKey

        cachedSceneFile = sceneFile

        val file = File(sceneFile)
        cachedSceneKey = if (file.isAbsolute)
        {
            runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        }
        else ResourceResolver.normalizeRelativePath(sceneFile) ?: sceneFile.trim().replace('\\', '/')

        return cachedSceneKey
    }

    private data class ResolvedSelection(
        val filter: SceneEntityFilter,
        val entities: List<SceneEntity>,
        val xCenter: Float,
        val yCenter: Float,
        val zCenter: Float,
        val width: Float,
        val height: Float
    )

    private data class PreviewEntry(
        val source: SceneEntity,
        val preview: SceneEntity
    )

    companion object
    {
        private const val DEFAULT_PREVIEW_SIZE = 32f
        private const val MIN_SIZE = 0.0001f
        private const val PREVIEW_ID_BIT = 1L shl 62
        private const val PREVIEW_ID_MASK = PREVIEW_ID_BIT - 1L

        private object BackToFrontComparator : Comparator<Renderable2D>
        {
            override fun compare(a: Renderable2D, b: Renderable2D): Int = ((b.z - a.z) * 10_000f).toInt()
        }

        private fun previewEntityId(ownerId: Long, sourceId: Long, index: Int): Long
        {
            var value = ownerId.takeIf { it != INVALID_ID } ?: 0L
            value = (value xor (sourceId + 0x9E3779B9L)) * -7046029254386353131L
            value = (value xor index.toLong()) * -4658895280553007687L
            val mixed = value xor (value ushr 32)
            return PREVIEW_ID_BIT or (mixed and PREVIEW_ID_MASK)
        }
    }
}

private object PrefabTransform
{
    private val sourceTransform = Matrix4f()
    private val resultTransform = Matrix4f()
    private val rotation = Quaternionf()
    private val eulerAngles = Vector3f()

    fun apply(entity: SceneEntity, prefab: Prefab, xCenter: Float, yCenter: Float, zCenter: Float) 
    {
        when (entity)
        {
            is Translatable3D -> apply3D(entity, prefab, xCenter, yCenter, zCenter)
            is Spatial2D      -> apply2D(entity, prefab, xCenter, yCenter, zCenter)
        }
    }

    fun restore(source: SceneEntity, target: SceneEntity)
    {
        if (source is Translatable3D && target is Translatable3D)
        {
            target.position.set(source.position)
            if (source is Rotatable3D && target is Rotatable3D)
                target.rotation.set(source.rotation)
            if (source is Scalable3D && target is Scalable3D)
                target.scale.set(source.scale)
        }
        else if (source is Spatial2D && target is Spatial2D)
        {
            target.x = source.x
            target.y = source.y
            target.z = source.z
            target.width = source.width
            target.height = source.height
            target.zRotation = source.zRotation
        }
    }

    private fun apply2D(entity: Spatial2D, prefab: Prefab, xCenter: Float, yCenter: Float, zCenter: Float)
    {
        val x = (entity.x - xCenter) * prefab.scale.x
        val y = (entity.y - yCenter) * prefab.scale.y
        val angle = prefab.rotation.z * PI.toFloat() / 180f
        val cos = cos(angle)
        val sin = sin(angle)

        entity.x = prefab.position.x + cos * x - sin * y
        entity.y = prefab.position.y + sin * x + cos * y
        entity.z = prefab.position.z + entity.z - zCenter
        entity.width *= abs(prefab.scale.x)
        entity.height *= abs(prefab.scale.y)
        entity.zRotation += prefab.rotation.z
    }

    private fun apply3D(entity: SceneEntity, prefab: Prefab, xCenter: Float, yCenter: Float, zCenter: Float)
    {
        val translatable = entity as? Translatable3D ?: return
        val rotatable = entity as? Rotatable3D
        val scalable = entity as? Scalable3D

        sourceTransform
            .identity()
            .translation(translatable.position.x - xCenter, translatable.position.y - yCenter, translatable.position.z - zCenter)
            .rotateXYZ(
                (rotatable?.rotation?.x ?: 0f).toRadians(),
                (rotatable?.rotation?.y ?: 0f).toRadians(),
                (rotatable?.rotation?.z ?: 0f).toRadians()
            )
            .scale(
                scalable?.scale?.x ?: 1f,
                scalable?.scale?.y ?: 1f,
                scalable?.scale?.z ?: 1f
            )

        resultTransform
            .identity()
            .translation(prefab.position)
            .rotateXYZ(prefab.rotation.x.toRadians(), prefab.rotation.y.toRadians(), prefab.rotation.z.toRadians())
            .scale(prefab.scale)
            .mul(sourceTransform)

        resultTransform.getTranslation(translatable.position)

        if (rotatable != null)
        {
            resultTransform.getUnnormalizedRotation(rotation).getEulerAnglesXYZ(eulerAngles)
            rotatable.rotation.set(
                eulerAngles.x.toDegrees(),
                eulerAngles.y.toDegrees(),
                eulerAngles.z.toDegrees()
            )
        }

        if (scalable != null)
        {
            resultTransform.getScale(scalable.scale)
        }
    }
}