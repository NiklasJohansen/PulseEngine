package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.ConicalLight3D
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.editor.GizmoRenderable
import no.njoh.pulseengine.modules.editor.GizmoRenderContext
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

@Name("3D Spot Light")
class SpotLight3D : SceneEntity(), Named, ConicalLight3D, Rotatable3D, GizmoRenderable
{
    @Prop(i=0) override var name = "Spot Light"

    @Prop("Transform", i=1) override var position = Vector3f(0f, 1f, 0f)
    @Prop("Transform", i=2) override var rotation = Vector3f(-90f, 0f, 0f)

    @Prop("Lighting", i=1)                           var color          = Color(1f, 1f, 1f)
    @Prop("Lighting", i=2, min=0f)                   var intensity      = 4f
    @Prop("Lighting", i=3, min=0f)          override var range          = 10f
    @Prop("Lighting", i=4, min=0f)          override var sourceRadius   = 0.1f
    @Prop("Lighting", i=5, min=0f, max=89f) override var outerConeAngle = 40f
    @Prop("Lighting", i=6, min=0f, max=89f)          var innerConeAngle = 0f

    @Prop("Shadow", i=1)          var shadowEnabled       = false
    @Prop("Shadow", i=2, min=64f) var shadowResolution    = 512
    @Prop("Shadow", i=3, min=0f)  var shadowNearPlane     = 0.05f
    @Prop("Shadow", i=4, min=0f)  var shadowBias          = 0.005f
    @Prop("Shadow", i=5, min=0f)  var shadowImportance    = 1f
    @Prop("Shadow", i=6, min=0f)  var contactShadowLength = 0.1f

    private val direction      = Vector3f()
    private val rotationMatrix = Matrix4f()
    private val intensityColor = Color()
    private val gizmoRight     = Vector3f()
    private val gizmoUp        = Vector3f()
    private val gizmoCenter    = Vector3f()
    private val gizmoEndpoint  = Vector3f()

    override fun onRenderLight(engine: PulseEngine, context: SceneRenderContext)
    {
        getDirection(direction)
        intensityColor.setFrom(color).multiplyRgb(intensity)
        val outerAngle = outerConeAngle.coerceIn(0f, 89f)
        val innerAngle = innerConeAngle.coerceIn(0f, outerAngle)
        context.submitSpotLight(position, direction, range, intensityColor, innerAngle, outerAngle, sourceRadius, shadowEnabled, shadowResolution, shadowNearPlane, shadowBias, shadowImportance, id, contactShadowLength)
    }

    override fun getDirection(out: Vector3f): Vector3f
    {
        rotationMatrix.identity().rotateXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())
        return rotationMatrix.transformDirection(out.set(0f, 0f, -1f)).normalize()
    }
    
    override fun renderGizmo(surface: Surface, context: GizmoRenderContext)
    {
        if (isSet(SELECTED)) 
            surface.setDrawColor(GizmoRenderContext.ACTIVE_COLOR) 
        else 
            surface.setDrawColor(1f, 0.72f, 0.16f)

        context.drawMarker(surface, position, gizmoMarkerRadius, cross = true)

        getDirection(direction)
        gizmoEndpoint.set(direction).mul(context.getWorldSize(position) * 0.45f).add(position)
        context.drawLine(surface, position, gizmoEndpoint)

        if (isNot(SELECTED) || !range.isFinite() || range <= 0f) 
            return

        if (abs(direction.y) < 0.99f) 
            gizmoRight.set(0f, 1f, 0f) 
        else 
            gizmoRight.set(1f, 0f, 0f)
        
        val right  = direction.cross(gizmoRight, gizmoRight).normalize()
        val up     = right.cross(direction, gizmoUp).normalize()
        val angle  = if (outerConeAngle.isFinite()) outerConeAngle.coerceIn(0f, 89f) else 0f
        val slope  = tan(angle.toRadians())
        val radius = slope * range
        val center = gizmoCenter.set(direction).mul(range).add(position)

        surface.setDrawColor(1f, 0.72f, 0.16f, 0.8f)
        context.drawCircle(surface, center, right, up, radius)
        context.drawLine(surface, position, center)

        for (i in 0 until 8)
        {
            val sideAngle = i * (Math.PI.toFloat() * 2f / 8)
            gizmoEndpoint.set(center).fma(cos(sideAngle) * radius, right).fma(sin(sideAngle) * radius, up)
            context.drawLine(surface, position, gizmoEndpoint)
        }

        if (sourceRadius.isFinite() && sourceRadius > 0f && slope > 0f)
        {
            val distance = (sourceRadius.coerceAtMost(range) / slope).coerceAtMost(range)
            gizmoCenter.set(direction).mul(distance).add(position)
            surface.setDrawColor(0.3f, 0.85f, 1f, 0.9f)
            context.drawCircle(surface, gizmoCenter, right, up, slope * distance)
        }
    }
}