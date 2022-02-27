package no.njoh.pulseengine.modules.lighting.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneState.RUNNING
import no.njoh.pulseengine.modules.lighting.LightSource
import no.njoh.pulseengine.modules.lighting.LightType
import no.njoh.pulseengine.modules.lighting.ShadowType
import no.njoh.pulseengine.widgets.editor.EditorIcon
import no.njoh.pulseengine.core.shared.annotations.Property
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.MathUtil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@EditorIcon("icon_light_bulb")
open class Lamp : SceneEntity(), LightSource
{
    @Property("Pin to entity", 0)
    var targetEntityId: Long = -1L

    @Property("Light", 0)
    override var color: Color = Color(1f, 0.92f, 0.75f)

    @Property("Light", 1, 0f)
    override var intensity = 4f

    @Property("Light", 2, 0f)
    override var radius: Float = 800f

    @Property("Light", 3, 0f)
    override var size = 100f

    @Property("Light", 4, 0f, 360f)
    override var coneAngle = 360f

    @Property("Light", 5, 0f, 1f)
    override var spill: Float = 0.95f

    @Property("Light", 6)
    override var type = LightType.RADIAL

    @Property("Light", 7)
    override var shadowType = ShadowType.SOFT

    private var initLength   = -1f // Length from light position to center of target entity
    private var initAngle    = -1f // Angle between light position and center of target entity
    private var initRotation = -1f // Difference in rotation between light and target entity

    override fun onStart(engine: PulseEngine)
    {
        val target = engine.scene.getEntity(targetEntityId) ?: return
        val xDelta = x - target.x
        val yDelta = y - target.y
        initLength = sqrt(xDelta * xDelta + yDelta * yDelta)
        initAngle = MathUtil.atan2(yDelta / initLength, xDelta / initLength) + target.rotation.toRadians()
        initRotation = rotation - target.rotation
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (engine.scene.state != RUNNING)
            return

        val target = engine.scene.getEntity(targetEntityId) ?: return
        val angle = initAngle - target.rotation.toRadians()
        x = target.x + cos(angle) * initLength
        y = target.y + sin(angle) * initLength
        rotation = target.rotation + initRotation
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D) { }
}