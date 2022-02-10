package no.njoh.pulseengine.widgets.sceneEditor

import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.modules.asset.types.Font

class EditorStyle
{
    val colors = mutableMapOf<String, Color>()
    val fonts = mutableMapOf<String, Font>()

    fun getColor(name: String) = colors[name] ?: DEFAULT_COLOR
    fun getFont(name: String = "") = fonts[name] ?: Font.DEFAULT

    companion object
    {
        private var DEFAULT_COLOR = Color(1f, 1f, 1f)
    }
}