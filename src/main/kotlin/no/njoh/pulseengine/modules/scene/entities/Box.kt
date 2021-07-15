package no.njoh.pulseengine.modules.scene.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.systems.lighting.LightOccluder
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.PolygonBody
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.RectangleShape
import no.njoh.pulseengine.util.degreesBetween
import no.njoh.pulseengine.util.interpolateFrom

open class Box : SceneEntity(), PolygonBody, LightOccluder
{
    var textureName: String = ""

    @JsonIgnore
    override var shape = RectangleShape()
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
        val x = if (isRunning) x.interpolateFrom(shape.xCenterLast) else x
        val y = if (isRunning) y.interpolateFrom(shape.yCenterLast) else y
        val r = if (isRunning) rotation + engine.data.interpolation * rotation.degreesBetween(shape.angleLast) else rotation

        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(engine.asset.getSafe(textureName) ?: Texture.BLANK, x, y, width, height, r, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}