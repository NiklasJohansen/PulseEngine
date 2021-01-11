package no.njoh.pulseengine.widgets.SceneEditor

import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.assets.Font

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