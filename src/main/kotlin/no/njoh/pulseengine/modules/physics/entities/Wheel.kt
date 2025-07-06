package no.njoh.pulseengine.modules.physics.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.modules.scene.entities.StandardSceneEntity
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.annotations.TexRef
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateAngleFrom
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.physics.bodies.CircleBody
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

open class Wheel : StandardSceneEntity(), CircleBody
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
    @Prop("Physics", 7) var acceleration = 1f
    @Prop("Physics", 8) var maxAngularVelocity = 0.3f

    @JsonIgnore private var acc = 0f

    @TexRef
    var baseTexture = ""

    /** Initialize the shape once when the entity is created */
    override fun onCreate() = shape.init(x, y, max(width, height) * 0.5f, rotation, density)

    override fun onUpdate(engine: PulseEngine)
    {
        if (engine.input.isPressed(Key.RIGHT) || engine.input.isPressed(Key.D))
            acc = acceleration

        if (engine.input.isPressed(Key.LEFT) || engine.input.isPressed(Key.A))
            acc = -acceleration
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

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(
            texture = engine.asset.getOrNull(baseTexture) ?: Texture.BLANK,
            x = xInterpolated(),
            y = yInterpolated(),
            width = width,
            height = height,
            angle = rotationInterpolated(),
            xOrigin = 0.5f,
            yOrigin = 0.5f
        )
    }

    override fun xInterpolated() = x.interpolateFrom(shape.xLast)
    override fun yInterpolated() = y.interpolateFrom(shape.yLast)
    override fun rotationInterpolated() = rotation.interpolateAngleFrom(shape.rotLast.toDegrees())
}