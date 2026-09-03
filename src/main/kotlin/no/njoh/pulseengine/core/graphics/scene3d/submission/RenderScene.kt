package no.njoh.pulseengine.core.graphics.scene3d.submission

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.*
import no.njoh.pulseengine.core.graphics.scene3d.view.ModelLodResolver
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.primitives.Mat4f
import no.njoh.pulseengine.core.shared.primitives.Mat4fArena
import no.njoh.pulseengine.core.shared.primitives.Mat4fProps
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.min

class RenderScene
{
    val opaqueItems  = DynamicList<RenderItem>(1024)
    val maskedItems  = DynamicList<RenderItem>(512)
    val blendedItems = DynamicList<RenderItem>(256)
    val localLights  = DynamicList<RenderLight>(64)

    val submittedSnapshot = RenderSceneSnapshot()

    val hasSubmittedGeometry get() = hasDrawItems || hasPendingModels
    val hasPendingModels     get() = pendingModels.isNotEmpty()
    val hasDrawItems         get() = drawItemCount > 0
    val pendingModelCount    get() = pendingModels.size
    val drawItemCount        get() = opaqueItems.size + maskedItems.size + blendedItems.size

    private val matrixArena   = Mat4fArena()
    private val pendingModels = DynamicList<ModelItem>()
    private val modelPool     = DynamicList<ModelItem>()
    private val itemPool      = DynamicList<RenderItem>()
    private val lightPool     = DynamicList<RenderLight>()

    fun addModel(
        model: Model,
        transform: Matrix4f,
        material: Material?,
        renderPassMask: RenderPassMask,
        lodThresholds: FloatArray?,
        lodHysteresis: Float,
        lodKey: Long,
        renderId: Long
    ) {
        val item = modelPool.removeLastOrNull() ?: ModelItem()
        item.set(model, transform, material, renderPassMask, lodThresholds, lodHysteresis, lodKey, renderId)
        pendingModels += item
    }

    fun addMesh(mesh: Mesh, material: Material?, transform: Matrix4f, cullingBounds: Aabb?, boneMatrices: Array<Matrix4f>?, renderPassMask: RenderPassMask, renderId: Long)
    {
        val item = itemPool.removeLastOrNull() ?: RenderItem()
        item.set(mesh, material, Mat4f(matrixArena).set(transform), cullingBounds, boneMatrices, renderPassMask, renderId)
        addRenderItem(item)
    }

    fun addModelMeshes(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material?,
        animationPose: AnimatedSkeletonPose?,
        lodLevel: Int,
        renderPassMask: RenderPassMask,
        renderId: Long
    ) {
        val meshInstances = model.lodLevels[lodLevel]
        val transformProperties = Mat4fProps.from(transform)
        val hasBones = model.hasBones

        meshInstances.forEachFast()
        {
            val animatedMeshPose = if (hasBones) animationPose?.getAnimatedMeshPose(it.nodeName) else null
            val boneMatrices     = if (hasBones) animatedMeshPose?.boneMatrices ?: model.getBindPoseBoneMatrices(it.nodeName) else null
            val cullingBounds    = if (hasBones && animatedMeshPose != null) it.mesh.animatedBounds ?: it.cullingBounds else it.cullingBounds
            val resolvedMaterial = material ?: engine.asset.getOrNull(it.materialHandle)
            val meshTransform    = Mat4f(matrixArena)

            if (it.transformProperties.isIdentity)
            {
                meshTransform.set(transform, transformProperties)
            }
            else
            {
                val properties = transformProperties.commonWith(it.transformProperties)
                meshTransform.setMul(transform, it.transform, properties)
            }

            val item = itemPool.removeLastOrNull() ?: RenderItem()
            item.set(it.mesh, resolvedMaterial, meshTransform, cullingBounds, boneMatrices, renderPassMask, renderId)
            addRenderItem(item)
        }
    }

    fun addRenderItem(item: RenderItem)
    {
        when (item.material?.blendMode ?: OPAQUE)
        {
            OPAQUE -> opaqueItems  += item
            MASK   -> maskedItems  += item
            BLEND  -> blendedItems += item
        }
    }

    fun addLight(
        position: Vector3f,
        direction: Vector3f?,
        range: Float,
        sourceRadius: Float,
        color: Color,
        innerConeAngle: Float,
        outerConeAngle: Float,
        shadowEnabled: Boolean,
        shadowResolution: Int,
        shadowNearPlane: Float,
        shadowBias: Float,
        shadowImportance: Float,
        shadowId: Long
    ) {
        val light = lightPool.removeLastOrNull() ?: RenderLight()

        if (direction != null && direction.lengthSquared() > 0.000001f)
        {
            light.direction.set(direction)
            light.direction.normalize()
        }
        else light.direction.set(0f, -1f, 0f)

        light.position.set(position)
        light.range = range.sanitizeNonNegative()
        light.sourceRadius = sourceRadius.sanitizeNonNegative().coerceAtMost(light.range)
        light.color.setFrom(color)
        light.outerConeAngle = outerConeAngle.sanitizeAngle()
        light.innerConeAngle = min(innerConeAngle.sanitizeAngle(), light.outerConeAngle)
        light.shadowEnabled = shadowEnabled
        light.shadowResolution = shadowResolution.coerceAtLeast(0)
        light.shadowNearPlane = shadowNearPlane.sanitizeNonNegative()
        light.shadowBias = shadowBias.sanitizeNonNegative()
        light.shadowImportance = shadowImportance.sanitizeNonNegative()
        light.shadowId = shadowId
        light.shadowFaceOffset = -1
        light.shadowFaceCount = 0

        localLights += light
    }

    fun selectPendingModelLods(engine: PulseEngine, lodResolver: ModelLodResolver)
    {
        pendingModels.forEach()
        {
            val lodLevel = lodResolver.resolve(it)
            addModelMeshes(engine, it.model, it.transform, it.material, animationPose = null, lodLevel, it.renderPassMask, it.renderId)
        }
    }

    fun captureSubmittedSnapshot() = submittedSnapshot.capture(opaqueItems, maskedItems, blendedItems, pendingModels)

    fun clear()
    {
        itemPool  += opaqueItems
        itemPool  += maskedItems
        itemPool  += blendedItems
        lightPool += localLights
        modelPool += pendingModels

        opaqueItems.clear()
        maskedItems.clear()
        blendedItems.clear()
        localLights.clear()
        pendingModels.clear()
        matrixArena.reset()
    }

    private fun Float.sanitizeAngle() = if (isFinite()) coerceIn(0f, 180f) else 0f
    private fun Float.sanitizeNonNegative() = if (isFinite()) coerceAtLeast(0f) else 0f
}

/**
 * Stable view of the items submitted before LOD expansion begins.
 * The backing arrays are retained without copying and remain valid until the next frame swap.
 */
class RenderSceneSnapshot
{
    private var opaqueItems      = emptyArray<Any?>()
    private var maskedItems      = emptyArray<Any?>()
    private var blendedItems     = emptyArray<Any?>()
    private var modelItems       = emptyArray<Any?>()
    private var opaqueItemCount  = 0
    private var maskedItemCount  = 0
    private var blendedItemCount = 0

    var renderItemCount = 0; private set
    var modelItemCount  = 0; private set

    fun capture(
        opaqueSceneItems: DynamicList<RenderItem>,
        maskedSceneItems: DynamicList<RenderItem>,
        blendedSceneItems: DynamicList<RenderItem>,
        modelSceneItems: DynamicList<ModelItem>
    ) {
        opaqueItems  = opaqueSceneItems.data
        maskedItems  = maskedSceneItems.data
        blendedItems = blendedSceneItems.data
        modelItems   = modelSceneItems.data

        opaqueItemCount  = opaqueSceneItems.size
        maskedItemCount  = maskedSceneItems.size
        blendedItemCount = blendedSceneItems.size
        modelItemCount   = modelSceneItems.size

        renderItemCount = opaqueItemCount + maskedItemCount + blendedItemCount
    }

    fun getRenderItem(index: Int): RenderItem
    {
        if (index < opaqueItemCount)
            return opaqueItems[index] as RenderItem

        val maskedIndex = index - opaqueItemCount
        if (maskedIndex < maskedItemCount)
            return maskedItems[maskedIndex] as RenderItem

        return blendedItems[maskedIndex - maskedItemCount] as RenderItem
    }

    fun getModelItem(index: Int) = modelItems[index] as ModelItem
}