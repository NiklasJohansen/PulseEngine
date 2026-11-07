package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.submission.DecalColorBlendMode
import no.njoh.pulseengine.core.graphics.scene3d.submission.DecalNormalBlendMode
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.editor.GizmoRenderContext
import no.njoh.pulseengine.modules.editor.GizmoRenderContext.Companion.ACTIVE_COLOR
import no.njoh.pulseengine.modules.editor.GizmoRenderable
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

@Name("3D Decal")
class Decal3D : SceneEntity(), Named, Spatial3D, Scene3DRenderable, GizmoRenderable
{
    @Prop override var name     = "Decal"
    @Prop          var material = AssetHandle<Material>()

    @Prop("Transform", i=1)           override var position              = Vector3f()
    @Prop("Transform", i=2)           override var rotation              = Vector3f()
    @Prop("Transform", i=3)           override var scale                 = Vector3f(1f, 1f, 0.25f)
    @Prop("Channels",  i=4)                    var colorBlendMode        = DecalColorBlendMode.MIX
    @Prop("Channels",  i=5,  min=0f, max=1f)   var colorBlend            = 1f
    @Prop("Channels",  i=6)                    var normalBlendMode       = DecalNormalBlendMode.COMBINE
    @Prop("Channels",  i=7,  min=0f, max=1f)   var normalBlend           = 1f
    @Prop("Channels",  i=8,  min=0f, max=1f)   var roughnessBlend        = 1f
    @Prop("Channels",  i=10, min=0f, max=1f)   var metallicBlend         = 0f
    @Prop("Fade",      i=11, min=0f, max=0.5f) var edgeFade              = 0.05f
    @Prop("Fade",      i=12, min=0f, max=90f)  var fadeStartAngleDegrees = 60f
    @Prop("Fade",      i=13, min=0f)           var fadeStartDistance     = 80f
    @Prop("Fade",      i=14, min=0f)           var fadeEndDistance       = 100f
    @Prop("Rendering", i=15, min=0f, max=1f)   var opacity               = 1f
    @Prop("Rendering", i=16)                   var sortOrder             = 0
    @Prop("Rendering", i=17)                   var targetLayerMask       = 1

    private val transform     = Matrix4f()
    private val gizmoRotation = Quaternionf()
    private val gizmoEndpoint = Vector3f()

    override fun onRender(engine: PulseEngine, context: SceneRenderContext)
    {
        if (scale.x == 0f || scale.y == 0f || scale.z == 0f) 
            return

        val material = engine.asset.getOrNull(material) ?: return

        transform.translation(position)
            .rotateXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())
            .scale(scale)

        context.submitDecal(
            material, transform, opacity, colorBlend, colorBlendMode, normalBlend, normalBlendMode, roughnessBlend, 
            metallicBlend, edgeFade, fadeStartAngleDegrees, fadeStartDistance, fadeEndDistance, targetLayerMask, sortOrder
        )
    }

    override fun renderGizmo(surface: Surface, context: GizmoRenderContext)
    {
        surface.setDrawColor(if (isSet(SELECTED)) ACTIVE_COLOR else DECAL_COLOR)
        context.drawMarker(surface, position, gizmoMarkerRadius, cross = true)

        if (isNot(SELECTED)) return
        
        gizmoRotation.rotationXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())
        context.drawBox(surface, position, scale, rotation = gizmoRotation)
        
        gizmoEndpoint.set(0f, 0f, -scale.z * 0.5f).rotate(gizmoRotation).add(position)
        context.drawLine(surface, position, gizmoEndpoint)
    }

    companion object
    {
        private val DECAL_COLOR = Color(0.4f, 0.9f, 0.6f)
    }
}