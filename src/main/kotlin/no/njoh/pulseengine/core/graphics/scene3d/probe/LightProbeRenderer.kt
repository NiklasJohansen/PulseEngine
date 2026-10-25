package no.njoh.pulseengine.core.graphics.scene3d.probe

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.FullscreenPass
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.gpu.buffer.FrameBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.LightProbeBufferObject
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.CubeMap
import no.njoh.pulseengine.core.graphics.gpu.texture.CubeMapArray
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureHandle
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContextInternal
import no.njoh.pulseengine.core.graphics.scene3d.probe.LightProbeRenderer.Phase.*
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ModelRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.SkyboxRenderer
import no.njoh.pulseengine.core.graphics.scene3d.view.LightProbeRenderView
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewDeclarer
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderViewKey
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import no.njoh.pulseengine.core.graphics.util.GpuProfiler.measure
import no.njoh.pulseengine.core.shared.datastructures.DynamicList
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import org.joml.Vector3f
import org.lwjgl.opengl.GL33.*
import kotlin.math.abs

/** 
 * Updates probes in round-robin order, budgeting capture, mip generation, filtering and publication steps.
 * A zero budget only updates unready or changed probes, at one step per frame.
 */
class LightProbeRenderer(
    val sourceSurfaceName: String,
    val targetSurfaceNames: List<String> = listOf(sourceSurfaceName),
    val resolution: Int = 128,
    override val order: Int = 0,
) : Renderer(), RenderViewDeclarer {

    private val modelRenderer = ModelRenderer()

    private var submittedCount  = 0
    private val submittedProbes = DynamicList<LightProbe>(8)

    private val probeStorage               = DynamicList<LightProbe>(8)
    private val probeStorageFreeIndices    = IntArrayList()
    private val probeStorageIndexByProbeId = Long2IntOpenHashMap().apply { defaultReturnValue(-1) }

    private val probeData                  = LightProbeData()
    private val currentCaptureProbe        = LightProbe()
    private val tmpCapturePos              = Vector3f()
    private var currentPhase               = CAPTURE_FACES
    private var currentProbeIndexToCapture = -1
    private var lastProbeIndexCaptured     = -1
    private var nextFaceIndexToCapture     = 0
    private var nextMipIndexToFilter       = 0
    private var stepsPerFrame              = 1
    private var submittedStepsPerFrame     = 1

    private val updatePosition   = Vector3f()
    private val tmpLocalPos      = Vector3f()
    private val liveProbeIndices = IntArrayList()
    private var probeDistances   = FloatArray(8)
    private var limitLiveProbes  = false
    private var maxLiveProbes    = 0

    private val captureCubeMap        = CubeMap(resolution)
    private val specularCubeMap       = CubeMap(resolution)
    private val diffuseCubeMap        = CubeMap(DIFFUSE_RESOLUTION, filter = LINEAR, mipLevels = 1)
    private val specularProbeCubeMaps = CubeMapArray(capacity = 8, resolution)
    private val diffuseProbeCubeMaps  = CubeMapArray(capacity = 8, DIFFUSE_RESOLUTION, filter = LINEAR, mipLevels = 1)
    private val probeGpuBuffer        = LightProbeBufferObject()

    private lateinit var viewKey: RenderViewKey<LightProbeRenderView>
    private lateinit var skyProgram: ShaderProgram
    private lateinit var skyPass: FullscreenPass
    private lateinit var filterPass: FullscreenPass
    private lateinit var filterProgram: ShaderProgram
    private lateinit var filterFbo: FrameBufferObject

    init
    {
        require(resolution in 32..1024 && resolution and (resolution - 1) == 0) { "Probe resolution must be a power of two between 32 and 1024" }
    }

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        GlCapabilities.requireFullGraphics("Light probes")

        viewKey = RenderViewKey(surface.viewGroup) { LightProbeRenderView() }
        modelRenderer.init(engine, surface)
        probeGpuBuffer.init()

        if (!this::filterProgram.isInitialized)
        {
            val vertexShader = engine.asset.loadNow(VertexShader("/pulseengine/shaders/utils/ibl.vert"))
  
            filterProgram = ShaderProgram.create(vertexShader, engine.asset.loadNow(FragmentShader("/pulseengine/shaders/utils/probe_filter.frag")))
            skyProgram    = ShaderProgram.create(vertexShader, engine.asset.loadNow(FragmentShader("/pulseengine/shaders/utils/probe_sky.frag")))
            filterPass    = FullscreenPass(filterProgram)
            skyPass       = FullscreenPass(skyProgram)
        }

        filterPass.init()
        skyPass.init()

        if (this::filterFbo.isInitialized)
            filterFbo.destroy()
        filterFbo = FrameBufferObject.createEmpty()

        captureCubeMap.destroy()
        captureCubeMap.init()

        specularCubeMap.destroy()
        specularCubeMap.init()

        specularProbeCubeMaps.destroy()
        specularProbeCubeMaps.init()

        diffuseCubeMap.destroy()
        diffuseCubeMap.init()

        diffuseProbeCubeMaps.destroy()
        diffuseProbeCubeMaps.init()
    }

    override fun onRenderTargetRecreated(engine: PulseEngineInternal)
    {
        cancelCapture()
        probeStorage.forEach { it.ready = false }
        publishProbeData(engine)
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        // Mark all stored probes as inactive, then reactivate the ones that were submitted this frame
        probeStorage.forEach { it.active = false }
        for (i in 0 until submittedCount)
        {
            val index = probeStorageIndexByProbeId[submittedProbes[i].id]
            if (index >= 0) probeStorage[index].active = true
        }

        // Release the stored probes that were not reactivated this frame, and free their indices for reuse
        probeStorageFreeIndices.clear()
        probeStorage.forEachIndexed { index, probe ->
            if (!probe.active)
            {
                probeStorageIndexByProbeId.remove(probe.id)
                probeStorageFreeIndices.add(index)
                liveProbeIndices.rem(index)
                probe.id = -1L
                probe.ready = false
            }
        }

        // Update the probe storage with the submitted probe data, creating new persistent probes for any new submissions
        for (i in 0 until submittedCount)
        {
            val submittedProbe = submittedProbes[i]
            var index = probeStorageIndexByProbeId[submittedProbe.id]
            val probe = if (index < 0)
            {
                index = if (!probeStorageFreeIndices.isEmpty) probeStorageFreeIndices.removeInt(probeStorageFreeIndices.lastIndex) else probeStorage.size
                probeStorageIndexByProbeId[submittedProbe.id] = index
                probeStorage.getOrAdd(index) { LightProbe() }
            }
            else probeStorage[index]
  
            probe.setFrom(submittedProbe)
        }

        specularProbeCubeMaps.ensureCapacity(probeStorage.size)
        diffuseProbeCubeMaps.ensureCapacity(probeStorage.size)
        stepsPerFrame = submittedStepsPerFrame
        selectLiveProbes()

        // Keep capturing from the starting position when a probe moves; only cancel removed/replaced or paused probes.
        if (currentProbeIndexToCapture >= 0)
        {
            val probe = probeStorage[currentProbeIndexToCapture]
            if (!probe.active || 
                (probe.specularIntensity <= 0f && probe.diffuseIntensity <= 0f) || 
                currentCaptureProbe.id != probe.id ||
                (stepsPerFrame == 0 && probe.ready && !probe.needsUpdate)
            ) {
                cancelCapture()
            }
        }

        publishProbeData(engine)
    }
    
    fun beginSubmission(stepsPerFrame: Int = 1, updatePosition: Vector3f? = null, maxLiveProbes: Int = 4)
    {
        this.submittedCount         = 0
        this.submittedStepsPerFrame = stepsPerFrame.coerceAtLeast(0)
        this.maxLiveProbes          = maxLiveProbes.coerceAtLeast(0)
        this.limitLiveProbes        = updatePosition != null && updatePosition.isFinite

        if (updatePosition != null) 
            this.updatePosition.set(updatePosition)
    }

    fun submit(probe: LightProbe)
    {
        submittedProbes.getOrAdd(submittedCount++) { LightProbe() }.setFrom(probe)
    }

    override fun declareRenderViews(engine: PulseEngineInternal, surface: SurfaceInternal, context: SceneRenderContextInternal)
    {
        if (engine.gfx.getSurface(sourceSurfaceName)?.getRenderer<ModelRenderer>() == null) 
            return

        if (currentProbeIndexToCapture < 0)
        {
            for (offset in 1..probeStorage.size)
            {
                val index = (lastProbeIndexCaptured + offset) % probeStorage.size
                val probe = probeStorage[index]
                if (!canUpdateProbe(index))
                    continue

                currentProbeIndexToCapture = index
                nextFaceIndexToCapture = 0
                nextMipIndexToFilter = 0
                currentPhase = CAPTURE_FACES
                currentCaptureProbe.setFrom(probe)
                break
            }
        }

        if (currentProbeIndexToCapture < 0) return

        if (currentPhase == CAPTURE_FACES)
        {
            val view = context.requestView(viewKey)
            view.setFor(
                probe = currentCaptureProbe,
                resolution = resolution,
                firstFace = nextFaceIndexToCapture,
                faceCount = minOf(maxOf(stepsPerFrame, 1), 6 - nextFaceIndexToCapture)
            )
            view.cameraStates.forEach { context.requestClusteredLightGrid(it) }
        }

        increaseBatchSize()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0 || currentProbeIndexToCapture < 0) return

        measure("probe_render", label = { "Update probe: #" plus currentCaptureProbe.id })
        {
            val seamless = glIsEnabled(GL_TEXTURE_CUBE_MAP_SEAMLESS)
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
            glDisable(GL_SCISSOR_TEST)
            glDisable(GL_BLEND)
            glColorMask(true, true, true, true)
            try
            {
                var remainingSteps = maxOf(stepsPerFrame, 1)
                while (remainingSteps > 0 && currentProbeIndexToCapture >= 0)
                {
                    val stepsUsed = when (currentPhase)
                    {
                        CAPTURE_FACES   -> captureFaces(engine, surface)
                        GENERATE_MIPS   -> { generateCubeMapMips(); 1 }
                        FILTER_SPECULAR -> { filterCubeMap(diffuse = false); 1 }
                        FILTER_DIFFUSE  -> { filterCubeMap(diffuse = true); 1 }
                        PUBLISH         -> { publishCapture(engine); 1 }
                    }
                    if (stepsUsed == 0) break
                    remainingSteps -= stepsUsed
                }
            }
            finally
            {
                surface.renderTarget.begin()
                glViewport(0, 0, surface.config.renderWidth, surface.config.renderHeight)
                glDepthMask(true)
                glEnable(GL_DEPTH_TEST)
                glEnable(GL_CULL_FACE)
                if (!seamless) glDisable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
            }
        }
    }

    private fun captureFaces(engine: PulseEngineInternal, surface: SurfaceInternal): Int
    {
        val view            = engine.gfx.sceneContext.getView(viewKey) ?: return 0
        val sourceSurface   = engine.gfx.getSurface(sourceSurfaceName) ?: return 0
        val sourceRenderer  = sourceSurface.getRenderer<ModelRenderer>() ?: return 0
        val sky             = sourceSurface.getRenderer<SkyboxRenderer>()
        val skyTexture      = engine.asset.getOrNull<Texture>(sky?.envTexture ?: "")
        val skyTextureArray = engine.gfx.textureBank.getTextureArray(skyTexture)
        val clearColor      = sourceRenderer.envSRgbColor.asLinear()

        modelRenderer.useDefaultLighting          = sourceRenderer.useDefaultLighting
        modelRenderer.iblDiffuseTexture           = sourceRenderer.iblDiffuseTexture
        modelRenderer.iblSpecularTexture          = sourceRenderer.iblSpecularTexture
        modelRenderer.iblIntensity                = sourceRenderer.iblIntensity
        modelRenderer.sunRadius                   = sourceRenderer.sunRadius
        modelRenderer.sunShadowMapSurfaceName     = sourceRenderer.sunShadowMapSurfaceName
        modelRenderer.localShadowAtlasSurfaceName = sourceRenderer.localShadowAtlasSurfaceName

        modelRenderer.sunSRgbColor.setFrom(sourceRenderer.sunSRgbColor)
        modelRenderer.sunIntensity = sourceRenderer.sunIntensity
        modelRenderer.envSRgbColor.setFrom(sourceRenderer.envSRgbColor)

        for (faceIndex in view.firstFace until view.firstFace + view.faceCount)
        {
            measure("probe_face", label = { "Capture face: #" plus faceIndex })
            {
                surface.renderTarget.begin()

                glViewport(0, 0, resolution, resolution)
                glDepthMask(true)
                glClearColor(clearColor.red * sourceRenderer.iblIntensity, clearColor.green * sourceRenderer.iblIntensity, clearColor.blue * sourceRenderer.iblIntensity, 1f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

                if (skyTexture != null && skyTextureArray != null)
                {
                    measure("probe_sky", label = { "Draw skybox" })
                    {
                        glDisable(GL_DEPTH_TEST)
                        glDisable(GL_CULL_FACE)

                        skyProgram.bind()
                        skyProgram.setUniformSamplerArray("skyTexture", skyTextureArray)
                        skyProgram.setUniform("skyDescriptor", skyTexture.handle.textureArrayLayer.toFloat(), skyTexture.uMax, skyTexture.vMax)
                        skyProgram.setUniform("brightness", sky!!.brightness)
                        skyProgram.setUniform("face", faceIndex)
                        skyPass.draw()
                    }
                }

                val viewIndex = faceIndex - view.firstFace
                modelRenderer.renderLightProbeFace(engine, surface, view.cameraViews[viewIndex], view.cameraStates[viewIndex]) 

                measure("probe_copy", label = { "Copy face to cubemap" }) 
                { 
                    surface.renderTarget.getFbo().copyToCubeFace(captureCubeMap, faceIndex) 
                }
            }
        }

        nextFaceIndexToCapture += view.faceCount
        if (nextFaceIndexToCapture == 6) 
            currentPhase = GENERATE_MIPS
        
        return view.faceCount
    }

    private fun generateCubeMapMips() = measure(
        id = "probe_generate_mips", 
        label = { "Generate cubemap mips (mip: " plus currentCaptureProbe.id plus ")" }
    ) {
        captureCubeMap.generateMips()
        currentPhase = FILTER_SPECULAR
    }
    
    private fun filterCubeMap(diffuse: Boolean = false) = measure(
        id = if (diffuse) "probe_diffuse_filter" else "probe_specular_filter",
        label = { if (diffuse) "Filter diffuse cubemap" else "Filter specular cubemap (mip: " plus nextMipIndexToFilter plus ")" }
    ) {
        val target = if (diffuse) diffuseCubeMap else specularCubeMap
        val mip = if (diffuse) 0 else nextMipIndexToFilter
        val size = target.resolution shr mip

        filterFbo.bind()

        glViewport(0, 0, size, size)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_BLEND)
        glDisable(GL_CULL_FACE)

        filterProgram.bind()
        filterProgram.setUniform("resolution", resolution.toFloat())
        filterProgram.setUniform("diffuse", diffuse)
        filterProgram.setUniform("roughness", mip.toFloat() / (specularCubeMap.mipLevels - 1))
        filterProgram.setUniformSampler("captureTexture", captureCubeMap)

        for (face in 0 until 6)
        {
            filterFbo.attachCubeFace(target, face, mip)
            filterFbo.checkStatus()
            filterProgram.setUniform("face", face)
            filterPass.draw()
        }

        if (diffuse)
        {
            currentPhase = PUBLISH
        }
        else if (++nextMipIndexToFilter == specularCubeMap.mipLevels)
        {
            currentPhase = FILTER_DIFFUSE
        }
    }

    private fun publishCapture(engine: PulseEngineInternal)
    {
        measure("probe_copy_to_array", label = { "Copy filtered cubemap to array" })
        {
            specularProbeCubeMaps.copyFrom(source = specularCubeMap, targetIndex = currentProbeIndexToCapture) 
            diffuseProbeCubeMaps.copyFrom(source = diffuseCubeMap, targetIndex = currentProbeIndexToCapture)
        }

        val probe = probeStorage[currentProbeIndexToCapture]
        val capturePosition = currentCaptureProbe.getCapturePosition(tmpCapturePos)
        val needsPublication = !probe.ready || probe.capturedPosition != capturePosition
        
        probe.capturedPosition.set(capturePosition)
        probe.ready = true
        probe.needsUpdate = !currentCaptureProbe.hasSameCapture(probe)

        lastProbeIndexCaptured = currentProbeIndexToCapture
        cancelCapture()

        // Publish the capture origin together with its cubemap
        if (needsPublication) publishProbeData(engine)
    }

    private fun cancelCapture()
    {
        currentProbeIndexToCapture = -1
        nextFaceIndexToCapture = 0
        nextMipIndexToFilter = 0
        currentPhase = CAPTURE_FACES
    }

    private fun publishProbeData(engine: PulseEngineInternal)
    {
        probeGpuBuffer.clear()

        for (index in 0 until probeStorage.size)
        {
            val probe = probeStorage[index]
            if (!probe.active || !probe.ready || (probe.specularIntensity <= 0f && probe.diffuseIntensity <= 0f))
                continue

            probeGpuBuffer.addProbe(probe, index)
        }

        probeGpuBuffer.submit()

        probeData.specularCubeMapTextureArrayHandle = specularProbeCubeMaps.handle
        probeData.diffuseCubeMapTextureArrayHandle  = diffuseProbeCubeMaps.handle
        probeData.specularCubeMapMipCount           = specularProbeCubeMaps.mipLevels
        probeData.probeCount                        = probeGpuBuffer.probeCount
        probeData.probeGpuBuffer                    = probeGpuBuffer

        targetSurfaceNames.forEachFast()
        {
            engine.gfx.getSurface(it)?.getRenderer<ModelRenderer>()?.lightProbeData = probeData
        }
    }

    private fun selectLiveProbes()
    {
        if (!limitLiveProbes || maxLiveProbes == 0)
        {
            liveProbeIndices.clear()
            return
        }

        if (probeDistances.size < probeStorage.size)
            probeDistances = FloatArray(probeStorage.size)

        for (index in 0 until probeStorage.size)
        {
            val probe = probeStorage[index]
            tmpLocalPos.set(updatePosition).sub(probe.position)
            probe.rotation.transformInverse(tmpLocalPos)

            val dx = (abs(tmpLocalPos.x) - probe.influenceSize.x * 0.5f).coerceAtLeast(0f)
            val dy = (abs(tmpLocalPos.y) - probe.influenceSize.y * 0.5f).coerceAtLeast(0f)
            val dz = (abs(tmpLocalPos.z) - probe.influenceSize.z * 0.5f).coerceAtLeast(0f)

            // A challenger must be about 10% closer to replace a live probe.
            probeDistances[index] = (dx * dx + dy * dy + dz * dz) * if (liveProbeIndices.contains(index)) 0.81f else 1f
        }

        liveProbeIndices.clear()
        for (probeIndex in 0 until probeStorage.size)
        {
            val probe = probeStorage[probeIndex]
            if (!probe.active || (probe.specularIntensity <= 0f && probe.diffuseIntensity <= 0f))
                continue

            var rank = 0
            val probeDist = probeDistances[probeIndex]
            while (rank < liveProbeIndices.size)
            {
                val other = liveProbeIndices.getInt(rank)
                val otherDist = probeDistances[other]
                if (otherDist > probeDist || (otherDist == probeDist && probeStorage[other].id > probe.id))
                    break
                rank++
            }

            if (rank >= maxLiveProbes)
                continue

            liveProbeIndices.add(rank, probeIndex)
            if (liveProbeIndices.size > maxLiveProbes)
                liveProbeIndices.removeInt(liveProbeIndices.lastIndex)
        }
    }

    private fun canUpdateProbe(index: Int): Boolean
    {
        val probe = probeStorage[index]
        if (!probe.active || (probe.specularIntensity <= 0f && probe.diffuseIntensity <= 0f))
            return false // No need to update inactive or zero-intensity probes

        if (!probe.ready || probe.needsUpdate)
            return true // Capture new or changed probes even if stepsPerFrame is zero

        if (stepsPerFrame > 0 && (!limitLiveProbes || liveProbeIndices.contains(index)))
            return true // Update live probes if stepsPerFrame is positive

        return false
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        probeData.probeCount = 0
        probeData.specularCubeMapTextureArrayHandle = TextureHandle.NONE
        probeData.diffuseCubeMapTextureArrayHandle = TextureHandle.NONE
        probeData.probeGpuBuffer = null
        
        targetSurfaceNames.forEachFast()
        {
            val renderer = engine.gfx.getSurface(it)?.getRenderer<ModelRenderer>()
            if (renderer?.lightProbeData === probeData)
                renderer.lightProbeData = null
        }

        modelRenderer.destroy(engine)
        filterPass.destroy()
        skyPass.destroy()
        filterProgram.destroy()
        skyProgram.destroy()
        filterFbo.destroy()

        cancelCapture()
        captureCubeMap.destroy()
        specularCubeMap.destroy()
        specularProbeCubeMaps.destroy()
        diffuseCubeMap.destroy()
        diffuseProbeCubeMaps.destroy()
        probeGpuBuffer.destroy()
    }

    private enum class Phase { CAPTURE_FACES, GENERATE_MIPS, FILTER_SPECULAR, FILTER_DIFFUSE, PUBLISH }

    companion object
    {
        private const val DIFFUSE_RESOLUTION = 16
    }
}