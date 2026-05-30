package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.AnimatedSkeletonPose
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f

class WorldRenderFrame(
    val opaqueItems: DynamicList<RenderItem> = DynamicList(1024),
    val maskedItems: DynamicList<RenderItem> = DynamicList(512),
    val blendedItems: DynamicList<RenderItem> = DynamicList(256)
) {
    internal var version = 0; private set

    fun clear()
    {
        version++
        opaqueItems.clear()
        maskedItems.clear()
        blendedItems.clear()
    }

    fun submit(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material? = null,
        animationPose: AnimatedSkeletonPose? = null
    ) {
        for (instance in model.subMeshInstances)
        {
            val material      = material ?: model.materials.getOrNull(instance.subMesh.materialIndex)?.let { engine.asset.getOrNull(it.name) }
            val animatedPose  = animationPose?.getAnimatedMeshPose(instance.nodeName)
            val boneMatrices  = animatedPose?.boneMatrices ?: model.getBindPoseBoneMatrices(instance.nodeName)
            val cullingBounds = instance.subMesh.animatedBounds?.takeIf { animatedPose != null } ?: instance.cullingBounds

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