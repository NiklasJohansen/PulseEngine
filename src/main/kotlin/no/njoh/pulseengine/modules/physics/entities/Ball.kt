package no.njoh.pulseengine.modules.physics.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.modules.lighting.LightOccluder
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.physics.bodies.CircleBody
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.annotations.Property

open class Ball : SceneEntity(), CircleBody, LightOccluder
{
    var textureName: String = "ball"

    @JsonIgnore
    override val shape = CircleShape()
    @Property("Physics", 0)
    override var bodyType = BodyType.DYNAMIC
    @Property("Physics", 1)
    override var layerMask = 1
    @Property("Physics", 2)
    override var collisionMask = 1
    @Property("Physics", 3)
    override var restitution = 0.5f
    @Property("Physics", 4)
    override var density = 1f
    @Property("Physics", 5)
    override var friction = 0.4f
    @Property("Physics", 6)
    override var drag = 0.01f

    @Property("Lighting")
    override var castShadows = true

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