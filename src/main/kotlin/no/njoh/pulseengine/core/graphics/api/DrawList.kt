package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.AnimatedModelPose
import no.njoh.pulseengine.core.graphics.api.DrawList.RenderItem
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.joml.Matrix4f

class DrawList(
    val opaqueItems: ArrayList<RenderItem> = ArrayList(256),
    val maskedItems: ArrayList<RenderItem> = ArrayList(256),
    val blendedItems: ArrayList<RenderItem> = ArrayList(256)
) {
    fun submit(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material? = null,
        animationPose: AnimatedModelPose? = null
    ) {
        for (instance in model.subMeshInstances)
        {
            val material      = material ?: model.materials.getOrNull(instance.subMesh.materialIndex)?.let { engine.asset.getOrNull(it.name) }
            val animatedPose  = animationPose?.getMeshPose(instance.nodeName)
            val boneMatrices  = animatedPose?.boneMatrices ?: model.getBindPoseBoneMatrices(instance.nodeName)
            val cullingBounds = animatedPose?.getBounds(instance.subMesh) ?: instance.cullingBounds

            val transform = Matrix4f(transform).mul(instance.transform)

            submit(model, instance.subMesh, material, transform, cullable = true, cullingBounds, boneMatrices)
        }
    }

    fun submit(
        model: Model,
        subMesh: Model.SubMesh,
        material: Material?,
        transform: Matrix4f,
        cullable: Boolean = true,
        cullingBounds: Model.Aabb = subMesh.localBounds,
        boneMatrices: Array<Matrix4f>? = null
    ) {
        val item = RenderItem(model, subMesh, material, transform, cullable, cullingBounds, boneMatrices)
        when (material?.blendMode ?: OPAQUE)
        {
            OPAQUE -> opaqueItems  += item
            MASK   -> maskedItems  += item
            BLEND  -> blendedItems += item
        }
    }

    data class RenderItem(
        val model: Model,
        val subMesh: Model.SubMesh,
        val material: Material?,
        val transform: Matrix4f,
        val cullable: Boolean,
        val cullingBounds: Model.Aabb,
        val boneMatrices: Array<Matrix4f>?
    )
}

fun ArrayList<RenderItem>.addAllVisible(source: List<RenderItem>, frustum: Frustum? = null)
{
    if (frustum != null)
    {
        source.forEachFast()
        {
            if (!it.cullable || frustum.intersectsAabb(it.cullingBounds, it.transform)) add(it)
        }
    }
    else source.forEachFast { add(it) }
}