package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.postprocessing.effects.VolumetricSunEffect
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

/**
 * Manages shadowed sun shafts as a post-processing effect on the configured render surfaces.
 * Sun direction, color, radius and cascaded shadow-map data are sourced from [WorldLightingSystem].
 */
@Name("Sun Volumetrics (3D)")
class SunVolumetricsSystem : SceneSystem()
{
    /** Multiplies the brightness of the scattered sunlight contribution. */
    @Prop(i=1, min=0f) var intensity = 1f

    /** Controls how much participating media is present in the air. Higher values create thicker shafts and faster extinction. */
    @Prop(i=2, min=0f) var density = 0.02f

    /** Biases scattering toward the sun direction. Higher values make the rays tighter and more forward-focused. */
    @Prop(i=3, min=-0.98f, max=0.98f) var anisotropy = 0.7f

    /** Limits how far from the camera the medium is ray-marched, in world units. */
    @Prop(i=4, min=0f) var maxDistance = 120f

    /** Number of ray-march steps taken per pixel. Higher values reduce banding at a higher GPU cost. */
    @Prop(i=5, min=1f, max=96f) var stepCount = 32

    /** Renders the volumetric buffers at 1/downsample resolution. Higher values are faster but produce softer shafts before upsampling. */
    @Prop(i=6, min=1f, max=4f) var downsample = 2

    /** Radius of the bilateral blur used to smooth the low-resolution volumetric buffer. */
    @Prop(i=7, min=0f) var blurRadius = 1.75f

    /** Depth difference tolerated by the low-resolution blur before it preserves an edge instead of blending across it. */
    @Prop(i=8, min=0f) var depthTolerance = 6f

    /** Controls how much neighboring low-resolution volumetric samples are blended during the depth-aware upsample pass. */
    @Prop(i=9, min=0f, max=1f) var upsampleBlur = 0.3f

    /** Controls how aggressively the upsample pass rejects samples across depth discontinuities. */
    @Prop(i=10, min=0f, max=1f) var upsampleEdgeTolerance = 1f

    /** Per-frame jitter strength used to hide marching banding and distribute sampling error over time. */
    @Prop(i=11, min=0f, max=1f) var jitter = 1f

    /** World-space height where the exponential height fog starts contributing to the volumetric density. */
    @Prop(i=12) var heightFogStart = 0f

    /** Exponential falloff of the medium above [heightFogStart]. Higher values concentrate the effect closer to the ground. */
    @Prop(i=13, min=0f) var heightFogFalloff = 0.03f

    /** Comma-separated list of surfaces that should receive the volumetric sun effect. */
    @Prop(i=14) var targetSurfaces = "world"

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

        val worldLighting = engine.scene.getSystemOfType<WorldLightingSystem>()
        targetSurfaceNames.forEachFast { updateEffect(engine, worldLighting, it) }
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (!enabled) onDestroy(engine)
    }

    override fun onDestroy(engine: PulseEngine)
    {
        targetSurfaceNames.forEachFast { engine.gfx.getSurface(it)?.deletePostProcessingEffect(EFFECT_NAME) }
        lastTargetSurfaces = ""
        targetSurfaceNames = emptyList()
    }

    private fun updateEffect(engine: PulseEngine, worldLighting: WorldLightingSystem?, surfaceName: String)
    {
        val surface = engine.gfx.getSurface(surfaceName) ?: return
        if (worldLighting == null || !worldLighting.enabled)
        {
            surface.deletePostProcessingEffect(EFFECT_NAME)
            return
        }

        var effect = surface.getPostProcessingEffect<VolumetricSunEffect>()
        if (effect == null)
        {
            surface.deletePostProcessingEffect(EFFECT_NAME)
            surface.addPostProcessingEffect(VolumetricSunEffect(EFFECT_NAME, EFFECT_ORDER, surface.camera))
            effect = surface.getPostProcessingEffect()
        }

        effect?.shadowMapSurfaceName = worldLighting.getShadowMapSurfaceName()
        effect?.sunColor?.setFrom(worldLighting.sunColor)?.multiplyRgb(worldLighting.sunIntensity)
        effect?.sunRadius = worldLighting.sunRadius
        effect?.intensity = intensity
        effect?.density = density
        effect?.anisotropy = anisotropy
        effect?.maxDistance = maxDistance
        effect?.stepCount = stepCount
        effect?.downsampleFactor = downsample
        effect?.blurRadius = blurRadius
        effect?.blurDepthTolerance = depthTolerance
        effect?.upsampleBlur = upsampleBlur
        effect?.upsampleEdgeTolerance = upsampleEdgeTolerance
        effect?.jitterStrength = jitter
        effect?.heightFogStart = heightFogStart
        effect?.heightFogFalloff = heightFogFalloff
    }

    companion object
    {
        private const val EFFECT_NAME = "volumetric_sun"
        private const val EFFECT_ORDER = 40
    }
}
