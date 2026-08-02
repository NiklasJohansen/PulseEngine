package no.njoh.pulseengine.core.graphics.scene3d.submission

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask
import no.njoh.pulseengine.core.shared.primitives.Mat4f
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.min

class RenderScene
{
    val opaqueItems  = DynamicList<RenderItem>(1024)
    val maskedItems  = DynamicList<RenderItem>(512)
    val blendedItems = DynamicList<RenderItem>(256)
    val localLights  = DynamicList<RenderLight>(64)

    fun addMesh(mesh: Mesh, material: Material?, transform: Mat4f, cullingBounds: Model.Aabb?, boneMatrices: Array<Matrix4f>?, renderPassMask: RenderPassMask, objectId: Long = -1L)
    {
        val poolItem = ITEM_POOL.removeLastOrNull()?.also()
        {
            it.setBatchState(mesh, material)
            it.transform = transform
            it.cullingBounds = cullingBounds
            it.boneMatrices = boneMatrices
            it.renderPassMask = renderPassMask
            it.objectId = objectId
        }

        val item = poolItem ?: RenderItem(mesh, material, transform, cullingBounds, boneMatrices, renderPassMask, objectId)

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
        radius: Float,
        color: Color,
        innerConeAngle: Float,
        outerConeAngle: Float,
        shadowEnabled: Boolean,
        shadowResolution: Int,
        shadowBias: Float,
        shadowImportance: Float,
        shadowId: Long
    ) {
        val light = LIGHT_POOL.removeLastOrNull() ?: RenderLight()

        if (direction != null && direction.lengthSquared() > 0.000001f)
        {
            light.direction.set(direction)
            light.direction.normalize()
        }
        else light.direction.set(0f, -1f, 0f)

        light.position.set(position)
        light.radius = radius.coerceAtLeast(0f)
        light.color.setFrom(color)
        light.outerConeAngle = outerConeAngle.sanitizeAngle()
        light.innerConeAngle = min(innerConeAngle.sanitizeAngle(), light.outerConeAngle)
        light.shadowEnabled = shadowEnabled
        light.shadowResolution = shadowResolution.coerceAtLeast(0)
        light.shadowBias = shadowBias.sanitizeNonNegative()
        light.shadowImportance = shadowImportance.sanitizeNonNegative()
        light.shadowId = shadowId
        light.shadowFaceOffset = -1
        light.shadowFaceCount = 0

        localLights += light
    }

    fun clear()
    {
        ITEM_POOL += opaqueItems
        ITEM_POOL += maskedItems
        ITEM_POOL += blendedItems
        LIGHT_POOL += localLights
        opaqueItems.clear()
        maskedItems.clear()
        blendedItems.clear()
        localLights.clear()
    }
 
    fun hasAnyItems() = opaqueItems.isNotEmpty() || maskedItems.isNotEmpty() || blendedItems.isNotEmpty()

    private fun Float.sanitizeAngle() = if (isFinite()) coerceIn(0f, 180f) else 0f

    private fun Float.sanitizeNonNegative() = if (isFinite()) coerceAtLeast(0f) else 0f

    companion object
    {
        private val ITEM_POOL = DynamicList<RenderItem>()
        private val LIGHT_POOL = DynamicList<RenderLight>()
    }
}