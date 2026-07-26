package no.njoh.pulseengine.modules.editor

import gnu.trove.map.hash.THashMap
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.shared.primitives.Color

class EditorStyle
{
    val colors = THashMap<String, Color>()
    val fonts = THashMap<String, Font>()
    val sizes = THashMap<String, Float>()
    val icons = THashMap<String, String>()
    val iconFontName = "icon_font"

    fun getColor(name: String) = colors[name] ?: DEFAULT_COLOR
    fun getFont(name: String = "") = fonts[name] ?: Font.DEFAULT
    fun getSize(name: String = "") = sizes[name] ?: 0f
    fun getIcon(name: String?) = icons[name] ?: DEFAULT_ICON

    init
    {
        // Set default colors
        colors["LABEL"]           = Color(220, 220, 220, 255)
        colors["LABEL_DARK"]      = Color(127, 127, 127, 255)
        colors["LIGHT_BG"]        = Color(14,  14,  14,  200)
        colors["DARK_BG"]         = Color(22,  22,  23,  200)
        colors["STROKE"]          = Color(23,  23,  23,  220)
        colors["HEADER"]          = Color(37,  37,  40,  220)
        colors["HEADER_HOVER"]    = Color(32,  42,  59,  220)
        colors["BUTTON"]          = Color(26,  26,  28,  220)
        colors["BUTTON_HOVER"]    = Color(21,  21,  23,  200)
        colors["BUTTON_EXIT"]     = Color(194, 91,  91,  220)
        colors["DROPDOWN_BG"]     = Color(26,  26,  28,  220)
        colors["DROPDOWN_HEADER"] = Color(37,  37,  40,  255)
        colors["SCROLLBAR_BG"]    = Color(36,  36,  38,  220)
        colors["SCROLLBAR"]       = Color(25,  25,  25,  220)
        colors["SCROLLBAR_HOVER"] = Color(32,  42,  59,  220)
        colors["INPUT_BG"]        = Color(10, 10,   10,  220)
        colors["HEADER_FOOTER"]   = Color(22,  22,  23,  220)
        colors["WINDOW_HEADER"]   = Color(32,  42,  59,  230)
        colors["ROW"]             = Color(41,  41,  43,  150)

        // Set default sizes
        sizes["PROP_ROW_HEIGHT"]        = 25f
        sizes["PROP_HEADER_ROW_HEIGHT"] = 26f
        sizes["DROPDOWN_ROW_HEIGHT"]    = 20f
        sizes["HEADER_FONT_SIZE"]       = 18f
        sizes["CONTENT_FONT_SIZE"]      = 16f
        sizes["BUTTON_FONT_SIZE"]       = 32f

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