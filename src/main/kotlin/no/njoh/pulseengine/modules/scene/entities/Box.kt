package no.njoh.pulseengine.modules.scene.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.scene.SceneState
import no.njoh.pulseengine.modules.asset.types.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.systems.lighting.NormalMapRenderPassTarget
import no.njoh.pulseengine.modules.scene.systems.lighting.LightOccluder
import no.njoh.pulseengine.modules.scene.systems.physics.BodyType
import no.njoh.pulseengine.modules.scene.systems.physics.bodies.PolygonBody
import no.njoh.pulseengine.modules.scene.systems.physics.shapes.RectangleShape
import no.njoh.pulseengine.modules.shared.utils.Extensions.degreesBetween
import no.njoh.pulseengine.modules.shared.utils.Extensions.interpolateFrom
import no.njoh.pulseengine.widgets.sceneEditor.Property

open class Box : SceneEntity(), PolygonBody, LightOccluder, NormalMapRenderPassTarget
{
    var textureName: String = ""
    override var normalMapName: String = ""

    @JsonIgnore
    override var shape = RectangleShape()

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
        val x = if (isRunning) x.interpolateFrom(shape.xCenterLast) else x
        val y = if (isRunning) y.interpolateFrom(shape.yCenterLast) else y
        val r = if (isRunning) rotation + engine.data.interpolation * rotation.degreesBetween(shape.angleLast) else rotation

        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(engine.asset.getSafe(textureName) ?: Texture.BLANK, x, y, width, height, r, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}