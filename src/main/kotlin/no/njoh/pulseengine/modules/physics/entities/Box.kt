package no.njoh.pulseengine.modules.physics.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.modules.scene.entities.StandardSceneEntity
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.bodies.PolygonBody
import no.njoh.pulseengine.modules.physics.shapes.RectangleShape
import no.njoh.pulseengine.core.shared.utils.Extensions.degreesBetween
import kotlin.Float.Companion.NaN

open class Box : StandardSceneEntity(), PolygonBody
{
    @JsonIgnore
    override var shape = RectangleShape()

    override var bodyType = BodyType.DYNAMIC
    override var layerMask = 1
    override var collisionMask = 1
    override var restitution = 0.5f
    override var density = 1f
    override var friction = 0.4f
    override var drag = 0.01f

    var xInterpolated = NaN
    var yInterpolated = NaN
    var rotInterpolated = NaN

    /** Initialize the shape once when the entity is created */
    override fun onCreate() = shape.init(x, y, width, height, rotation, density)

    override fun onUpdate(engine: PulseEngine)
    {
        val i = engine.data.interpolation
        xInterpolated = x * i + (1f - i) * shape.xCenterLast
        yInterpolated = y * i + (1f - i) * shape.yCenterLast
        rotInterpolated = rotation + i * rotation.degreesBetween(shape.angleLast)
    }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        val x = if (xInterpolated.isNaN()) x else xInterpolated
        val y = if (yInterpolated.isNaN()) y else yInterpolated
        val r = if (rotInterpolated.isNaN()) rotation else rotInterpolated

        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(Texture.BLANK, x, y, width, height, r, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}