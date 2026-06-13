package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.min

class WorldRenderScene
{
    val opaqueItems  = DynamicList<WorldRenderItem>(1024)
    val maskedItems  = DynamicList<WorldRenderItem>(512)
    val blendedItems = DynamicList<WorldRenderItem>(256)
    val localLights  = DynamicList<WorldRenderLight>(64)

    fun addMesh(mesh: Mesh, material: Material?, transform: Matrix4f, cullingBounds: Model.Aabb?, boneMatrices: Array<Matrix4f>?, viewIds: Int)
    {
        val poolItem = ITEM_POOL.removeLastOrNull()?.also()
        {
            it.mesh = mesh
            it.material = material
            it.transform = transform
            it.cullingBounds = cullingBounds
            it.boneMatrices = boneMatrices
            it.viewIds = viewIds
        }

        val item = poolItem ?: WorldRenderItem(mesh, material, transform, cullingBounds, boneMatrices, viewIds)

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
        val light = LIGHT_POOL.removeLastOrNull() ?: WorldRenderLight()

        if (direction != null)
        {
            light.direction.set(direction)
            if (direction.lengthSquared() > 0.000001f) direction.normalize() else direction.set(0f, -1f, 0f)
        }

        light.position.set(position)
        light.radius = radius
        light.color.setFrom(color)
        light.outerConeAngle = min(outerConeAngle, 180f)
        light.innerConeAngle = min(innerConeAngle, 180f)
        light.shadowEnabled = shadowEnabled
        light.shadowResolution = shadowResolution
        light.shadowBias = shadowBias
        light.shadowImportance = shadowImportance
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

    companion object
    {
        private val ITEM_POOL = DynamicList<WorldRenderItem>()
        private val LIGHT_POOL = DynamicList<WorldRenderLight>()
    }
}