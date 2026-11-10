package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.gpu.texture.BlendFunction.NONE
import no.njoh.pulseengine.core.graphics.scene3d.probe.LightProbe
import no.njoh.pulseengine.core.graphics.scene3d.probe.LightProbeRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ModelRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.PomQuality.LOW
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.CAMERA
import no.njoh.pulseengine.core.graphics.surface.SurfaceOutputSpec
import no.njoh.pulseengine.core.graphics.surface.colorAttachment
import no.njoh.pulseengine.core.graphics.surface.depthTexture
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.PackedSize
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.modules.scene.entities.LightProbe3D
import org.joml.Matrix4f
import org.joml.Vector3f

@Name("3D Light Probes")
class Scene3DLightProbeSystem : SceneSystem()
{
    @Prop(i=1, min=32f, max=1024f) var resolution               = 256
    @Prop(i=7)                     var pomQuality               = LOW
    @Prop(i=2, min=0f)             var maxUpdateStepsPerFrame   = 1
    @Prop(i=3, min=0f)             var maxUpdatedProbes         = 3
    @Prop(i=4) @EntityRef          var updateProbesAroundEntity = INVALID_ID
    @Prop(i=5)                     var showProbes               = false
    @Prop(i=6)                     var targetSurfaces           = "scene3d"

    private val submittedProbe       = LightProbe()
    private var targetSurfaceNames   = emptyList<String>()
    private var lastTargetSurfaces   = ""
    private var lastResolution       = 0
    private var reportedInvalidProbe = false
    private val debugSphere          = AssetHandle<Model>("light_probe_debug_sphere")
    private val debugMaterial        = AssetHandle<Material>("light_probe_debug_material")
    private val debugPosition        = Vector3f()
    private val debugTransform       = Matrix4f()

    override fun onCreate(engine: PulseEngine)
    {
        engine.asset.load(Model("/pulseengine/assets/sphere.glb", debugSphere.name))
        engine.asset.load(Material(debugMaterial.name, metallicFactor = 1f, roughnessFactor = 0f))
    }

    override fun onRender(engine: PulseEngine)
    {
        // Round the editor value down to a supported power of two
        val size = Integer.highestOneBit(resolution.coerceIn(32, 1024))
        if (targetSurfaces != lastTargetSurfaces || size != lastResolution)
        {
            onDestroy(engine)
            lastResolution = size
            lastTargetSurfaces = targetSurfaces
            targetSurfaceNames = targetSurfaces.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }

        val sourceSurface = targetSurfaceNames.firstOrNull() ?: return
        val source = engine.gfx.getSurface(sourceSurface) ?: return
        if (source.getRenderer<ModelRenderer>() == null) 
            return // The source surface must have a model renderer 

        if (engine.gfx.getSurface(LIGHT_PROBE_SURFACE) == null)
        {
            engine.gfx.createSurface(
                name = LIGHT_PROBE_SURFACE,
                sizeFunction = { _, _ -> PackedSize(size) },
                isVisible = false,
                clearColor = null,
                blendFunction = NONE,
                zOrder = 48, // After local/sun shadows, before the scene camera
                output = SurfaceOutputSpec(attachments = listOf(colorAttachment(), depthTexture()))
            ).addRenderer(
                LightProbeRenderer(
                    sourceSurfaceName = sourceSurface,
                    targetSurfaceNames = targetSurfaceNames,
                    resolution = size
                )
            )
            return // Surface initialization happens at the next frame boundary
        }

        val renderer  = engine.gfx.getSurface(LIGHT_PROBE_SURFACE)?.getRenderer<LightProbeRenderer>() ?: return
        val updatePos = engine.scene.getEntityOfType<Translatable3D>(updateProbesAroundEntity)?.position
        val material  = if (showProbes) engine.asset.getOrNull(debugMaterial) else null
        val sphere    = if (showProbes) engine.asset.getOrNull(debugSphere) else null

        renderer.beginSubmission(maxUpdateStepsPerFrame, updatePos, maxUpdatedProbes, pomQuality)

        engine.scene.forEachEntityOfType<LightProbe3D>()
        {
            if (it.isNot(HIDDEN or DEAD))
            {
                submittedProbe.id                = it.id
                submittedProbe.blendDistance     = it.blendDistance
                submittedProbe.specularIntensity = it.specularIntensity
                submittedProbe.diffuseIntensity  = it.diffuseIntensity
                submittedProbe.nearPlane         = it.nearPlane
                submittedProbe.farPlane          = it.farPlane

                submittedProbe.position.set(it.position)
                submittedProbe.rotation.rotationXYZ(it.rotation.x.toRadians(), it.rotation.y.toRadians(), it.rotation.z.toRadians())
                submittedProbe.captureOffset.set(it.captureOffset)
                submittedProbe.influenceSize.set(it.influenceSize)
                submittedProbe.projectionSize.set(it.projectionSize)

                if (submittedProbe.isValid())
                {
                    renderer.submit(submittedProbe)
                    if (sphere != null && material != null)
                    {
                        // Render light probe as a metallic sphere for debugging
                        debugTransform.translation(submittedProbe.getCapturePosition(debugPosition)).scale(0.25f)
                        engine.gfx.sceneContext.submitModel(engine, sphere, debugTransform, material, renderPassMask = CAMERA, renderId = it.id)
                    }
                }
                else if (!reportedInvalidProbe)
                {
                    Logger.error { "Invalid light probe '${it.id}': transform must be finite, sizes and capture range positive, blend distance and intensity non-negative" }
                    reportedInvalidProbe = true
                }
            }
        }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        engine.gfx.deleteSurface(LIGHT_PROBE_SURFACE)
        lastTargetSurfaces = ""
        targetSurfaceNames = emptyList()
        lastResolution = 0
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (!enabled) onDestroy(engine)
    }

    companion object {
        private const val LIGHT_PROBE_SURFACE = "light_probes"
    }
}