package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.scene.interfaces.Renderable
import no.njoh.pulseengine.core.shared.annotations.ScnProp
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Updatable
import kotlin.math.max

abstract class StandardSceneEntity : SceneEntity(), Initiable, Updatable, Renderable, Spatial, Named
{
    @ScnProp(i = -1)
    override var name = ""

    @ScnProp("Transform", 0)
    override var x = 0f

    @ScnProp("Transform", 1)
    override var y = 0f

    @ScnProp("Transform", 2)
    override var z = -0.1f

    @ScnProp("Transform", 3)
    override var width = 100f

    @ScnProp("Transform", 4)
    override var height = 100f

    @ScnProp("Transform", 5)
    override var rotation = 0f

    override fun onCreate() { }
    override fun onStart(engine: PulseEngine) {  }
    override fun onUpdate(engine: PulseEngine) { }
    override fun onFixedUpdate(engine: PulseEngine) { }
    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        var text = this::class.java.simpleName
        val fontWidth = Font.DEFAULT.getWidth(text)
        if (fontWidth > width)
            text = text.substring(0, ((text.length / (fontWidth / max(width, 1f))).toInt().coerceIn(0, text.length)))
        surface.setDrawColor(1f, 1f, 1f, 0.5f)
        surface.drawTexture(Texture.BLANK, x, y, width, height, rotation, 0.5f, 0.5f)
        surface.setDrawColor(1f, 1f, 1f, 1f)
        surface.drawText(text, x, y, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}