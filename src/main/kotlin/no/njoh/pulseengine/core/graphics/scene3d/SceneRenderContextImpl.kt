package no.njoh.pulseengine.core.graphics.scene3d

import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.*
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderState
import no.njoh.pulseengine.core.graphics.gpu.buffer.BoneBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.CullingBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.LightBufferObject
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.scene3d.view.ModelLodResolver
import no.njoh.pulseengine.core.graphics.scene3d.lighting.ClusteredLightGrid
import no.njoh.pulseengine.core.graphics.scene3d.lighting.ClusteredDecalGrid
import no.njoh.pulseengine.core.graphics.scene3d.lighting.ClusteredGrid
import no.njoh.pulseengine.core.graphics.gpu.buffer.DecalBufferObject
import no.njoh.pulseengine.core.graphics.scene3d.shadow.LocalShadowAtlas
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawCommandBuilder
import no.njoh.pulseengine.core.graphics.scene3d.submission.DecalColorBlendMode
import no.njoh.pulseengine.core.graphics.scene3d.submission.DecalNormalBlendMode
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.shared.primitives.Mobility
import no.njoh.pulseengine.core.graphics.scene3d.view.CameraRenderStateProvider
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderView
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewDeclarer
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewKey
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.shared.datastructures.DynamicList
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachInstance
import no.njoh.pulseengine.core.shared.utils.retainEntries
import org.joml.Matrix4f
import org.joml.Vector3f

class SceneRenderContextImpl : SceneRenderContextInternal()
{
    private var thisFrameScene = RenderScene()
    private var nextFrameScene = RenderScene()

    private val views = Object2ObjectOpenHashMap<RenderViewKey<*>, RenderView>()

    private val instanceBuffer = InstanceBufferObject()
    private val cullingBuffer  = CullingBufferObject()
    private val boneBuffer     = BoneBufferObject()
    private val lightBuffer    = LightBufferObject()
    private val decalBuffer    = DecalBufferObject()

    private val drawCommandBuilder    = DrawCommandBuilder(instanceBuffer, cullingBuffer, boneBuffer)
    private val modelLodResolver      = ModelLodResolver()
    private val localShadowAtlas      = LocalShadowAtlas()
    private val clusteredLightGrids   = Object2ObjectOpenHashMap<CameraRenderState, ClusteredLightGrid>()
    private val clusteredDecalGrids   = Object2ObjectOpenHashMap<CameraRenderState, ClusteredDecalGrid>()
    private val clusteredGridRequests = ObjectOpenHashSet<CameraRenderState>()
    private val renderIdOverrides     = LongArrayList()

    private var frameNumber           = 0
    private var initialized           = false
    private var lastFrameHadDrawItems = false

    override fun initFrame()
    {
        renderIdOverrides.clear()
        frameNumber++

        nextFrameScene = thisFrameScene.also { thisFrameScene = nextFrameScene }
        nextFrameScene.clear()
        thisFrameScene.captureSubmittedSnapshot()

        clusteredGridRequests.clear()

        views.forEach { it.value.beginFrame() }
    }

    override fun buildFrame(engine: PulseEngineInternal)
    {
        // Cache unrequested views for 30 frames to avoid reallocating them between uses
        views.entries.removeIf { it.value.lastFrameRequested < frameNumber - VIEW_CACHE_FRAMES }

        if (!thisFrameScene.hasSubmittedGeometry && !lastFrameHadDrawItems)
            return // This and last frame had no items, skip frame. If the last frame had items, do a pass to clear everything.

        if (!initialized)
        {
            GlCapabilities.requireFullGraphics("3D scene rendering")
            instanceBuffer.init()
            cullingBuffer.init()
            boneBuffer.init()
            lightBuffer.init()
            decalBuffer.init()
            drawCommandBuilder.init(engine)
            initialized = true
        }

        // Declare world views
        engine.gfx.getAllSurfaces().forEachFast { surface ->
            surface.getAllRenderers().forEachInstance<RenderViewDeclarer>()
            {
                it.declareRenderViews(engine, surface, this)
            }
        }

        if (thisFrameScene.hasPendingModels)
        {
            modelLodResolver.beginFrame(views.values, frameNumber, thisFrameScene.pendingModelCount)
            thisFrameScene.selectPendingModelLods(engine, modelLodResolver)
        }

        if (!thisFrameScene.hasDrawItems && !lastFrameHadDrawItems)
            return

        // Clear buffers for new frame
        instanceBuffer.clear()
        cullingBuffer.clear()
        boneBuffer.clear()
        lightBuffer.clear()
        decalBuffer.clear()

        // Reserve buffer capacity
        val itemCount = thisFrameScene.drawItemCount
        instanceBuffer.reserveItemCapacity(itemCount)
        cullingBuffer.reserveItemCapacity(itemCount)

        // Populate GPU buffers with draw items
        populateGpuBuffers(thisFrameScene.opaqueItems)
        populateGpuBuffers(thisFrameScene.maskedItems)
        populateGpuBuffers(thisFrameScene.blendedItems)

        // Prepare lighting
        localShadowAtlas.update(thisFrameScene, getCameraPosition())
        localShadowAtlas.getActiveShadowFaces().forEach { face -> lightBuffer.addShadowFace(face) }
        thisFrameScene.localLights.forEach { lightBuffer.addLight(it) }
        buildRequestedClusteredLightGrids()

        // Prepare decals
        thisFrameScene.sortDecals()
        thisFrameScene.decals.forEach { decalBuffer.addDecal(it) }
        buildRequestedClusteredDecalGrids()

        // Submit buffers to GPU
        measure("Submit context buffers")
        {
            instanceBuffer.submit()
            cullingBuffer.submit()
            boneBuffer.submit()
            lightBuffer.submit()
            decalBuffer.submit()
            clusteredDecalGrids.forEach { (_, grid) -> if (grid.wasRequested()) grid.submit() }
            clusteredLightGrids.forEach { (_, grid) -> if (grid.wasRequested()) grid.submit() }
        }

        // Prepare views for rendering
        drawCommandBuilder.beginFrame()
        views.forEach { (_, view) -> if (view.wasRequested()) view.prepare(thisFrameScene, drawCommandBuilder) }
        drawCommandBuilder.finishFramePreparation(engine)

        lastFrameHadDrawItems = thisFrameScene.hasDrawItems
    }

    override fun endFrame()
    {
        if (initialized && thisFrameScene.hasDrawItems)
        {
            measure("Fence context buffers")
            {
                instanceBuffer.markSubmittedDataInUse()
                cullingBuffer.markSubmittedDataInUse()
                boneBuffer.markGpuDataInUse()
                lightBuffer.markSubmittedDataInUse()
                decalBuffer.markSubmittedDataInUse()
                drawCommandBuilder.markSubmittedDataInUse()
                clusteredLightGrids.forEach { (_, grid) -> if (grid.wasRequested()) grid.markSubmittedDataInUse() }
                clusteredDecalGrids.forEach { (_, grid) -> if (grid.wasRequested()) grid.markSubmittedDataInUse() }
            }
        }
    }

    override fun destroy()
    {
        views.clear()
        clusteredLightGrids.forEach { (_, grid) -> grid.destroy() }
        clusteredLightGrids.clear()
        clusteredDecalGrids.forEach { (_, grid) -> grid.destroy() }
        clusteredDecalGrids.clear()

        if (!initialized) return

        instanceBuffer.destroy()
        cullingBuffer.destroy()
        boneBuffer.destroy()
        lightBuffer.destroy()
        decalBuffer.destroy()
        drawCommandBuilder.destroy()
        initialized = false
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: RenderView> requestView(key: RenderViewKey<T>): T
    {
        val existing = views[key]
        if (existing != null)
        {
            require(key.type.isAssignableFrom(existing.javaClass)) { "View $key is already used by ${existing.javaClass.simpleName}" }
            existing.lastFrameRequested = frameNumber
            return existing as T
        }

        val view = key.create()
        require(key.type.isAssignableFrom(view.javaClass)) { "View key $key created ${view.javaClass.simpleName}, expected ${key.type.simpleName}" }
        view.lastFrameRequested = frameNumber
        views[key] = view
        return view
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: RenderView> getView(key: RenderViewKey<T>): T? = 
        views[key]?.takeIf { it.wasRequested() && key.type.isAssignableFrom(it.javaClass) } as T?

    override fun submitSpotLight(
        position: Vector3f,
        direction: Vector3f,
        range: Float,
        sRgbColor: Color,
        intensity: Float,
        innerConeAngle: Float,
        outerConeAngle: Float,
        sourceRadius: Float,
        shadowEnabled: Boolean,
        shadowResolution: Int,
        shadowNearPlane: Float,
        shadowBias: Float,
        shadowImportance: Float,
        shadowId: Long,
        contactShadowLength: Float
    ) {
        nextFrameScene.addLight(
            position, direction, range, sourceRadius, sRgbColor, intensity, innerConeAngle, outerConeAngle, 
            shadowEnabled, shadowResolution, shadowNearPlane, shadowBias, shadowImportance, shadowId, contactShadowLength
        )
    }

    override fun submitPointLight(
        position: Vector3f,
        range: Float,
        sRgbColor: Color,
        intensity: Float,
        sourceRadius: Float,
        shadowEnabled: Boolean,
        shadowResolution: Int,
        shadowNearPlane: Float,
        shadowBias: Float,
        shadowImportance: Float,
        shadowId: Long,
        contactShadowLength: Float
    ) {
        nextFrameScene.addLight(
            position, null, range, sourceRadius, sRgbColor, intensity, 180f, 180f, shadowEnabled, 
            shadowResolution, shadowNearPlane, shadowBias, shadowImportance, shadowId, contactShadowLength
        )
    }

    override fun submitMesh(
        mesh: Mesh,
        material: Material?,
        transform: Matrix4f,
        cullingBounds: Aabb?,
        boneMatrices: Array<Matrix4f>?,
        renderPassMask: RenderPassMask,
        mobility: Mobility,
        renderId: Long
    ) {
        nextFrameScene.addMesh(mesh, material, transform, cullingBounds, boneMatrices, renderPassMask, mobility, resolveRenderId(renderId))
    }

    override fun submitModel(
        engine: PulseEngine,
        model: Model,
        transform: Matrix4f,
        material: Material?,
        animationPose: AnimatedSkeletonPose?,
        renderPassMask: RenderPassMask,
        mobility: Mobility,
        lodThresholds: FloatArray?,
        lodHysteresis: Float,
        lodKey: Long,
        renderId: Long
    ) {
        val resolvedRenderId = resolveRenderId(renderId)
        if (lodThresholds == null || lodThresholds.isEmpty() || model.hasBones || model.lodLevels.size < 2)
        {
            nextFrameScene.addModelMeshes(engine, model, transform, material, animationPose, lodLevel = 0, renderPassMask, mobility, resolvedRenderId)
            return
        }

        val resolvedLodKey = when
        {
            lodKey != 0L -> lodKey
            renderId >= 0L -> renderId
            resolvedRenderId >= 0L -> resolvedRenderId
            else -> 0L
        }

        nextFrameScene.addModel(model, transform, material, renderPassMask, mobility, lodThresholds, lodHysteresis, resolvedLodKey, resolvedRenderId)
    }

    override fun submitDecal(
        material: Material,
        transform: Matrix4f,
        opacity: Float,
        colorBlend: Float,
        colorBlendMode: DecalColorBlendMode,
        normalBlend: Float,
        normalBlendMode: DecalNormalBlendMode,
        roughnessBlend: Float,
        metallicBlend: Float,
        heightBlend: Float,
        edgeFade: Float,
        fadeStartAngleDegrees: Float,
        fadeStartDistance: Float,
        fadeEndDistance: Float,
        targetLayerMask: Int,
        sortOrder: Int
    ) {
        if (opacity <= 0f || targetLayerMask == 0) return

        nextFrameScene.addDecal(
            material, transform, opacity, colorBlend, colorBlendMode, normalBlend, normalBlendMode, roughnessBlend, metallicBlend, 
            heightBlend, edgeFade, fadeStartAngleDegrees, fadeStartDistance, fadeEndDistance, targetLayerMask, sortOrder
        )
    }

    override fun pushRenderIdOverride(renderId: Long)
    {
        renderIdOverrides.add(renderId)
    }

    override fun popRenderIdOverride()
    {
        renderIdOverrides.removeLong(renderIdOverrides.size - 1)
    }

    override fun getSubmittedSceneSnapshot() = thisFrameScene.submittedSnapshot

    override fun getDecalBuffer() = decalBuffer

    override fun getClusteredDecalGrid(state: CameraRenderState) = clusteredDecalGrids[state]?.takeIf { it.wasRequested() && it.enabled }

    override fun getLocalShadowAtlas() = localShadowAtlas

    override fun getBoneBuffer() = boneBuffer

    override fun getLightBuffer() = lightBuffer

    override fun getInstanceBuffer() = instanceBuffer

    override fun getClusteredLightGrid(state: CameraRenderState) = clusteredLightGrids[state]?.takeIf { it.wasRequested() }
    
    override fun requestClusteredGrids(state: CameraRenderState)
    {
        clusteredGridRequests += state
    }

    private fun buildRequestedClusteredLightGrids()
    {
        if (!thisFrameScene.hasDrawItems)
        {
            clusteredLightGrids.forEach { (_, grid) -> grid.destroy() }
            clusteredLightGrids.clear()
            return
        }

        for (state in clusteredGridRequests)
        {
            val grid = clusteredLightGrids.getOrPut(state) { ClusteredLightGrid() }
            grid.build(state, thisFrameScene.localLights)
            grid.lastFrameRequested = frameNumber
        }

        clusteredLightGrids.retainEntries { _, grid ->
            val retain = grid.lastFrameRequested >= frameNumber - VIEW_CACHE_FRAMES
            if (!retain) grid.destroy()
            retain
        }
    }

    private fun buildRequestedClusteredDecalGrids()
    {
        if (thisFrameScene.hasDrawItems && thisFrameScene.decals.isNotEmpty())
        {
            for (state in clusteredGridRequests)
            {
                val grid = clusteredDecalGrids.getOrPut(state) { ClusteredDecalGrid() }
                grid.build(state, thisFrameScene.decals)
                grid.lastFrameRequested = frameNumber
            }
        }

        clusteredDecalGrids.retainEntries { _, grid ->
            val retain = thisFrameScene.hasDrawItems && grid.lastFrameRequested >= frameNumber - VIEW_CACHE_FRAMES
            if (!retain) grid.destroy()
            retain
        }
    }

    private fun populateGpuBuffers(items: DynamicList<RenderItem>) =
        items.forEach() 
        {
            val boneOffset = boneBuffer.addBoneMatricesAndGetOffset(it.boneMatrices)
            it.gpuInstanceIndex = instanceBuffer.addItem(it, boneOffset)
            cullingBuffer.addItem(it)
        }

    private fun getCameraPosition(): Vector3f? =
        views.firstNotNullOfOrNull { (_, view) -> if (view.wasRequested() && view is CameraRenderStateProvider) view.shadowReferencePosition else null }

    private fun resolveRenderId(renderId: Long) = if (renderIdOverrides.isEmpty) renderId else renderIdOverrides.getLong(renderIdOverrides.size - 1)

    private fun RenderView.wasRequested() = (this.lastFrameRequested == frameNumber)

    private fun ClusteredGrid.wasRequested() = (this.lastFrameRequested == frameNumber)

    companion object
    {
        private const val VIEW_CACHE_FRAMES = 30
    }
}