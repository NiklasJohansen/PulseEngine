package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f

class WorldRenderScene
{
    val opaqueItems  = DynamicList<WorldRenderItem>(1024)
    val maskedItems  = DynamicList<WorldRenderItem>(512)
    val blendedItems = DynamicList<WorldRenderItem>(256)

    fun add(mesh: Model.Mesh, material: Material?, transform: Matrix4f, cullingBounds: Model.Aabb?, boneMatrices: Array<Matrix4f>?, viewIds: Int) 
    {
        val poolItem = POOL.removeLastOrNull()?.also() 
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

    fun clear()
    {
        POOL += opaqueItems
        POOL += maskedItems
        POOL += blendedItems
        opaqueItems.clear()
        maskedItems.clear()
        blendedItems.clear()
    }
 
    fun hasAnyItems() = opaqueItems.isNotEmpty() || maskedItems.isNotEmpty() || blendedItems.isNotEmpty()

    companion object
    {
        private val POOL = DynamicList<WorldRenderItem>()
    }
}