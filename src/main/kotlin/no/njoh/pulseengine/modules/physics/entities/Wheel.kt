package no.njoh.pulseengine.modules.physics.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.physics.bodies.CircleBody
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import kotlin.math.abs
import kotlin.math.sign

open class Wheel : SceneEntity(), CircleBody
{
    var textureName: String = "wheel"
    var acceleration = 3f
    var maxSpeed = 1f

    @JsonIgnore
    override val shape = CircleShape()
    override var bodyType = BodyType.DYNAMIC
    override var layerMask = 1
    override var collisionMask = 1
    override var restitution = 0.5f
    override var density = 1f
    override var friction = 0.4f
    override var drag = 0.01f

    @JsonIgnore
    private var acc = 0f

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
            shape.rotLast -= acc * engine.data.fixedDeltaTime
            acc = 0f
            wakeUp()
        }

        val rotVel = shape.rot - shape.rotLast
        if (abs(rotVel) > maxSpeed)
            shape.rotLast = shape.rot - maxSpeed * sign(rotVel)
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        val isRunning = (engine.scene.state == SceneState.RUNNING)
        val x = if (isRunning) x.interpolateFrom(shape.xLast) else x
        val y = if (isRunning) y.interpolateFrom(shape.yLast) else y
        val rot = if (isRunning) rotation.interpolateFrom(shape.rotLast.toDegrees()) else rotation

        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(engine.asset.getSafe(textureName) ?: Texture.BLANK, x, y, width, height, rot, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}