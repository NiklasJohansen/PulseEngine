package no.njoh.pulseengine.widgets.SceneEditor

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Scrollable
import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.elements.*
import no.njoh.pulseengine.modules.graphics.ui.elements.InputField.ContentType.*
import no.njoh.pulseengine.modules.graphics.ui.layout.*
import no.njoh.pulseengine.modules.scene.SceneEntity
import no.njoh.pulseengine.util.Logger
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField


object EditorUtil
{
    private val style = EditorStyle().apply {
        colors["INPUT_COLOR"] = Color(41, 43, 46)
        colors["INPUT_COLOR_HOVER_LIGHT"] = Color(46, 48, 51)
        colors["HEADER_COLOR"] = Color(46, 48, 51)
        colors["HEADER_COLOR_HOVER_DARK"] = Color(42, 44, 47)
        colors["BG_COLOR"] = Color(59, 63, 67)
        colors["BG2_COLOR"] = Color(54, 58, 62)
        colors["TILE_COLOR"] = Color(52, 53, 56)
        colors["TILE_COLOR_HOVER"] = Color(56, 57, 60)
        colors["FONT_COLOR"] = Color(212, 214, 218)
    }

    /**
     * Creates a movable and resizable window panel.
     */
    fun createWindowUI(title: String): WindowPanel
    {
        val label = Label(title)
        label.padding.left = 10f
        label.font = style.getFont()
        label.color = style.getColor("FONT_COLOR")
        label.focusable = false

        val exitButton = Button(width = Size.absolute(15f), height = Size.absolute(15f))
        exitButton.padding.top = 7.5f
        exitButton.padding.right = 10f
        exitButton.color = Color(0.8f, 0.2f, 0.2f)
        exitButton.colorHover = Color(1f, 0.4f, 0.4f)
        exitButton.setOnClicked { println("EXIT") }

        val headerPanel = HorizontalPanel(height = Size.absolute(30f))
        headerPanel.color = style.getColor("HEADER_COLOR")
        headerPanel.focusable = false
        headerPanel.addChildren(label, exitButton)

        val windowPanel = WindowPanel(
            x = Position.fixed(0f),
            y = Position.fixed(20f),
            width = Size.absolute(300f),
            height = Size.absolute(200f)
        )

        windowPanel.color = style.getColor("BG_COLOR")
        windowPanel.strokeColor = style.getColor("HEADER_COLOR")
        windowPanel.movable = true
        windowPanel.resizable = true
        windowPanel.minHeight = windowPanel.header.height.value
        windowPanel.minWidth = 50f
        windowPanel.id = title

        windowPanel.header.addChildren(headerPanel)

        return windowPanel
    }

    /**
     * Creates a menu bar containing buttons with dropdown menus.
     */
    fun createMenuBarUI(vararg buttons: MenuBarButton): UiElement
    {
        val menuBar = HorizontalPanel(height = Size.absolute(25f))
        menuBar.color = style.getColor("BG_COLOR")
        menuBar.strokeColor = style.getColor("HEADER_COLOR")
        menuBar.addChildren(*buttons.map { createMenuBarButtonUI(it) }.toTypedArray(), Panel())
        return menuBar
    }

    data class MenuBarButton(val labelText: String, val items: List<MenuBarItem>)
    data class MenuBarItem(val labelText: String, val onClick: () -> Unit)

    /**
     * Creates a menu button with a dropdown.
     */
    private fun createMenuBarButtonUI(menuBarButton: MenuBarButton): UiElement
    {
        val font = style.getFont()
        val fontSize = 18f
        val menu = DropdownMenu<MenuBarItem>(
            width = Size.absolute(55f),
            dropDownWidth = Size.absolute(menuBarButton.items.map { font.getCharacterWidths(it.labelText, fontSize).sum() + 30f }.max() ?: 100f ),
            dropDownHeight = Size.absolute(menuBarButton.items.size * (35f))
        ).apply {
            showArrow = false
            useSelectedItemAsMenuLabel = false
            bgColor = Color(0f, 0f, 0f, 0f)
            bgColorHover = style.getColor("TILE_COLOR_HOVER")
            menuLabel.text = menuBarButton.labelText
            menuLabel.fontSize = fontSize
            menuLabel.padding.left = 12f
            menuLabel.padding.top = 10f
            menuLabel.font = style.getFont()
            rowPanel.rowHeight = 25f
            rowPanel.rowPadding = 5f
            dropdown.color = style.getColor("BG_COLOR")
            dropdown.strokeColor = style.getColor("HEADER_COLOR")
            dropdown.minHeight = 0f
            dropdown.resizable = false
            scrollbar.hidden = true
            setOnItemToString { it.labelText }
            menuBarButton.items.forEach { addItem(it) }
            setOnItemChanged { it.onClick() }
        }

        return menu
    }

    /**
     * Creates a [DropdownMenu] with a generic type.
     */
    private fun <T> createDropdownUI(selectedItem: T, items: List<T>, onItemToString: (T) -> String): DropdownMenu<T>
    {
        val dropdown = DropdownMenu<T>(
            width = Size.relative(0.5f),
            dropDownWidth = Size.absolute(250f),
            dropDownHeight = Size.absolute(180f)
        )

        dropdown.padding.top = 5f
        dropdown.padding.bottom = 5f
        dropdown.padding.right = 5f
        dropdown.rowPanel.rowPadding = 5f
        dropdown.menuLabel.font = style.getFont()
        dropdown.menuLabel.color = style.getColor("FONT_COLOR")
        dropdown.menuLabel.padding.left = 10f
        dropdown.bgColor = style.getColor("INPUT_COLOR")
        dropdown.bgColorHover = style.getColor("INPUT_COLOR_HOVER_LIGHT")
        dropdown.dropdown.color = style.getColor("BG_COLOR")
        dropdown.scrollbar.sliderColor = style.getColor("HEADER_COLOR")
        dropdown.scrollbar.sliderColorHover = style.getColor("HEADER_COLOR_HOVER_DARK")
        dropdown.scrollbar.bgColor = style.getColor("TILE_COLOR")
        dropdown.setOnItemToString(onItemToString)
        dropdown.selectedItem = selectedItem
        items.forEach(dropdown::addItem)

        return dropdown
    }

    /**
     * Creates an panel containing all loaded texture assets.
     */
    fun createAssetPanelUI(engine: PulseEngine, onAssetClicked: (Texture) -> Unit): UiElement
    {
        val tilePanel = TilePanel()
        tilePanel.horizontalTiles = 5
        tilePanel.maxTileSize = 80f
        tilePanel.tilePadding = 5f

        val textureAssets = engine.asset.getAll(Texture::class.java)
        for (texture in textureAssets)
        {
            val tile = Button()
            tile.bgColor = style.getColor("TILE_COLOR")
            tile.bgColorHover = style.getColor("TILE_COLOR_HOVER")
            tile.textureScale = 0.9f
            tile.texture = texture
            tile.setOnClicked { onAssetClicked(texture) }
            tilePanel.addChildren(tile)
        }

        return createScrollableSectionUI(tilePanel)
    }

    /**
     * Creates a UI panel with a horizontally aligned scrollbar.
     */
    fun createScrollableSectionUI(panel: UiElement): HorizontalPanel
    {
        if (panel !is Scrollable)
            throw IllegalArgumentException("${panel::class.simpleName} is not Scrollable")

        val hPanel = HorizontalPanel()
        hPanel.color = style.getColor("BG2_COLOR")
        hPanel.addChildren(panel, createScrollbarUI(panel))

        return hPanel
    }

    /**
     * Creates a default [Scrollbar] UI element.
     */
    private fun createScrollbarUI(scrollBinding: Scrollable): Scrollbar
    {
        val scrollbar = Scrollbar(width = Size.absolute(20f))
        scrollbar.bgColor = style.getColor("TILE_COLOR")
        scrollbar.sliderColor = style.getColor("HEADER_COLOR")
        scrollbar.sliderColorHover = style.getColor("HEADER_COLOR_HOVER_DARK")
        scrollbar.padding.top = 5f
        scrollbar.padding.bottom = 5f
        scrollbar.padding.right = 5f
        scrollbar.sliderPadding = 3f
        scrollbar.bind(scrollBinding)
        return scrollbar
    }

    /**
     * Creates a property row UI element for the given entity with a dropdown of [SceneEntity] types.
     */
    fun createEntityTypePropertyUI(entity: SceneEntity, onItemChange: (KClass<out SceneEntity>) -> Unit): UiElement
    {
        val typeLabel = Label("Entity type", width = Size.relative(0.5f))
        typeLabel.padding.setAll(5f)
        typeLabel.padding.left = 10f
        typeLabel.font = style.getFont()
        typeLabel.color = style.getColor("FONT_COLOR")

        val typeDropdown = createDropdownUI(entity::class, SceneEntity.REGISTERED_TYPES.toList()) { it.simpleName ?: "NO NAME" }
        typeDropdown.setOnItemChanged(onItemChange)

        val typeHPanel = HorizontalPanel()
        typeHPanel.padding.left = 5f
        typeHPanel.padding.right = 5f
        typeHPanel.padding.top = 5f
        typeHPanel.color = style.getColor("TILE_COLOR")
        typeHPanel.addChildren(typeLabel, Panel(), typeDropdown)

        return typeHPanel
    }

    /**
     * Creates a property row UI element for the given entity.
     * Returns the main UI panel and the input field / dropdown menu
     */
    fun createEntityPropertyUI(entity: SceneEntity, prop: KMutableProperty<*>): Pair<HorizontalPanel, UiElement>
    {
        val propValue = prop.getter.call(entity)?.toString() ?: "nan"
        val propType = prop.javaField?.type

        val propUi: UiElement = if (propType?.kotlin?.isSubclassOf(Enum::class) == true)
        {
            val items = propType.enumConstants?.toList() ?: emptyList()
            val dropdownMenu = createDropdownUI(prop.getter.call(entity), items) { it.toString() }
            dropdownMenu.setOnItemChanged { it?.let { setEntityProperty(entity, prop.name, it) } }
            dropdownMenu
        }
        else
        {
            val contentType = when (propType)
            {
                Float::class.java, Double::class.java -> FLOAT
                Int::class.java, Long::class.java, Char::class.java -> INTEGER
                Boolean::class.java -> BOOLEAN
                else -> TEXT
            }

            val inputField = InputField(propValue, width = Size.relative(0.5f))
            inputField.padding.top = 5f
            inputField.padding.bottom = 5f
            inputField.padding.right = 5f
            inputField.font = style.getFont()
            inputField.fontColor = style.getColor("FONT_COLOR")
            inputField.bgColor = style.getColor("INPUT_COLOR")
            inputField.editable = true
            inputField.contentType = contentType
            inputField.setOnTextChanged { if (it.isValid) setEntityProperty(entity, prop, it.text) }

            if (contentType == FLOAT || contentType == INTEGER)
                prop.findAnnotation<ValueRange>()?.let {
                    inputField.numberMinVal = it.min
                    inputField.numberMaxVal = it.max
                }

            inputField
        }

        val label = Label(prop.name, width = Size.relative(0.5f))
        label.padding.setAll(5f)
        label.padding.left = 10f
        label.font = style.getFont()
        label.color = style.getColor("FONT_COLOR")

        val hPanel = HorizontalPanel()
        hPanel.padding.left = 5f
        hPanel.padding.right = 5f
        hPanel.padding.top = 5f
        hPanel.color = style.getColor("TILE_COLOR")
        hPanel.addChildren(label, propUi)

        return Pair(hPanel, propUi)
    }

    /**
     * Parses the given string value into a the class type given by the [KMutableProperty].
     * Sets the named property of the entity to the parsed value.
     */
    private fun setEntityProperty(entity: SceneEntity, property: KMutableProperty<*>, value: String) =
        try
        {
            when (property.javaField?.type)
            {
                String::class.java  -> value
                Int::class.java     -> value.toIntOrNull()
                Float::class.java   -> value.toFloatOrNull()
                Double::class.java  -> value.toDoubleOrNull()
                Long::class.java    -> value.toLongOrNull()
                Boolean::class.java -> value.toBoolean()
                else                -> null
            }?.let { property.setter.call(entity, it) }
        }
        catch (e: Exception) { Logger.error("Failed to parse value: $value into required type: ${property.javaField?.type}, reason: ${e.message}") }

    /**
     * Sets the named property of the entity to the given value.
     */
    fun setEntityProperty(entity: SceneEntity, name: String, value: Any)
    {
        val prop = entity::class.memberProperties.find { it.name == name }
        if (prop != null && prop is KMutableProperty<*>)
        {
            try { prop.setter.call(entity, value) }
            catch (e: Exception) { Logger.error("Failed to set property with name: $name, reason: ${e.message}") }
        }
    }
}