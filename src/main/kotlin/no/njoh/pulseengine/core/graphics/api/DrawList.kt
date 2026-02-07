package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Model
import org.joml.Matrix4f

class DrawList(
    val opaqueItems : ArrayList<RenderItem> = ArrayList(256),
    val maskedItems : ArrayList<RenderItem> = ArrayList(256),
    val transparentItems: ArrayList<RenderItem> = ArrayList(256)
) {
    fun submit(engine: PulseEngine, model: Model, transform: Matrix4f)
    {
        for (instance in model.subMeshInstances)
        {
            val subMesh  = instance.subMesh
            val matInfo  = model.materials.getOrNull(subMesh.materialIndex)
            val material = matInfo?.name?.let { engine.asset.getOrNull<Material>(it) }
            
            val transform = Matrix4f(transform).mul(instance.transform)

            submit(model, subMesh, material, transform)
        }
    }

    fun submit(model: Model, subMesh: Model.SubMesh, material: Material?, transform: Matrix4f)
    {
        val item = RenderItem(model, subMesh, material, transform)
        when (material?.blendMode ?: OPAQUE)
        {
            OPAQUE -> opaqueItems += item
            MASK -> maskedItems += item
            TRANSPARENT -> transparentItems += item
        }
    }

    fun getFrustumCulledList(frustum: Frustum): DrawList
    {
        val opaque = opaqueItems.cullFor(frustum)
        val masked = maskedItems.cullFor(frustum)
        val transparent = transparentItems.cullFor(frustum)

        return DrawList(opaque, masked, transparent)
    }

    private fun List<RenderItem>.cullFor(frustum: Frustum): ArrayList<RenderItem>
    {
        val result = ArrayList<RenderItem>(this.size)
        for (i in indices)
        {
            val item = this[i]
            if (frustum.intersectsAabb(item.subMesh.localBounds, item.transform))
                result += item
        }
        return result
    }

    data class RenderItem(
        val model: Model,
        val subMesh: Model.SubMesh,
        val material: Material?,
        val transform: Matrix4f
    )
}