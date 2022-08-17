package no.njoh.pulseengine.modules.physics.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.ScnProp
import no.njoh.pulseengine.core.shared.utils.Extensions.degreesBetween
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.physics.bodies.CircleBody
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import kotlin.Float.Companion.NaN
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

open class Wheel : SceneEntity(), CircleBody
{
    @JsonIgnore
    override val shape = CircleShape()

    override var bodyType = BodyType.DYNAMIC
    override var layerMask = 1
    override var collisionMask = 1
    override var restitution = 0.5f
    override var density = 1f
    override var friction = 0.4f
    override var drag = 0.01f
    @ScnProp("Physics", 7) var acceleration = 1f
    @ScnProp("Physics", 8) var maxAngularVelocity = 0.3f

    @JsonIgnore protected var xInterpolated = NaN
    @JsonIgnore protected var yInterpolated = NaN
    @JsonIgnore protected var rotInterpolated = NaN
    @JsonIgnore private var acc = 0f

    var textureName = ""

    /** Initialize the shape once when the entity is created */
    override fun onCreate() = shape.init(x, y, max(width, height) * 0.5f, rotation, density)

    override fun onUpdate(engine: PulseEngine)
    {
        if (engine.input.isPressed(Key.RIGHT) || engine.input.isPressed(Key.D))
            acc = acceleration

        if (engine.input.isPressed(Key.LEFT) || engine.input.isPressed(Key.A))
            acc = -acceleration

        val i = engine.data.interpolation
        xInterpolated = x * i + (1f - i) * shape.xLast
        yInterpolated = y * i + (1f - i) * shape.yLast
        rotInterpolated = rotation + i * rotation.degreesBetween(shape.rotLast.toDegrees())
    }

    override fun onFixedUpdate(engine: PulseEngine)
    {
        if (acc != 0f)
        {
            shape.applyAngularAcceleration(acc * engine.data.fixedDeltaTime)
            acc = 0f
            wakeUp()
        }

        val rotVel = shape.rot - shape.rotLast
        if (abs(rotVel) > maxAngularVelocity)
            shape.rotLast = shape.rot - maxAngularVelocity * sign(rotVel)
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        val x = if (xInterpolated.isNaN()) x else xInterpolated
        val y = if (yInterpolated.isNaN()) y else yInterpolated
        val r = if (rotInterpolated.isNaN()) rotation else rotInterpolated

        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(Texture.BLANK, x, y, width, height, r, 0.5f, 0.5f)
    }
}