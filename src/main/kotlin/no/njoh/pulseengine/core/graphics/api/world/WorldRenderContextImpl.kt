package no.njoh.pulseengine.core.graphics.api.world

import gnu.trove.map.hash.THashMap
import gnu.trove.set.hash.THashSet
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.*
import no.njoh.pulseengine.core.graphics.api.Camera
import no.njoh.pulseengine.core.graphics.api.objects.BoneBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.CullingBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.LightBufferObject
import no.njoh.pulseengine.core.graphics.api.world.views.CameraRenderStateProvider
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderView
import no.njoh.pulseengine.core.graphics.api.world.views.WorldViewDeclarer
import no.njoh.pulseengine.core.graphics.api.world.views.WorldRenderViewKey
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3fc

class WorldRenderContextImpl : WorldRenderContextInternal()
{
    private var nextFrameScene = WorldRenderScene()
    private var thisFrameScene = WorldRenderScene()

    private val views                  = DynamicList<WorldRenderView>()
    private val lightGrids             = THashMap<Camera, ClusteredLightGrid>()
    private val activeLightGridCameras = THashSet<Camera>()

    private val instanceBuffer    = InstanceBufferObject()
    private val cullingBuffer     = CullingBufferObject()
    private val boneBuffer        = BoneBufferObject()
    private val lightBuffer       = LightBufferObject()
    private val commandBuilder    = WorldRenderCommandBuilder(instanceBuffer, cullingBuffer)
    private val localShadowAtlas  = LocalShadowAtlas()
 
    private var initialized = false
    private var frameNumber = 0

    override fun initFrame()
    {
        nextFrameScene = thisFrameScene.also { thisFrameScene = nextFrameScene }
        nextFrameScene.clear()
        frameNumber++
        views.forEach { it.beginFrame() }
    }

    override fun buildFrame(engine: PulseEngineInternal)
    {
        // Declare world views
        engine.gfx.getAllSurfaces().forEachFast { surface -> 
            surface.getAllRenderers().forEachFast() 
            { 
                (it as? WorldViewDeclarer)?.declareWorldViews(engine, surface as SurfaceInternal, this) 
            }
        }

        views.removeIf { it.lastFrameRequested < frameNumber - 10 }

        var camPos: Vector3fc? = null
        views.forEach { if (it.wasRequestedThisFrame() && it is CameraRenderStateProvider) camPos = it.shadowReferencePosition }
        localShadowAtlas.update(thisFrameScene, camPos)

        if (!thisFrameScene.hasAnyItems())
        {
            buildLightGrids()
            return
        }

        if (!initialized)
        {
            instanceBuffer.init()
            cullingBuffer.init()
            boneBuffer.init()
            lightBuffer.init()
            commandBuilder.init(engine)
            initialized = true
        }

        instanceBuffer.clear()
        cullingBuffer.clear()
        boneBuffer.clear()
        lightBuffer.clear()

        // Upload items
        thisFrameScene.opaqueItems.addToBuffers()
        thisFrameScene.maskedItems.addToBuffers()
        thisFrameScene.blendedItems.addToBuffers()

        // Upload lights and shadow faces
        thisFrameScene.localLights.forEach { lightBuffer.addLight(it) }
        localShadowAtlas.getActiveShadowFaces().forEach { face -> lightBuffer.addShadowFace(face) }

        instanceBuffer.submit()
        cullingBuffer.submit()
        boneBuffer.submit()
        lightBuffer.submit()

        commandBuilder.beginFrame()

        views.forEach { if (it.wasRequestedThisFrame()) it.prepare(thisFrameScene, commandBuilder) }

        buildLightGrids()
        commandBuilder.finishFramePreparation()
    }

    override fun endFrame()
    {
        if (initialized && thisFrameScene.hasAnyItems())
        {
            GpuProfiler.measure("fence context buffers")
            {
                instanceBuffer.markSubmittedDataInUse()
                cullingBuffer.markSubmittedDataInUse()
                boneBuffer.markGpuDataInUse()
                lightBuffer.markSubmittedDataInUse()
                commandBuilder.markSubmittedDataInUse()
                lightGrids.values.forEach { it.markSubmittedDataInUse() }
            }
        }
    }

    override fun destroy()
    {
        views.clear()
        lightGrids.values.forEach { it.destroy() }
        lightGrids.clear()

        if (!initialized) return

        instanceBuffer.destroy()
        cullingBuffer.destroy()
        boneBuffer.destroy()
        lightBuffer.destroy()
        commandBuilder.destroy()
        initialized = false
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: WorldRenderView> requestView(key: WorldRenderViewKey<T>): T
    {
        val existing = views.firstOrNull { it.viewId == key.viewId }
        if (existing != null)
        {
            require(key.type.isAssignableFrom(existing.javaClass)) { "View id ${key.viewId} is already used by ${existing.javaClass.simpleName}" }
            existing.lastFrameRequested = frameNumber
            return existing as T
        }

        val view = key.create()
        require(view.viewId == key.viewId) { "View key id ${key.viewId} created view with id ${view.viewId}" }
        require(key.type.isAssignableFrom(view.javaClass)) { "View key id ${key.viewId} created ${view.javaClass.simpleName}, expected ${key.type.simpleName}" }
        view.lastFrameRequested = frameNumber
        views += view
        return view
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: WorldRenderView> getView(key: WorldRenderViewKey<T>): T? = 
        views.firstOrNull { it.viewId == key.viewId && key.type.isAssignableFrom(it.javaClass) && it.wasRequestedThisFrame() } as T?

    override fun submitModel(engine: PulseEngine, model: Model, transform: Matrix4f, material: Material?, animationPose: AnimatedSkeletonPose?, visibilityMask: Int)
    {
        for (instance in model.meshInstances)
        {
            val material      = material ?: model.materials.getOrNull(instance.mesh.materialIndex)?.let { engine.asset.getOrNull(it.name) }
            val animatedPose  = animationPose?.getAnimatedMeshPose(instance.nodeName)
            val boneMatrices  = animatedPose?.boneMatrices ?: model.getBindPoseBoneMatrices(instance.nodeName)
            val cullingBounds = instance.mesh.animatedBounds?.takeIf { animatedPose != null } ?: instance.cullingBounds

            val transform = Matrix4f(transform).mul(instance.transform)

            nextFrameScene.addMesh(instance.mesh, material, transform, cullingBounds, boneMatrices, visibilityMask)
        }
    }

    override fun submitMesh(mesh: Mesh, material: Material?, transform: Matrix4f, cullingBounds: Aabb?, boneMatrices: Array<Matrix4f>?, visibilityMask: Int)
    {
        nextFrameScene.addMesh(mesh, material, transform, cullingBounds, boneMatrices, visibilityMask)
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

    override fun getLightBuffer() = lightBuffer
    
    override fun getClusteredLightGrid(camera: Camera) = lightGrids[camera]

    private fun buildLightGrids()
    {
        activeLightGridCameras.clear()

        views.forEach()
        {
            if (it.wasRequestedThisFrame() && it is CameraRenderStateProvider)
            {
                it.cameraStates.forEach { state ->
                    activeLightGridCameras += state.camera
                    lightGrids.getOrPut(state.camera) { ClusteredLightGrid() }.buildAndSubmit(state, thisFrameScene)
                }
            }
        }

        val iterator = lightGrids.iterator()
        while (iterator.hasNext())
        {
            val entry = iterator.next()
            if (entry.key !in activeLightGridCameras)
            {
                entry.value.destroy()
                iterator.remove()
            }
        }
    }

    private fun DynamicList<WorldRenderItem>.addToBuffers() =
        forEach { item ->
            val boneOffset = boneBuffer.addBoneMatricesAndGetOffset(item.boneMatrices)
            item.gpuInstanceIndex = instanceBuffer.addItem(item, boneOffset)
            cullingBuffer.addItem(item)
        }

    private fun WorldRenderView.wasRequestedThisFrame() = (lastFrameRequested == frameNumber)
}