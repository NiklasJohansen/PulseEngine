package no.njoh.pulseengine.modules.physics.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.modules.scene.entities.StandardSceneEntity
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.physics.bodies.CircleBody
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateAngleFrom
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
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

    /** Initialize the shape once when the entity is created */
    override fun onCreate() = shape.init(x, y, max(width, height) * 0.5f, rotation, density)

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        val size = max(width, height)
        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(
            texture = Texture.BLANK,
            x = xInterpolated(),
            y = yInterpolated(),
            width = size,
            height = size,
            angle = rotationInterpolated(),
            xOrigin = 0.5f,
            yOrigin = 0.5f, cornerRadius = size * 0.5f)
    }

    override fun xInterpolated() = x.interpolateFrom(shape.xLast)
    override fun yInterpolated() = y.interpolateFrom(shape.yLast)
    override fun rotationInterpolated() = rotation.interpolateAngleFrom(shape.rotLast.toDegrees())
}