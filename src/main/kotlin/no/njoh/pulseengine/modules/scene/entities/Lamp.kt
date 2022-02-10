package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.systems.lighting.LightSource
import no.njoh.pulseengine.modules.scene.systems.lighting.LightType
import no.njoh.pulseengine.modules.scene.systems.lighting.ShadowType
import no.njoh.pulseengine.widgets.sceneEditor.EditorIcon
import no.njoh.pulseengine.widgets.sceneEditor.Property

@EditorIcon("icon_light_bulb")
open class Lamp : SceneEntity(), LightSource
{
    @Property("Light", 0)
    override var color: Color = Color(1f, 0.92f, 0.75f)

    @Property("Light", 1, 0f)
    override var intensity = 7f

    @Property("Light", 2, 0f)
    override var radius: Float = 400f

    @Property("Light", 3, 0f)
    override var size = 100f

    @Property("Light", 4, 0f, 360f)
    override var coneAngle = 360f

    @Property("Light", 5, 0f, 1f)
    override var spill: Float = 0.5f

    @Property("Light", 6)
    override var type = LightType.RADIAL

    @Property("Light", 7)
    override var shadowType = ShadowType.SOFT

    override fun onRender(engine: PulseEngine, surface: Surface2D) { }
}