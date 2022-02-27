package no.njoh.pulseengine.modules.physics.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.bodies.PolygonBody
import no.njoh.pulseengine.modules.physics.shapes.RectangleShape
import no.njoh.pulseengine.core.shared.utils.Extensions.degreesBetween
import no.njoh.pulseengine.core.shared.annotations.Property
import kotlin.Float.Companion.NaN

open class Box : SceneEntity(), PolygonBody
{
    @JsonIgnore
    override var shape = RectangleShape()

    @Property("Physics", 0) override var bodyType = BodyType.DYNAMIC
    @Property("Physics", 1) override var layerMask = 1
    @Property("Physics", 2) override var collisionMask = 1
    @Property("Physics", 3) override var restitution = 0.5f
    @Property("Physics", 4) override var density = 1f
    @Property("Physics", 5) override var friction = 0.4f
    @Property("Physics", 6) override var drag = 0.01f

    protected var xInterpolated = NaN
    protected var yInterpolated = NaN
    protected var rotInterpolated = NaN

    /** Initialize the shape once when the entity is created */
    override fun onCreate() = shape.init(x, y, width, height, rotation, density)

    override fun onUpdate(engine: PulseEngine)
    {
        val i = engine.data.interpolation
        xInterpolated = x * i + (1f - i) * shape.xCenterLast
        yInterpolated = y * i + (1f - i) * shape.yCenterLast
        rotInterpolated = rotation + i * rotation.degreesBetween(shape.angleLast)
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        val x = if (xInterpolated.isNaN()) x else xInterpolated
        val y = if (yInterpolated.isNaN()) y else yInterpolated
        val r = if (rotInterpolated.isNaN()) rotation else rotInterpolated

        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(Texture.BLANK, x, y, width, height, r, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}