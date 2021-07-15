package no.njoh.pulseengine.modules.scene.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.systems.lighting.LightOccluder
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.CircleShape
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.CircleBody
import no.njoh.pulseengine.util.interpolateFrom
import no.njoh.pulseengine.util.toDegrees

open class Ball : SceneEntity(), CircleBody, LightOccluder
{
    var textureName: String = "ball"

    @JsonIgnore
    override val shape = CircleShape()
    override var bodyType = BodyType.DYNAMIC
    override var layerMask = 1
    override var collisionMask = 1
    override var restitution = 0.5f
    override var density = 1f
    override var friction = 0.4f
    override var drag = 0.01f

    override val castShadows = true

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