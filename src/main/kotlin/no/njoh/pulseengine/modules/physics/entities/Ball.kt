package no.njoh.pulseengine.modules.physics.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.entities.StandardSceneEntity
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.physics.bodies.CircleBody
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.degreesBetween
import kotlin.Float.Companion.NaN
import kotlin.math.max

open class Ball : StandardSceneEntity(), CircleBody
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

    protected var xInterpolated = NaN
    protected var yInterpolated = NaN
    protected var rotInterpolated = NaN

    /** Initialize the shape once when the entity is created */
    override fun onCreate() = shape.init(x, y, max(width, height) * 0.5f, rotation, density)

    override fun onUpdate(engine: PulseEngine)
    {
        val i = engine.data.interpolation
        xInterpolated = x * i + (1f - i) * shape.xLast
        yInterpolated = y * i + (1f - i) * shape.yLast
        rotInterpolated = rotation + i * rotation.degreesBetween(shape.rotLast.toDegrees())
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        val x = if (xInterpolated.isNaN()) x else xInterpolated
        val y = if (yInterpolated.isNaN()) y else yInterpolated
        val r = if (rotInterpolated.isNaN()) rotation else rotInterpolated
        val size = max(width, height)

        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(Texture.BLANK, x, y, size, size, r, 0.5f, 0.5f, cornerRadius = size * 0.5f)
    }
}