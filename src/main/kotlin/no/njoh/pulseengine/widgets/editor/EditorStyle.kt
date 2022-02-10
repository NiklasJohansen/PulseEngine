package no.njoh.pulseengine.widgets.editor

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Font

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