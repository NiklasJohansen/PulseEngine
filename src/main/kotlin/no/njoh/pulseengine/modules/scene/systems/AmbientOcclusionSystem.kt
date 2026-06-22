package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.scene3d.renderers.GtaoRenderer
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

@Name("Ambient Occlusion")
class AmbientOcclusionSystem : SceneSystem()
{
    // AO Settings
    @Prop(i=0,  min=0f)         var intensity       = 1.5f
    @Prop(i=1,  min=1f)         var slices          = 3
    @Prop(i=2,  min=1f)         var maxSteps        = 10
    @Prop(i=3,  min=0.0001f)    var radiusMeters    = 1.5f
    @Prop(i=5,  min=0.0001f)    var maxRadiusPixels = 600f
    @Prop(i=6,  min=0.0001f)    var thicknessMeters = 0.01f

    // Denoising
    @Prop(i=7,  min=0f)         var denoisePasses           = 1
    @Prop(i=8,  min=0f)         var denoiseRadius           = 5
    @Prop(i=9,  min=0f)         var denoiseBlurWidth        = 3f
    @Prop(i=10, min=0f, max=1f) var denoiseEdgePreservation = 0.9f

    // Down/up sampling
    @Prop(i=11, min=1f)         var downsampleFactor      = 2
    @Prop(i=12, min=0f, max=1f) var upsampleBlur          = 0.3f
    @Prop(i=13, min=0f, max=1f) var upsampleEdgeTolerance = 1.0f

    // Temporal accumulation
    @Prop(i=14, min=0f, max=1f) var temporalAccumulation     = 0.95f
    @Prop(i=15, min=0f, max=1f) var temporalHistoryRejection = 0.5f

    // Target surfaces
    @Prop(i=16) var targetSurfaces = "scene3d"

    private var lastTargetSurfaces = ""
    private var targetSurfaceNames = emptyList<String>()

    override fun onUpdate(engine: PulseEngine)
    {
        if (targetSurfaces != lastTargetSurfaces)
        {
            onDestroy(engine)
            lastTargetSurfaces = targetSurfaces
            targetSurfaceNames = targetSurfaces.split(",").map { it.trim() }
        }

        targetSurfaceNames.forEachFast { updateRenderer(engine, it) }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        for (surfaceName in targetSurfaceNames)
        {
            val surface = engine.gfx.getSurface(surfaceName) ?: continue
            val renderer = surface.getRenderer<GtaoRenderer>() ?: continue
            surface.deleteRenderer(renderer)
        }
        lastTargetSurfaces = ""
        targetSurfaceNames = emptyList()
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (!enabled) onDestroy(engine)
    }
    
    private fun updateRenderer(engine: PulseEngine, surfaceName: String)
    {
        val surface = engine.gfx.getSurface(surfaceName) ?: return
        val renderer = surface.getRenderer<GtaoRenderer>()
        if (renderer == null)
        {
            surface.addRenderer(GtaoRenderer())
            return
        }

        renderer.intensity                = intensity
        renderer.slices                   = slices
        renderer.maxSteps                 = maxSteps
        renderer.maxRadiusPixels          = maxRadiusPixels
        renderer.radiusMeters             = radiusMeters
        renderer.thicknessMeters          = thicknessMeters
        renderer.denoisePasses            = denoisePasses
        renderer.denoiseRadius            = denoiseRadius
        renderer.denoiseBlurWidth         = denoiseBlurWidth
        renderer.denoiseEdgePreservation  = denoiseEdgePreservation
        renderer.upsampleBlur             = upsampleBlur
        renderer.upsampleEdgeTolerance    = upsampleEdgeTolerance
        renderer.downsampleFactor         = downsampleFactor
        renderer.temporalAccumulation     = temporalAccumulation
        renderer.temporalHistoryRejection = temporalHistoryRejection
    }
}