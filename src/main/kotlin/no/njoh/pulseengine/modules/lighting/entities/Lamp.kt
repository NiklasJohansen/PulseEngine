package no.njoh.pulseengine.modules.lighting.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneState.RUNNING
import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.modules.lighting.LightSource
import no.njoh.pulseengine.modules.lighting.LightType
import no.njoh.pulseengine.modules.lighting.ShadowType
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.MathUtil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@ScnIcon("LIGHT_BULB", size = 24f, showInViewport = true)
open class Lamp : SceneEntity(), LightSource
{
    var trackParent = true

    override var color: Color = Color(1f, 0.92f, 0.75f)
    override var intensity = 4f
    override var radius: Float = 800f
    override var size = 100f
    override var coneAngle = 360f
    override var spill: Float = 0.95f
    override var type = LightType.RADIAL
    override var shadowType = ShadowType.SOFT

    protected var initLength   = -1f // Length from light position to center of target entity
    protected var initAngle    = -1f // Angle between light position and center of target entity
    protected var initRotation = -1f // Difference in rotation between light and target entity

    override fun onStart(engine: PulseEngine)
    {
        if (trackParent)
        {
            val target = engine.scene.getEntity(parentId) ?: return
            val xDelta = x - target.x
            val yDelta = y - target.y
            initLength = sqrt(xDelta * xDelta + yDelta * yDelta)
            initAngle = MathUtil.atan2(yDelta / initLength, xDelta / initLength) + target.rotation.toRadians()
            initRotation = rotation - target.rotation
        }
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (engine.scene.state != RUNNING)
            return

        if (trackParent)
        {
            val target = engine.scene.getEntity(parentId) ?: return
            val angle = initAngle - target.rotation.toRadians()
            x = target.x + cos(angle) * initLength
            y = target.y + sin(angle) * initLength
            rotation = target.rotation + initRotation
        }
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D) { }
}