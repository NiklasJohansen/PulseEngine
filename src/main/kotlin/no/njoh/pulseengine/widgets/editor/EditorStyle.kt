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
        colors["LABEL_DARK"] = Color(0.5f, 0.5f, 0.5f, 1.0f)
        colors["LIGHT_BG"] = Color(14, 14, 14)
        colors["DARK_BG"] = Color(10, 10, 11)
        colors["STROKE"] = Color(0.03612247f, 0.038525835f, 0.04285717f, 1.0f)
        colors["HEADER"] = Color(19, 19, 21)
        colors["HEADER_HOVER"] = Color(32, 42, 59)
        colors["BUTTON"] = Color(8, 8, 9)
        colors["BUTTON_HOVER"] = Color(12, 12, 13)
        colors["BUTTON_EXIT"] = Color(0.7642857f, 0.3603061f, 0.3603061f, 0.69803923f)
        colors["SCROLLBAR_BG"] = Color(6, 6, 7)
        colors["SCROLLBAR"] = Color(19, 19, 21)
        colors["SCROLLBAR_HOVER"] = Color(32, 42, 59)
        colors["INPUT_BG"] = Color(3, 3, 3)
        colors["HEADER_FOOTER"] = Color(12, 12, 13)
        colors["WINDOW_HEADER"] = Color(32, 42, 59)

        // Set default sizes
        sizes["PROP_ROW_HEIGHT"] = 25f
        sizes["PROP_HEADER_ROW_HEIGHT"] = 26f
        sizes["DROPDOWN_ROW_HEIGHT"] = 20f

        // Set default icons
        icons["CUBE"] = "a"
        icons["LIGHT_BULB"] = "b"
        icons["COG"] = "c"
        icons["MONITOR"] = "d"
        icons["CROSS"] = "e"
        icons["IMAGE"] = "f"
        icons["CURSOR"] = "g"
        icons["FILE"] = "h"
        icons["SHAPES"] = "i"
        icons["GEARS"] = "j"
        icons["MUSIC"] = "k"
        icons["FONT"] = "l"
        icons["TEXT"] = "m"
        icons["NO_IMAGE"] = "n"
        icons["BOX"] = "o"
        icons["LIST"] = "p"
        icons["NESTED_LIST"] = "q"
        icons["EDIT"] = "s"
        icons["EDIT_DISABLED"] = "t"
        icons["VISIBLE"] = "u"
        icons["HIDDEN"] = "v"
        icons["CAMERA"] = "w"
        icons["FOLDER"] = "x"
        icons["ARROW_DOWN"] = "1"
        icons["ARROW_RIGHT"] = "2"
    }

    companion object
    {
        private var DEFAULT_COLOR = Color(1f, 1f, 1f)
        private var DEFAULT_ICON = "a"
    }
}