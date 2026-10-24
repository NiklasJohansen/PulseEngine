package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.Light3D
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.editor.GizmoRenderable
import no.njoh.pulseengine.modules.editor.GizmoRenderContext
import org.joml.Vector3f

@Name("3D Point Light")
@Icon("LIGHT_BULB")
class PointLight3D : SceneEntity(), Light3D, Named, GizmoRenderable
{
    @Prop override var name = "Point Light"

    @Prop("Transform") override var position = Vector3f(0f, 1f, 0f)

    @Prop("Lighting", i=1)                  var color        = Color(1f, 1f, 1f)
    @Prop("Lighting", i=2, min=0f)          var intensity    = 4f
    @Prop("Lighting", i=3, min=0f) override var range        = 10f
    @Prop("Lighting", i=4, min=0f) override var sourceRadius = 0.1f

    @Prop("Shadow", i=1)          var shadowEnabled       = false
    @Prop("Shadow", i=2, min=64f) var shadowResolution    = 512
    @Prop("Shadow", i=3, min=0f)  var shadowNearPlane     = 0.05f
    @Prop("Shadow", i=4, min=0f)  var shadowBias          = 0.005f
    @Prop("Shadow", i=5, min=0f)  var shadowImportance    = 1f
    @Prop("Shadow", i=6, min=0f)  var contactShadowLength = 0.1f

    override fun onRenderLight(engine: PulseEngine, context: SceneRenderContext)
    {
        context.submitPointLight(position, range, color, intensity, sourceRadius, shadowEnabled, shadowResolution, shadowNearPlane, shadowBias, shadowImportance, id, contactShadowLength)
    }

    override fun renderGizmo(surface: Surface, context: GizmoRenderContext)
    {
        if (isSet(SELECTED)) 
            surface.setDrawColor(GizmoRenderContext.ACTIVE_COLOR)
        else
            surface.setDrawColor(1f, 0.72f, 0.16f)
        
        context.drawMarker(surface, position, gizmoMarkerRadius, cross = true)

        if (isNot(SELECTED) || !range.isFinite() || range <= 0f) 
            return
 
        surface.setDrawColor(1f, 0.72f, 0.16f, 0.8f)
        context.drawCircle(surface, position, context.cameraRight, context.cameraUp, range)
        surface.setDrawColor(0.3f, 0.85f, 1f, 0.9f)
        context.drawCircle(surface, position, context.cameraRight, context.cameraUp, sourceRadius.coerceIn(0f, range))
    }
}