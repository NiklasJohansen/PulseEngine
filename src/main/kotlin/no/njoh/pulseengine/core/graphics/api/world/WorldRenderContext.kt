package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.AnimatedSkeletonPose
import no.njoh.pulseengine.core.graphics.api.objects.BoneBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.api.world.views.ViewIds
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderView
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import org.joml.Matrix4f

class WorldRenderContext
{
    private var nextFrameScene = WorldRenderScene()
    private var thisFrameScene = WorldRenderScene()

    var views = mutableListOf<WorldRenderView>()

    private val instanceBuffer = InstanceBufferObject()
    private val boneBuffer     = BoneBufferObject()
    private val cullData       = WorldRenderItemCullingData()
    private var initalized     = false

    fun initFrame()
    {
        nextFrameScene = thisFrameScene.also { thisFrameScene = nextFrameScene }
        nextFrameScene.clear()
    }

    fun prepareFrameDraw(engine: PulseEngineInternal)
    {
        if (!thisFrameScene.hasAnyItems())
            return

        if (!initalized)
        {
            cullData.init()
            instanceBuffer.init()
            initalized = true
        }

        boneBuffer.clear()
        instanceBuffer.clear()
        cullData.clear()

        thisFrameScene.opaqueItems.addToBuffers()
        thisFrameScene.maskedItems.addToBuffers()
        thisFrameScene.blendedItems.addToBuffers()

        cullData.submit()
        instanceBuffer.submit()
        boneBuffer.submit()

        var commandBufferStarIndex = 0
        for (view in views)
        {
            view.clear()
            commandBufferStarIndex = view.update(engine, thisFrameScene, commandBufferStarIndex, cullData)
        }
    }

    fun endFrame()
    {
        if (!thisFrameScene.hasAnyItems()) return

        GpuProfiler.measure("sync context buffers")
        {
            instanceBuffer.markSubmittedDataInUse()
            cullData.markSubmittedDataInUse()
            boneBuffer.markGpuDataInUse()
            views.forEach { it.finish() }
        }
    }

    fun submit(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material? = null,
        animationPose: AnimatedSkeletonPose? = null,
        viewIds: Int = ViewIds.MAIN_CAMERA_VIEW or ViewIds.SHADOW_VIEW
    ) {
        for (instance in model.subMeshInstances)
        {
            val material      = material ?: model.materials.getOrNull(instance.subMesh.materialIndex)?.let { engine.asset.getOrNull(it.name) }
            val animatedPose  = animationPose?.getAnimatedMeshPose(instance.nodeName)
            val boneMatrices  = animatedPose?.boneMatrices ?: model.getBindPoseBoneMatrices(instance.nodeName)
            val cullingBounds = instance.subMesh.animatedBounds?.takeIf { animatedPose != null } ?: instance.cullingBounds

            val transform = Matrix4f(transform).mul(instance.transform)

            submit(model, instance.subMesh, material, transform, cullable = true, cullingBounds, boneMatrices, viewIds)
        }
    }

    fun submit(
        model: Model,
        subMesh: Model.SubMesh,
        material: Material?,
        transform: Matrix4f,
        cullable: Boolean = true,
        cullingBounds: Model.Aabb = subMesh.localBounds,
        boneMatrices: Array<Matrix4f>? = null,
        viewIds: Int = ViewIds.MAIN_CAMERA_VIEW or ViewIds.SHADOW_VIEW
    ) {
        val item = WorldRenderItem(model, subMesh, material, transform, cullable, cullingBounds, boneMatrices, viewIds)
        nextFrameScene.add(item)
    }

    fun destroy()
    {
        instanceBuffer.destroy()
        boneBuffer.destroy()
        cullData.destroy()
        views.forEach { it.destroy() }
    }

    inline fun addView(viewId: Int, onCreate: (Int) -> WorldRenderView)
    {
        if (views.any { it.viewId == viewId }) return

        views += onCreate(viewId)
    }

    inline fun <reified T> getRenderView(viewId: Int) = views.firstOrNull { it.viewId == viewId && it is T } as T?
    
    fun getInstancesBuffer() = instanceBuffer

    private fun DynamicList<WorldRenderItem>.addToBuffers() =
        forEach { item ->
            val boneOffset = boneBuffer.addBoneMatricesAndGetOffset(item.boneMatrices)
            item.gpuInstanceIndex = instanceBuffer.addItem(item, boneOffset)
            cullData.addItem(item)
        }
}