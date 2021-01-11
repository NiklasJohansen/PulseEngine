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
        colors["LABEL"] = Color(222, 224, 228)
        colors["BG_LIGHT"] = Color(59 / 2, 63 / 2, 67 / 2)
        colors["BG_DARK"] = Color(54 / 2, 58 / 2, 62 / 2)
        colors["HEADER"] = Color(46 / 2, 48 / 2, 51 / 2)
        colors["HEADER_HOVER"] = Color(42 * 3, 44, 47)
        colors["BUTTON"] = Color(41 / 2, 43 / 2, 46 / 2)
        colors["BUTTON_HOVER"] = Color(56 / 4 * 3, 57 / 4 * 3, 60 /  4 * 3)
        colors["ITEM"] = Color(52 / 2, 53 / 2, 56 / 2)
        colors["ITEM_HOVER"] = Color(56 / 3 * 2, 57 / 3 * 2, 60 / 3 * 2)
    }

    /**
     * Creates a movable and resizable window panel.
     */
    fun createWindowUI(title: String): WindowPanel
    {
        val windowPanel = WindowPanel(
            x = Position.fixed(0f),
            y = Position.fixed(20f),
            width = Size.absolute(300f),
            height = Size.absolute(200f)
        )

        val label = Label(title).apply {
            padding.left = 10f
            fontSize = 22f
            font = style.getFont()
            color = style.getColor("LABEL")
            focusable = false
        }

        val xLabel = Label("x").apply {
            focusable = false
            fontSize = 20f
            padding.top = 0f
            padding.left = 6f
            color = style.getColor("LABEL")
        }

        val exitButton = Button(width = Size.absolute(20f), height = Size.absolute(20f)).apply {
            padding.top = 5f
            padding.right = 5f
            color = Color.BLANK
            hoverColor = style.getColor("BUTTON_HOVER")
            setOnClicked { windowPanel.parent?.removeChild(windowPanel) }
            addChildren(xLabel)
        }

        val headerPanel = HorizontalPanel(height = Size.absolute(30f)).apply {
            color = style.getColor("HEADER")
            focusable = false
            addChildren(label, exitButton)
        }

        windowPanel.color = style.getColor("BG_LIGHT")
        windowPanel.strokeColor = style.getColor("HEADER")
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
        return HorizontalPanel(height = Size.absolute(25f)).apply {
            color = style.getColor("BG_LIGHT")
            strokeColor = style.getColor("HEADER")
            addChildren(*buttons.map { createMenuBarButtonUI(it) }.toTypedArray(), Panel())
        }
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
        return DropdownMenu<MenuBarItem>(
            width = Size.absolute(55f),
            dropDownWidth = Size.absolute(menuBarButton.items.map { font.getCharacterWidths(it.labelText, fontSize).sum() + 30f }.max() ?: 100f ),
            dropDownHeight = Size.absolute(5f + menuBarButton.items.size * 30)
        ).apply {
            showArrow = false
            useSelectedItemAsMenuLabel = false
            bgColor = Color.BLANK
            bgHoverColor = style.getColor("BUTTON_HOVER")
            itemBgColor = Color.BLANK
            itemBgHoverColor = style.getColor("BUTTON_HOVER")
            menuLabel.text = menuBarButton.labelText
            menuLabel.fontSize = fontSize
            menuLabel.padding.left = 12f
            menuLabel.padding.top = 7f
            menuLabel.font = style.getFont()
            rowPanel.rowHeight = 25f
            rowPanel.rowPadding = 5f
            dropdown.color = style.getColor("BG_LIGHT")
            dropdown.strokeColor = style.getColor("HEADER")
            dropdown.minHeight = 0f
            dropdown.resizable = false
            scrollbar.hidden = true
            setOnItemToString { it.labelText }
            menuBarButton.items.forEach { addItem(it) }
            setOnItemChanged { it.onClick() }
        }
    }

    /**
     * Creates a [DropdownMenu] with a generic type.
     */
    private fun <T> createDropdownUI(selectedItem: T, items: List<T>, onItemToString: (T) -> String): DropdownMenu<T>
    {
        return DropdownMenu<T>(
            width = Size.relative(0.5f),
            dropDownWidth = Size.absolute(250f),
            dropDownHeight = Size.absolute(180f)
        ).apply {
            padding.top = 5f
            padding.bottom = 5f
            padding.right = 5f
            rowPanel.rowPadding = 5f
            menuLabel.font = style.getFont()
            menuLabel.fontSize = 20f
            menuLabel.color = style.getColor("LABEL")
            menuLabel.padding.left = 10f
            bgColor = style.getColor("BUTTON")
            bgHoverColor = style.getColor("BUTTON_HOVER")
            dropdown.color = style.getColor("BG_LIGHT")
            itemBgColor = style.getColor("ITEM")
            itemBgHoverColor = style.getColor("ITEM_HOVER")
            scrollbar.sliderColor = style.getColor("BUTTON")
            scrollbar.sliderColorHover = style.getColor("BUTTON_HOVER")
            scrollbar.bgColor = style.getColor("ITEM")

            setOnItemToString(onItemToString)
            this.selectedItem = selectedItem
            items.forEach(this::addItem)
        }
    }

    /**
     * Creates an panel containing all loaded texture assets.
     */
    fun createAssetPanelUI(engine: PulseEngine, onAssetClicked: (Texture) -> Unit): UiElement
    {
        val tilePanel = TilePanel().apply {
            horizontalTiles = 5
            maxTileSize = 80f
            tilePadding = 5f
        }

        val textureAssets = engine.asset.getAll(Texture::class.java)
        for (tex in textureAssets)
        {
            val tile = Button().apply {
                bgColor = style.getColor("ITEM")
                bgHoverColor = style.getColor("ITEM_HOVER")
                textureScale = 0.9f
                texture = tex
                setOnClicked { onAssetClicked(tex) }
            }
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

        return HorizontalPanel().apply {
            color = style.getColor("BG_DARK")
            addChildren(panel, createScrollbarUI(panel))
        }
    }

    /**
     * Creates a default [Scrollbar] UI element.
     */
    private fun createScrollbarUI(scrollBinding: Scrollable): Scrollbar
    {
        return Scrollbar(width = Size.absolute(20f)).apply {
            bgColor = style.getColor("ITEM")
            sliderColor = style.getColor("BUTTON")
            sliderColorHover = style.getColor("BUTTON_HOVER")
            padding.top = 5f
            padding.bottom = 5f
            padding.right = 5f
            sliderPadding = 3f
            bind(scrollBinding)
        }
    }

    /**
     * Creates a [ColorPicker] UI element.
     */
    private fun createColorPickerUI(color: Color) =
        ColorPicker(color).apply {
            padding.setAll(5f)
            bgColor = style.getColor("BUTTON")
            hexInput.fontSize = 20f
            hexInput.fontColor = style.getColor("FONT_COLOR")
            colorEditor.color = style.getColor("BG_LIGHT")
            colorEditor.strokeColor = style.getColor("HEADER")
            saturationBrightnessPicker.strokeColor = style.getColor("HEADER")
            huePicker.strokeColor = style.getColor("HEADER")
            hsbSection.color = style.getColor("ITEM")
            rgbaSection.color = style.getColor("ITEM")
            listOf(redInput, greenInput, blueInput, alphaInput).forEach {
                it.fontSize = 20f
                it.fontColor = style.getColor("FONT_COLOR")
                it.bgColor = style.getColor("BUTTON")
            }
        }

    private fun createInputFieldUI(value: Any?, prop: KMutableProperty<*>): InputField
    {
        val type = when (prop.javaField?.type)
        {
            Float::class.java, Double::class.java -> FLOAT
            Int::class.java, Long::class.java, Char::class.java -> INTEGER
            Boolean::class.java -> BOOLEAN
            else -> TEXT
        }

        return InputField(
            defaultText = value?.toString() ?: "",
            width = Size.relative(0.5f)
        ).apply {
            padding.top = 5f
            padding.bottom = 5f
            padding.right = 5f
            font = style.getFont()
            fontSize = 20f
            fontColor = style.getColor("LABEL")
            bgColor = style.getColor("BUTTON")
            editable = true
            contentType = type

            if (type == FLOAT || type == INTEGER)
            {
                prop.findAnnotation<ValueRange>()?.let {
                    numberMinVal = it.min
                    numberMaxVal = it.max
                }
            }
        }
    }

    /**
     * Creates a property row UI element for the given entity with a dropdown of [SceneEntity] types.
     */
    fun createEntityTypePropertyUI(entity: SceneEntity, onItemChange: (KClass<out SceneEntity>) -> Unit): UiElement
    {
        val typeLabel = Label("Entity type", width = Size.relative(0.5f)).apply {
            padding.setAll(5f)
            padding.left = 10f
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val typeDropdown = createDropdownUI(entity::class, SceneEntity.REGISTERED_TYPES.toList()) { it.simpleName ?: "NO NAME" }
        typeDropdown.setOnItemChanged(onItemChange)

        return HorizontalPanel().apply {
            padding.left = 5f
            padding.right = 5f
            padding.top = 5f
            color = style.getColor("ITEM")
            addChildren(typeLabel, Panel(), typeDropdown)
        }
    }

    /**
     * Creates a property row UI element for the given entity.
     * Returns the main UI panel and the input UiElement
     */
    fun createEntityPropertyUI(entity: SceneEntity, prop: KMutableProperty<*>): Pair<HorizontalPanel, UiElement>
    {
        val propType = prop.javaField?.type
        val propUi: UiElement = when
        {
            propType?.kotlin?.isSubclassOf(Enum::class) == true ->
            {
                val items = propType.enumConstants?.toList() ?: emptyList()
                createDropdownUI(prop.getter.call(entity), items, { it.toString() }).apply {
                    setOnItemChanged { it?.let { setEntityProperty(entity, prop.name, it) } }
                }
            }
            propType?.kotlin?.isSubclassOf(Boolean::class) == true ->
            {
                createDropdownUI(prop.getter.call(entity), listOf(true, false), { it.toString() }).apply {
                    setOnItemChanged { it?.let { setEntityProperty(entity, prop.name, it) } }
                }
            }
            propType?.kotlin?.isSubclassOf(Color::class) == true ->
            {
                val color = prop.getter.call(entity) as Color
                createColorPickerUI(color)
            }
            else ->
            {
                val value = prop.getter.call(entity)
                createInputFieldUI(value, prop).apply {
                    setOnTextChanged { if (it.isValid) setEntityProperty(entity, prop, it.text) }
                }
            }
        }

        val label = Label(prop.name.capitalize(), width = Size.relative(0.5f)).apply {
            padding.setAll(5f)
            padding.left = 10f
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val hPanel = HorizontalPanel().apply {
            padding.left = 5f
            padding.right = 5f
            padding.top = 5f
            color = style.getColor("ITEM")
            addChildren(label, propUi)
        }

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