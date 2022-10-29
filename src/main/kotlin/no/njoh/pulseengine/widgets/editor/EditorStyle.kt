package no.njoh.pulseengine.widgets.editor

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Font

class EditorStyle
{
    val colors = mutableMapOf<String, Color>()
    val fonts = mutableMapOf<String, Font>()
    val sizes = mutableMapOf<String, Float>()
    val icons = mutableMapOf<String, String>()
    val iconFontName = "icon_font"

    fun getColor(name: String) = colors[name] ?: DEFAULT_COLOR
    fun getFont(name: String = "") = fonts[name] ?: Font.DEFAULT
    fun getSize(name: String = "") = sizes[name] ?: 0f
    fun getIcon(name: String?) = icons[name] ?: DEFAULT_ICON

    init
    {
        // Set default colors
        colors["LABEL"] = Color(1.0f, 1.0f, 1.0f, 1.0f)
        colors["BG_LIGHT"] = Color(0.036326528f, 0.048244897f, 0.057142854f, 0.80784315f)
        colors["BG_DARK"] = Color(0.024897957f, 0.026741894f, 0.028571427f, 0.9490196f)
        colors["STROKE"] = Color(0.03612247f, 0.038525835f, 0.04285717f, 1.0f)
        colors["HEADER"] = Color(0.08892856f, 0.11766804f, 0.14999998f, 0.9490196f)
        colors["HEADER_HOVER"] = Color(0.09918367f, 0.17537574f, 0.25714284f, 1.0f)
        colors["BUTTON"] = Color(0.033418354f, 0.03418366f, 0.03571427f, 1.0f)
        colors["BUTTON_HOVER"] = Color(0.047058824f, 0.050980393f, 0.050980393f, 1.0f)
        colors["BUTTON_EXIT"] = Color(0.7642857f, 0.3603061f, 0.3603061f, 0.69803923f)
        colors["ITEM"] = Color(0.048367348f, 0.06711405f, 0.08571428f, 1.0f)
        colors["ITEM_HOVER"] = Color(0.10275511f, 0.11865307f, 0.13571429f, 1.0f)

        // Set default sizes
        sizes["PROP_ROW_HEIGHT"] = 30f
        sizes["PROP_HEADER_ROW_HEIGHT"] = 30f
        sizes["DROPDOWN_ROW_HEIGHT"] = 25f

        // Set default icons
        icons["CUBE"] = "a"
        icons["LIGHT_BULB"] = "b"
        icons["COG"] = "c"
        icons["MONITOR"] = "d"
        icons["CROSS"] = "e"
        icons["GEARS"] = "j"
        icons["SHAPES"] = "i"
        icons["IMAGE"] = "f"
        icons["LIST"] = "p"
        icons["NESTED_LIST"] = "q"
        icons["EDIT"] = "s"
        icons["EDIT_DISABLED"] = "t"
        icons["VISIBLE"] = "u"
        icons["HIDDEN"] = "v"
        icons["CAMERA"] = "w"
    }

    companion object
    {
        private var DEFAULT_COLOR = Color(1f, 1f, 1f)
        private var DEFAULT_ICON = "a"
    }
}