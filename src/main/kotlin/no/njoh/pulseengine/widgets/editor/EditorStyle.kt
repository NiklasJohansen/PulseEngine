package no.njoh.pulseengine.widgets.editor

import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.shared.primitives.Color

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
        colors["LABEL"]           = Color(255, 255, 255, 220)
        colors["LABEL_DARK"]      = Color(127, 127, 127, 220)
        colors["LIGHT_BG"]        = Color(14,  14,  14,  200)
        colors["DARK_BG"]         = Color(22,  22,  23,  200)
        colors["STROKE"]          = Color(9,   9,   10,  220)
        colors["HEADER"]          = Color(37,  37,  40,  220)
        colors["HEADER_HOVER"]    = Color(32,  42,  59,  220)
        colors["BUTTON"]          = Color(26,  26,  28,  220)
        colors["BUTTON_HOVER"]    = Color(12,  12,  13,  220)
        colors["BUTTON_EXIT"]     = Color(194, 91,  91,  220)
        colors["SCROLLBAR_BG"]    = Color(16,  16,  17,  220)
        colors["SCROLLBAR"]       = Color(29,  29,  31,  220)
        colors["SCROLLBAR_HOVER"] = Color(32,  42,  59,  220)
        colors["INPUT_BG"]        = Color(7,   7,   7,   220)
        colors["HEADER_FOOTER"]   = Color(22,  22,  23,  220)
        colors["WINDOW_HEADER"]   = Color(32,  42,  59,  230)
        colors["ROW"]             = Color(0,    0,   0,   50)

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