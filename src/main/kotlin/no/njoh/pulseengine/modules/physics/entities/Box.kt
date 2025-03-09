package no.njoh.pulseengine.modules.physics.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.modules.scene.entities.StandardSceneEntity
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.bodies.PolygonBody
import no.njoh.pulseengine.modules.physics.shapes.RectangleShape
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateAngleFrom
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom

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

    /** Initialize the shape once when the entity is created */
    override fun onCreate() = shape.init(x, y, width, height, rotation, density)

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(
            texture = Texture.BLANK,
            x = xInterpolated(),
            y = yInterpolated(),
            width = width,
            height = height,
            angle = rotationInterpolated(),
            xOrigin = 0.5f,
            yOrigin = 0.5f
        )
    }

    override fun xInterpolated() = x.interpolateFrom(shape.xCenterLast)
    override fun yInterpolated() = y.interpolateFrom(shape.yCenterLast)
    override fun rotationInterpolated() = rotation.interpolateAngleFrom(shape.angleLast)
}