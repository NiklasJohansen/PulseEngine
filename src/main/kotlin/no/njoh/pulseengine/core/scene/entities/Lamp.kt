package no.njoh.pulseengine.core.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.systems.lighting.LightSource
import no.njoh.pulseengine.core.scene.systems.lighting.LightType
import no.njoh.pulseengine.core.scene.systems.lighting.ShadowType
import no.njoh.pulseengine.widgets.editor.EditorIcon
import no.njoh.pulseengine.widgets.editor.Property

@EditorIcon("icon_light_bulb")
open class Lamp : SceneEntity(), LightSource
{
    @Property("Light", 0)
    override var color: Color = Color(1f, 0.92f, 0.75f)

    @Property("Light", 1, 0f)
    override var intensity = 4f

    @Property("Light", 2, 0f)
    override var radius: Float = 800f

    @Property("Light", 3, 0f)
    override var size = 100f

    @Property("Light", 4, 0f, 360f)
    override var coneAngle = 360f

    @Property("Light", 5, 0f, 1f)
    override var spill: Float = 0.95f

    @Property("Light", 6)
    override var type = LightType.RADIAL

    @Property("Light", 7)
    override var shadowType = ShadowType.SOFT

    override fun onRender(engine: PulseEngine, surface: Surface2D) { }
}