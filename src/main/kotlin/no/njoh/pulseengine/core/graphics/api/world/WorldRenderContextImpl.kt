package no.njoh.pulseengine.core.graphics.api.world

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.*
import no.njoh.pulseengine.core.graphics.api.objects.BoneBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.CullingBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.api.world.views.WorldCameraRenderView
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderView
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.utils.Logger
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3fc

class WorldRenderContextImpl : WorldRenderContextInternal()
{
    private var views = mutableListOf<WorldRenderView>()

    private var nextFrameScene = WorldRenderScene()
    private var thisFrameScene = WorldRenderScene()

    private val instanceBuffer   = InstanceBufferObject()
    private val cullingBuffer    = CullingBufferObject()
    private val boneBuffer       = BoneBufferObject()
    private val commandBuilder   = WorldRenderCommandBuilder(instanceBuffer, cullingBuffer)
    private val localShadowAtlas = LocalShadowAtlas()
 
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
            localShadowAtlas.prepare(thisFrameScene, getPrimaryCameraPosition())
            views.forEach()
            {
                if (it is WorldCameraRenderView)
                    it.clusteredLights.clearAndSubmit()
            }
            return
        }

        if (!initialized)
        {
            instanceBuffer.init()
            cullingBuffer.init()
            boneBuffer.init()
            commandBuilder.init(engine)
            initialized = true
        }

        instanceBuffer.clear()
        cullingBuffer.clear()
        boneBuffer.clear()

        thisFrameScene.opaqueItems.addToBuffers()
        thisFrameScene.maskedItems.addToBuffers()
        thisFrameScene.blendedItems.addToBuffers()

        instanceBuffer.submit()
        cullingBuffer.submit()
        boneBuffer.submit()

        commandBuilder.beginFrame()
        localShadowAtlas.prepare(thisFrameScene, getPrimaryCameraPosition())

        for (view in views)
        {
            view.clear()
            view.update(thisFrameScene, commandBuilder)
            if (view is WorldCameraRenderView)
                view.clusteredLights.buildAndSubmit(view, thisFrameScene, localShadowAtlas)
        }

        commandBuilder.finishFramePreparation()
    }

    override fun endFrame()
    {
        if (!initialized || !thisFrameScene.hasAnyItems()) return

        GpuProfiler.measure("fence context buffers")
        {
            instanceBuffer.markSubmittedDataInUse()
            cullingBuffer.markSubmittedDataInUse()
            boneBuffer.markGpuDataInUse()
            commandBuilder.markSubmittedDataInUse()
            views.forEach()
            {
                if (it is WorldCameraRenderView)
                    it.clusteredLights.markSubmittedDataInUse()
            }
        }
    }

    override fun destroy()
    {
        views.forEach()
        {
            if (it is WorldCameraRenderView)
                it.destroy()
        }

        if (!initialized) return

        instanceBuffer.destroy()
        cullingBuffer.destroy()
        boneBuffer.destroy()
        commandBuilder.destroy()
        initialized = false
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
        views.firstOrNull { it.viewId == viewId && (it.javaClass == type || type.isAssignableFrom(it.javaClass)) } as T?

    override fun submitModel(engine: PulseEngine, model: Model, transform: Matrix4f, material: Material?, animationPose: AnimatedSkeletonPose?, viewIds: Int)
    {
        for (instance in model.meshInstances)
        {
            val material      = material ?: model.materials.getOrNull(instance.mesh.materialIndex)?.let { engine.asset.getOrNull(it.name) }
            val animatedPose  = animationPose?.getAnimatedMeshPose(instance.nodeName)
            val boneMatrices  = animatedPose?.boneMatrices ?: model.getBindPoseBoneMatrices(instance.nodeName)
            val cullingBounds = instance.mesh.animatedBounds?.takeIf { animatedPose != null } ?: instance.cullingBounds

            val transform = Matrix4f(transform).mul(instance.transform)

            nextFrameScene.addMesh(instance.mesh, material, transform, cullingBounds, boneMatrices, viewIds)
        }
    }

    override fun submitMesh(mesh: Mesh, material: Material?, transform: Matrix4f, cullingBounds: Aabb?, boneMatrices: Array<Matrix4f>?, viewIds: Int)
    {
        nextFrameScene.addMesh(mesh, material, transform, cullingBounds, boneMatrices, viewIds)
    }

    override fun submitPointLight(position: Vector3f, radius: Float, color: Color, shadowEnabled: Boolean, shadowResolution: Int, shadowBias: Float, shadowImportance: Float, shadowId: Long)
    {
        nextFrameScene.addLight(position, null, radius, color, 180f, 180f, shadowEnabled, shadowResolution, shadowBias, shadowImportance, shadowId)
    }

    override fun submitSpotLight(
        position: Vector3f,
        direction: Vector3f,
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
        nextFrameScene.addLight(position, direction, radius, color, innerConeAngle, outerConeAngle, shadowEnabled, shadowResolution, shadowBias, shadowImportance, shadowId)
    }

    override fun getLocalShadowAtlas() = localShadowAtlas

    private fun getPrimaryCameraPosition(): Vector3fc?
    {
        for (view in views)
            if (view is WorldCameraRenderView)
                return view.cameraPosition
        return null
    }

    private fun DynamicList<WorldRenderItem>.addToBuffers() =
        forEach { item ->
            val boneOffset = boneBuffer.addBoneMatricesAndGetOffset(item.boneMatrices)
            item.gpuInstanceIndex = instanceBuffer.addItem(item, boneOffset)
            cullingBuffer.addItem(item)
        }
}