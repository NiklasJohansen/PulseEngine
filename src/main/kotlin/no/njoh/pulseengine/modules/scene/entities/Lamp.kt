package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.scene.systems.lighting.LightSource

open class Lamp : SceneEntity(), LightSource
{
    override var color: Color = Color(1f, 1f, 1f)
    override var intensity = 0.8f
    override var radius: Float = 3000f

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        if (engine.scene.state == SceneState.STOPPED)
        {
            surface.setDrawColor(1f, 1f, 0.2f, 0.8f)
            surface.drawTexture(Texture.BLANK, x, y, 50f, 50f, rotation, 0.5f, 0.5f)
        }
    }
}