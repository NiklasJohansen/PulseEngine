package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Animation
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Model
import org.joml.Matrix4f

class DrawList(
    val opaqueItems: ArrayList<RenderItem> = ArrayList(256),
    val maskedItems: ArrayList<RenderItem> = ArrayList(256),
    val transparentItems: ArrayList<RenderItem> = ArrayList(256)
) {
    fun submit(engine: PulseEngine, model: Model, transform: Matrix4f, material: Material? = null)
    {
        for (instance in model.subMeshInstances)
        {
            val subMesh  = instance.subMesh
            val material = material ?: model.materials.getOrNull(subMesh.materialIndex)?.let { engine.asset.getOrNull(it.name) }
            val boneMatrices = model.getBindPoseBoneMatrices(instance.nodeName)
            val transform = Matrix4f(transform).mul(instance.transform)

            submit(model, subMesh, material, transform, cullable = true, instance.cullingBounds, boneMatrices)
        }
    }

    fun submit(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material? = null,
        animation: Animation?,
        animationTimeSeconds: Float = 0f,
        blendAnimation: Animation? = null,
        blendAnimationTimeSeconds: Float = 0f,
        blendFactor: Float = 0f
    ) {
        for (instance in model.subMeshInstances)
        {
            val subMesh  = instance.subMesh
            val material = material ?: model.materials.getOrNull(subMesh.materialIndex)?.let { engine.asset.getOrNull(it.name) }

            val animatedPose = model.getAnimatedPose(
                nodeName = instance.nodeName,
                animation = animation,
                animationTimeSeconds = animationTimeSeconds,
                blendAnimation = blendAnimation,
                blendAnimationTimeSeconds = blendAnimationTimeSeconds,
                blendFactor = blendFactor,
                frameNumber = engine.data.frameNumber
            )
            val boneMatrices  = animatedPose?.boneMatrices ?: model.getBindPoseBoneMatrices(instance.nodeName)
            val cullingBounds = animatedPose?.getBounds(subMesh) ?: instance.cullingBounds

            val transform = Matrix4f(transform).mul(instance.transform)

            submit(model, subMesh, material, transform, cullable = true, cullingBounds, boneMatrices)
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
            if (!item.cullable || frustum.intersectsAabb(item.cullingBounds, item.transform))
                result += item
        }
        return result
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