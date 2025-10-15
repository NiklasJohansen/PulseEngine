package no.njoh.pulseengine.modules.scene.entities

import com.fasterxml.jackson.annotation.JsonAlias
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneState.RUNNING
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.modules.lighting.direct.DirectLightSource
import no.njoh.pulseengine.modules.lighting.direct.DirectLightType
import no.njoh.pulseengine.modules.lighting.direct.DirectShadowType
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.MathUtil
import no.njoh.pulseengine.modules.lighting.global.GiLightSource
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Icon("LIGHT_BULB", size = 24f, showInViewport = true)
open class Lamp : CommonSceneEntity(), DirectLightSource, GiLightSource
{
    var trackParent = true

    @JsonAlias("color")
    override var lightColor   = Color(1f, 0.92f, 0.75f)
    override var lightTexture = ""
    override var intensity    = 1f
    override var radius       = 0f // 0=infinite in global illumination
    override var size         = 30f
    override var coneAngle    = 360f
    override var spill        = 0.95f
    override var type         = DirectLightType.RADIAL
    override var shadowType   = DirectShadowType.SOFT

    protected var initLength   = -1f // Length from light position to center of target entity
    protected var initAngle    = -1f // Angle between light position and center of target entity
    protected var initRotation = -1f // Difference in rotation between light and target entity

    override fun onStart(engine: PulseEngine)
    {
        if (trackParent)
        {
            val target = engine.scene.getEntityOfType<Spatial>(parentId) ?: return
            val xDelta = x - target.x
            val yDelta = y - target.y
            initLength = sqrt(xDelta * xDelta + yDelta * yDelta)
            initAngle = MathUtil.atan2(yDelta / initLength, xDelta / initLength) + target.rotation.toRadians()
            initRotation = rotation - target.rotation
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (engine.scene.state != RUNNING)
            return

        if (trackParent)
        {
            val target = engine.scene.getEntityOfType<Spatial>(parentId) ?: return
            val angle = initAngle - target.rotationInterpolated().toRadians()
            x = target.xInterpolated() + cos(angle) * initLength
            y = target.yInterpolated() + sin(angle) * initLength
            rotation = target.rotationInterpolated() + initRotation
        }
    }

    override fun onRender(engine: PulseEngine, surface: Surface) { }
}