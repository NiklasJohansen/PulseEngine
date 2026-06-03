package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.api.objects.BoneBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.CullingBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderView
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.utils.Logger
import org.joml.Matrix4f

class WorldRenderContextImpl : WorldRenderContextInternal()
{
    private var views = mutableListOf<WorldRenderView>()

    private var nextFrameScene = WorldRenderScene()
    private var thisFrameScene = WorldRenderScene()

    private val instanceBuffer = InstanceBufferObject()
    private val cullingBuffer  = CullingBufferObject()
    private val boneBuffer     = BoneBufferObject()
    private val drawBuffer     = WorldRenderDrawBuffer(instanceBuffer)
 
    private var initialized = false

    override fun initFrame()
    {
        nextFrameScene = thisFrameScene.also { thisFrameScene = nextFrameScene }
        nextFrameScene.clear()
    }

    override fun buildFrame(engine: PulseEngineInternal)
    {
        if (!thisFrameScene.hasAnyItems())
        {
            views.forEach { it.clear() }
            return
        }

        if (!initialized)
        {
            instanceBuffer.init()
            cullingBuffer.init()
            boneBuffer.init()
            drawBuffer.init(engine)
            initialized = true
        }

        instanceBuffer.clear()
        cullingBuffer.clear()
        boneBuffer.clear()

        thisFrameScene.opaqueItems.addToBuffers()
        thisFrameScene.maskedItems.addToBuffers()
        thisFrameScene.blendedItems.addToBuffers()

        cullingBuffer.submit()
        instanceBuffer.submit()
        boneBuffer.submit()

        drawBuffer.beginFrame(cullingBuffer)

        for (view in views)
        {
            view.clear()
            view.update(thisFrameScene, drawBuffer)
        }

        drawBuffer.finishPreparation(cullingBuffer)
    }
    
    override fun endFrame()
    {
        if (!thisFrameScene.hasAnyItems()) return

        GpuProfiler.measure("sync context buffers")
        {
            instanceBuffer.markSubmittedDataInUse()
            cullingBuffer.markSubmittedDataInUse()
            boneBuffer.markGpuDataInUse()
            drawBuffer.markSubmittedDataInUse()
        }
    }

    override fun destroy()
    {
        instanceBuffer.destroy()
        cullingBuffer.destroy()
        boneBuffer.destroy()
        drawBuffer.destroy()
    }

    override fun addView(view: WorldRenderView)
    {
        if (views.any { it.viewId == view.viewId })
        {
            Logger.error { "View with id ${view.viewId} already exists, replacing existing view" }
            views.removeIf { it.viewId == view.viewId }
        }
        views += view
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getView(viewId: Int, type: Class<T>) =
        views.firstOrNull { it.viewId == viewId && it.javaClass == type || type.isAssignableFrom(it.javaClass) } as T?

    override fun submit(engine: PulseEngine, model: Model, transform: Matrix4f, material: Material?, animationPose: Model.AnimatedSkeletonPose?, viewIds: Int) 
    {
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

    override fun submit(model: Model, subMesh: Model.SubMesh, material: Material?, transform: Matrix4f, cullable: Boolean, cullingBounds: Model.Aabb, boneMatrices: Array<Matrix4f>?, viewIds: Int) 
    {
        nextFrameScene.add(WorldRenderItem(model, subMesh, material, transform, cullable, cullingBounds, boneMatrices, viewIds))
    }

    private fun DynamicList<WorldRenderItem>.addToBuffers() =
        forEach { item ->
            val boneOffset = boneBuffer.addBoneMatricesAndGetOffset(item.boneMatrices)
            item.gpuInstanceIndex = instanceBuffer.addItem(item, boneOffset)
            cullingBuffer.addItem(item)
        }
}