package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.editor.GizmoRenderable
import no.njoh.pulseengine.modules.editor.GizmoRenderContext
import org.joml.Quaternionf
import org.joml.Vector3f

@Name("3D Light Probe")
class LightProbe3D : SceneEntity(), Named, Translatable3D, Rotatable3D, GizmoRenderable
{
    @Prop override var name = "Light Probe"

    @Prop("Transform", i=0)    override var position          = Vector3f(0f, 1f, 0f)
    @Prop("Transform", i=1)    override var rotation          = Vector3f()
    @Prop("Influence", i=2)             var influenceSize     = Vector3f(10f)
    @Prop("Influence", i=3, min=0f)     var blendDistance     = 1f
    @Prop("Capture",   i=4)             var captureOffset     = Vector3f()
    @Prop("Capture",   i=5, min=0.001f) var nearPlane         = 0.05f
    @Prop("Capture",   i=6, min=0.01f)  var farPlane          = 100f
    @Prop("Specular",  i=7)             var projectionSize    = Vector3f(10f)
    @Prop("Specular",  i=8, min=0f)     var specularIntensity = 1f
    @Prop("Diffuse",   i=9, min=0f)     var diffuseIntensity  = 1f

    private val capturePosition = Vector3f()
    private val blendInnerSize  = Vector3f()
    private val gizmoRotation   = Quaternionf()

    override fun renderGizmo(surface: Surface, context: GizmoRenderContext)
    {
        if (isSet(SELECTED)) 
            surface.setDrawColor(GizmoRenderContext.ACTIVE_COLOR) 
        else 
            surface.setDrawColor(0.3f, 0.85f, 1f)
        
        context.drawMarker(surface, position, gizmoMarkerRadius, cross = true)

        if (isNot(SELECTED)) return

        gizmoRotation.rotationXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())

        surface.setDrawColor(0.3f, 0.85f, 1f, 0.9f)
        context.drawBox(surface, position, influenceSize, rotation = gizmoRotation)

        if (blendDistance.isFinite() && blendDistance > 0f)
        {
            blendInnerSize.set(influenceSize).sub(blendDistance * 2f, blendDistance * 2f, blendDistance * 2f)
            surface.setDrawColor(0.3f, 0.85f, 1f, 0.35f)
            context.drawBox(surface, position, blendInnerSize, rotation = gizmoRotation)
        }

        surface.setDrawColor(1f, 0.45f, 0.2f, 0.9f)
        context.drawBox(surface, position, projectionSize, dashed = true, rotation = gizmoRotation)

        capturePosition.set(captureOffset).rotate(gizmoRotation).add(position)
        surface.setDrawColor(GizmoRenderContext.ACTIVE_COLOR)
        context.drawLine(surface, position, capturePosition)
        context.drawMarker(surface, capturePosition, 4f)
    }
}