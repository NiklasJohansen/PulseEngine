package no.njoh.pulseengine.widgets.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.modules.gui.*
import no.njoh.pulseengine.modules.gui.ScrollDirection.*
import no.njoh.pulseengine.modules.gui.elements.*
import no.njoh.pulseengine.modules.gui.elements.InputField.ContentType.*
import no.njoh.pulseengine.modules.gui.layout.*
import no.njoh.pulseengine.widgets.editor.EditorUtil.getName
import no.njoh.pulseengine.widgets.editor.EditorUtil.getPropInfo
import no.njoh.pulseengine.widgets.editor.EditorUtil.isEditable
import no.njoh.pulseengine.widgets.editor.EditorUtil.setArrayProperty
import no.njoh.pulseengine.widgets.editor.EditorUtil.setPrimitiveProperty
import java.lang.IllegalArgumentException
import kotlin.math.min
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Factory for building [UiElement]s used in the [SceneEditor].
 */
open class UiElementFactory(
    val style: EditorStyle = EditorStyle()
) {
    /** Property UI factory functions for specific class types. */
    val propertyUiFactories = mutableMapOf(
        Enum::class to ::createEnumPropertyUi,
        Boolean::class to ::createBooleanPropertyUi,
        Color::class to { obj, prop, onChanged -> createColorPickerUI(color = prop.getter.call(obj) as Color) },
        LongArray::class to ::createNumberArrayPropertyUi,
        IntArray::class to ::createNumberArrayPropertyUi,
        ShortArray::class to ::createNumberArrayPropertyUi,
        ByteArray::class to ::createNumberArrayPropertyUi,
        FloatArray::class to ::createNumberArrayPropertyUi,
        DoubleArray::class to ::createNumberArrayPropertyUi,
        String::class to { _, _, _ -> null } // Factory functions returning null will use default InputField UI
    )

    /**
     * Creates a [DropdownMenu] containing all the enum constants.
     */
    open fun createEnumPropertyUi(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ) = createItemSelectionDropdownUI(
        selectedItem = prop.getter.call(obj),
        items = prop.javaField?.type?.enumConstants?.toList() ?: emptyList(),
        onItemToString = { it.toString() },
        onItemChanged = { lastValue, newValue ->
            obj.setPrimitiveProperty(prop.name, newValue)
            onChanged(prop.name, lastValue, newValue)
        }
    )

    /**
     * Creates a [DropdownMenu] containing TRUE and FALSE.
     */
    open fun createBooleanPropertyUi(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ) = createItemSelectionDropdownUI(
        selectedItem = prop.getter.call(obj),
        items = listOf(true, false),
        onItemToString = { it.toString().capitalize() },
        onItemChanged = { lastValue, newValue ->
            obj.setPrimitiveProperty(prop.name, newValue)
            onChanged(prop.name, lastValue, newValue)
        }
    )

    /**
     * Creates a [InputField] for editing an array of primitive numbers.
     */
    open fun createNumberArrayPropertyUi(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ) = createInputFieldUI(obj, prop).apply {
        setOnValidTextChanged {
            obj.setArrayProperty(prop, it.text)
            onChanged(prop.name, it.lastValidText, it.text)
        }
        editable = obj.getPropInfo(prop)?.editable ?: true
    }

    /**
     * Creates a movable and resizable window panel.
     */
    open fun createWindowUI(
        title: String,
        iconName: String = "",
        x: Float = 0f,
        y: Float = 20f,
        width: Float = 350f,
        height: Float = 200f,
        onClosed: () -> Unit = { }
    ): WindowPanel {
        val windowPanel = WindowPanel(
            x = Position.fixed(x),
            y = Position.fixed(y),
            width = Size.absolute(width),
            height = Size.absolute(height)
        )

        val icon = Icon(width = Size.absolute(15f)).apply {
            padding.left = 10f
            iconSize = 18f
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon(iconName)
            color = style.getColor("LABEL")
        }

        val label = Label(title).apply {
            padding.left = 10f
            fontSize = 20f
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val crossIcon = Icon(width = Size.absolute(15f)).apply {
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon("CROSS")
            color = style.getColor("LABEL")
            padding.top = 2f
        }

        val exitButton = Button(width = Size.absolute(20f), height = Size.absolute(20f)).apply {
            padding.top = 5f
            padding.right = 5f
            cornerRadius = 4f
            color = Color.BLANK
            hoverColor = style.getColor("BUTTON_EXIT")
            setOnClicked {
                windowPanel.parent?.removeChildren(windowPanel)
                onClosed()
            }
            addChildren(crossIcon)
        }

        val headerPanel = HorizontalPanel(height = Size.absolute(30f)).apply {
            color = style.getColor("HEADER")
            focusable = false
            addChildren(icon, label, exitButton)
        }

        windowPanel.color = style.getColor("BG_LIGHT")
        windowPanel.strokeColor = style.getColor("STROKE")
        windowPanel.movable = true
        windowPanel.resizable = true
        windowPanel.minHeight = windowPanel.header.height.value + 100
        windowPanel.minWidth = 150f
        windowPanel.id = title
        windowPanel.header.addChildren(headerPanel)

        return windowPanel
    }

    /**
     * Creates a menu bar containing buttons with dropdown menus.
     */
    open fun createMenuBarUI(vararg buttons: MenuBarButton): UiElement =
        HorizontalPanel(height = Size.absolute(25f)).apply {
            color = style.getColor("BG_LIGHT")
            strokeColor = style.getColor("STROKE")
            addChildren(
                *buttons.map { createMenuBarButtonUI(it, 18f, false) }.toTypedArray(), Panel()
            )
        }

    /**
     * Creates a menu button with a dropdown.
     */
    open fun createMenuBarButtonUI(
        menuBarButton: MenuBarButton,
        fontSize: Float,
        showScrollbar: Boolean = true
    ): DropdownMenu<MenuBarItem> {
        val font = style.getFont()
        val scrollBarWidth = if (showScrollbar) 25f else 0f
        val maxItemCount = if (showScrollbar) 8 else 100
        val items = menuBarButton.items.map { it.labelText }
        val dropdownRowHeight = style.getSize("DROPDOWN_ROW_HEIGHT")
        val dropdownRowPadding = 5f
        val (width, height) = getDropDownDimensions(font, fontSize, scrollBarWidth, dropdownRowHeight + dropdownRowPadding, maxItemCount, items)
        return DropdownMenu<MenuBarItem>(
            width = Size.absolute(55f),
            dropDownWidth = Size.absolute(width),
            dropDownHeight = Size.absolute(height)
        ).apply {
            showArrow = false
            useSelectedItemAsMenuLabel = false
            bgColor = Color.BLANK
            bgHoverColor = style.getColor("BUTTON_HOVER")
            itemBgColor = Color.BLANK
            itemBgHoverColor = style.getColor("BUTTON_HOVER")
            rowHeight = dropdownRowHeight
            rowPadding = dropdownRowPadding
            menuLabel.text = menuBarButton.labelText
            menuLabel.fontSize = fontSize
            menuLabel.font = font
            menuLabel.centerHorizontally = true
            menuLabel.centerVertically = true
            menuLabel.font = style.getFont()
            dropdown.color = style.getColor("BG_LIGHT")
            dropdown.cornerRadius = 5f
            dropdown.minHeight = 0f
            dropdown.minWidth = 10f
            dropdown.resizable = false
            scrollbar.bgColor = style.getColor("ITEM")
            scrollbar.sliderColor = style.getColor("BUTTON")
            scrollbar.sliderColorHover = style.getColor("BUTTON_HOVER")
            scrollbar.hidden = !showScrollbar
            scrollbar.cornerRadius = 8f
            setOnItemToString { it.labelText }
            menuBarButton.items.forEach { addItem(it) }
            setOnItemChanged { _, item -> item.onClick() }
        }
    }

    /**
     * Creates a [DropdownMenu] with a generic type.
     */
    open fun <T> createItemSelectionDropdownUI(
        selectedItem: T,
        items: List<T>,
        onItemToString: (T) -> String,
        onItemChanged: (lastValue: T?, newValue: T) -> Unit
    ): DropdownMenu<T> {
        val fontSize = 18f
        val font = style.getFont()
        val showScrollbar = items.size > 8
        val scrollBarWidth = if (showScrollbar) 25f else 0f
        val stringItems = items.map { onItemToString(it) }
        val (width, height) = getDropDownDimensions(font, fontSize, scrollBarWidth, 35f, 8, stringItems)
        return DropdownMenu<T>(
            width = Size.relative(0.5f),
            dropDownWidth = Size.absolute(width),
            dropDownHeight = Size.absolute(height)
        ).apply {
            padding.top = 5f
            padding.bottom = 5f
            padding.right = 5f
            rowHeight = style.getSize("DROPDOWN_ROW_HEIGHT")
            menuLabel.font = font
            menuLabel.fontSize = fontSize
            menuLabel.color = style.getColor("LABEL")
            menuLabel.padding.left = 10f
            cornerRadius = 8f
            bgColor = style.getColor("BUTTON")
            bgHoverColor = style.getColor("BUTTON_HOVER")
            itemBgColor = Color.BLANK
            itemBgHoverColor = style.getColor("BUTTON_HOVER")
            dropdown.color = style.getColor("BG_DARK")
            dropdown.strokeColor = Color.BLANK
            dropdown.cornerRadius = 5f
            scrollbar.bgColor = style.getColor("ITEM")
            scrollbar.sliderColor = style.getColor("BUTTON")
            scrollbar.sliderColorHover = style.getColor("BUTTON_HOVER")
            scrollbar.hidden = !showScrollbar
            scrollbar.cornerRadius = 8f
            setOnItemToString(onItemToString)
            setOnItemChanged(onItemChanged)
            this.selectedItem = selectedItem
            items.forEach(this::addItem)
        }
    }

    /**
     * Creates a panel containing all loaded texture assets.
     */
    open fun createAssetPanelUI(engine: PulseEngine, onAssetClicked: (Texture) -> Unit): UiElement
    {
        val tilePanel = TilePanel().apply {
            horizontalTiles = 5
            maxTileSize = 80f
            tilePadding = 5f
        }

        val textureAssets = engine.asset.getAllOfType<Texture>()
        for (tex in textureAssets)
        {
            val tile = Button().apply {
                bgColor = style.getColor("ITEM")
                bgHoverColor = style.getColor("ITEM_HOVER")
                color = Color.WHITE
                hoverColor = Color.WHITE
                textureScale = 0.9f
                cornerRadius = 20f
                texture = tex
                setOnClicked { onAssetClicked(tex) }
            }
            tilePanel.addChildren(tile)
        }

        return createScrollableSectionUI(tilePanel)
    }

    /**
     * Creates a [Surface2D] viewport.
     */
    open fun createViewportUI(engine: PulseEngine): VerticalPanel
    {
        val image = Image()
        image.bgColor = Color.BLACK
        image.texture = engine.gfx.mainSurface.getTexture()

        val surfaceSelector = createItemSelectionDropdownUI(
            selectedItem = engine.gfx.mainSurface.name,
            items = engine.gfx.getAllSurfaces().flatMap { it.getTextures().mapIndexed { i, tex -> "${it.name}  (${tex.name})  #$i" } },
            onItemToString = { it },
            onItemChanged = { _, surfaceName ->
                val surface = surfaceName.substringBefore("  (")
                val index = surfaceName.substringAfterLast("#").toIntOrNull() ?: 0
                image.texture = engine.gfx.getSurface(surface)?.getTexture(index)
            }
        ).apply {
            width.updateType(Size.ValueType.AUTO)
            height.updateType(Size.ValueType.ABSOLUTE)
            height.value = 30f
            padding.setAll(0f)
            bgColor = image.bgColor
            setOnClicked {
                val selected = selectedItem
                clearItems()
                engine.gfx.getAllSurfaces().forEachFast { surface ->
                    surface.getTextures().forEachIndexed { i, tex -> addItem("${surface.name}  (${tex.name})  #$i") }
                }
                selectedItem = selected
            }
        }

        return VerticalPanel().apply { addChildren(surfaceSelector, image) }
    }

    /**
     * Creates the properties panel for [SceneSystem]s
     */
    open fun createSystemPropertiesPanelUI(engine: PulseEngine, propertiesRowPanel: RowPanel): HorizontalPanel
    {
        val menuItems = SceneSystem.REGISTERED_TYPES.map {
            MenuBarItem(it.simpleName ?: "") {
                val newSystem = it.createInstance()
                newSystem.init(engine)
                engine.scene.addSystem(newSystem)
                val props = createSystemProperties(newSystem, isHidden = false, onClose = { props ->
                    newSystem.onDestroy(engine)
                    engine.scene.removeSystem(newSystem)
                    propertiesRowPanel.removeChildren(*props.toTypedArray())
                })
                propertiesRowPanel.addChildren(*props.toTypedArray())
            }
        }

        val button = MenuBarButton(labelText = "+", items = menuItems)
        val showScrollBar = menuItems.size > 8
        val buttonUI = createMenuBarButtonUI(button, 18f, showScrollBar).apply()
        {
            width.setQuiet(Size.absolute(40f))
            height.setQuiet(Size.absolute(40f))
            dropdown.resizable = true
            dropdown.color = style.getColor("BUTTON")
            menuLabel.fontSize = 40f
            menuLabel.padding.top = 4f
            menuLabel.padding.right = 2f
            padding.setAll(5f)
            bgColor = style.getColor("HEADER")
            hoverColor = style.getColor("BUTTON_HOVER")
            cornerRadius = 40f
        }

        return HorizontalPanel().apply()
        {
            color = style.getColor("BG_DARK")
            cornerRadius = 4f
            addChildren(
                VerticalPanel().apply()
                {
                    addChildren(propertiesRowPanel, buttonUI)
                },
                createScrollbarUI(propertiesRowPanel, VERTICAL)
            )
        }
    }

    /**
     * Creates a list of property [UiElement]s for the given [SceneSystem].
     */
    open fun createSystemProperties(system: SceneSystem, isHidden: Boolean, onClose: (props: List<UiElement>) -> Unit): List<UiElement>
    {
        val icon = system::class.findAnnotation<ScnIcon>()
        val headerIcon = Icon(width = Size.absolute(30f))
        headerIcon.iconFontName = style.iconFontName
        headerIcon.iconCharacter = style.getIcon(icon?.iconName ?: "COG")
        headerIcon.iconSize = 15f

        val headerText = system::class.findAnnotation<Name>()?.name
            ?: (system::class.java.simpleName ?: "")
                .split("(?=[A-Z])".toRegex())
                .joinToString(" ")
                .trim()

        val headerLabel = Label(headerText).apply {
            fontSize = 20f
            color = style.getColor("LABEL")
        }

        val crossIcon = Icon(width = Size.absolute(15f)).apply {
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon("CROSS")
            color = style.getColor("LABEL")
            padding.top = 3f
        }

        val exitButton = Button(width = Size.absolute(20f)).apply {
            padding.setAll(5f)
            color = Color.BLANK
            cornerRadius = 7.5f
            hoverColor = style.getColor("BUTTON_EXIT")
            addChildren(crossIcon)
        }

        val headerPanel = HorizontalPanel().apply {
            focusable = false
            color = Color.BLANK
            addChildren(headerIcon, headerLabel, exitButton)
        }

        val headerButton = Button(
            height = Size.absolute(style.getSize("PROP_HEADER_ROW_HEIGHT"))
        ).apply {
            id = system::class.simpleName
            toggleButton = true
            isPressed = isHidden
            padding.left = 5f
            padding.right = 5f
            padding.top = 5f
            cornerRadius = 12f
            color = style.getColor("HEADER")
            activeColor = style.getColor("HEADER")
            hoverColor = style.getColor("HEADER_HOVER")
            addChildren(headerPanel)
        }

        val nopCallback = { _: String, _: Any?, _: Any? -> }
        val props = system::class.memberProperties
            .filter { it is KMutableProperty<*> && it.isEditable() && system.getPropInfo(it)?.hidden != true }
            .sortedBy { system.getPropInfo(it)?.i ?: 1000 }
            .map { prop ->
                val (panel, _) = createPropertyUI(system, prop as KMutableProperty<*>, nopCallback)
                panel.apply {
                    padding.left = 10f
                    padding.right = 10f
                    hidden = isHidden
                }
            }

        val uiElements = listOf(headerButton).plus(props)

        headerButton.setOnClicked { btn -> props.forEach { it.hidden = btn.isPressed } }
        exitButton.setOnClicked { onClose(uiElements) }

        return uiElements
    }

    /**
     * Creates a UI panel with a horizontally and/or vertically aligned scrollbar.
     */
    open fun createScrollableSectionUI(scrollablePanel: UiElement): Panel
    {
        if (scrollablePanel !is Scrollable)
            throw IllegalArgumentException("${scrollablePanel::class.simpleName} is not Scrollable")

        var outerPanel: Panel? = null
        if (scrollablePanel is VerticallyScrollable)
        {
            outerPanel = HorizontalPanel()
            outerPanel.addChildren(scrollablePanel, createScrollbarUI(scrollablePanel, direction = VERTICAL))
        }

        if (scrollablePanel is HorizontallyScrollable)
        {
            val scrollBar = createScrollbarUI(scrollablePanel, direction = HORIZONTAL)
            val body = outerPanel ?: scrollablePanel
            outerPanel = VerticalPanel()
            outerPanel.addChildren(body, scrollBar)
        }

        outerPanel!!.color = style.getColor("BG_DARK")
        return outerPanel
    }

    /**
     * Creates a default [Scrollbar] UI element.
     */
    open fun createScrollbarUI(scrollBinding: Scrollable, direction: ScrollDirection): Scrollbar
    {
        val width = if (direction == VERTICAL) Size.absolute(10f) else Size.auto()
        val height = if (direction == HORIZONTAL) Size.absolute(10f) else Size.auto()
        return Scrollbar(width, height).apply {
            bgColor = style.getColor("ITEM")
            sliderColor = style.getColor("BUTTON")
            sliderColorHover = style.getColor("BUTTON_HOVER")
            cornerRadius = 5f
            padding.setAll(2f)
            sliderPadding = 1.5f
            bind(scrollBinding, direction)
        }
    }

    /**
     * Creates a new [ColorPicker] UI element.
     */
    open fun createColorPickerUI(color: Color) =
        ColorPicker(color).apply {
            padding.setAll(5f)
            cornerRadius = 8f
            bgColor = style.getColor("BUTTON")
            hexInput.fontSize = 20f
            hexInput.textColor = style.getColor("LABEL")
            hexInput.bgColorHover = style.getColor("BUTTON_HOVER")
            hexInput.strokeColor = Color.BLANK
            hexInput.cornerRadius = cornerRadius
            colorEditor.color = style.getColor("BG_LIGHT")
            colorEditor.strokeColor = style.getColor("STROKE")
            saturationBrightnessPicker.strokeColor = style.getColor("HEADER")
            huePicker.strokeColor = style.getColor("STROKE")
            hsbSection.color = style.getColor("ITEM")
            rgbaSection.color = style.getColor("ITEM")
            listOf(redInput, greenInput, blueInput, alphaInput).forEach {
                it.fontSize = 20f
                it.textColor = style.getColor("LABEL")
                it.bgColor = style.getColor("BUTTON")
                it.bgColorHover = style.getColor("BUTTON_HOVER")
            }
        }

    /**
     * Creates a new [InputField] UI element.
     */
    open fun createInputFieldUI(obj: Any, prop: KMutableProperty<*>): InputField
    {
        val value = prop.getter.call(obj)
        val (type, defaultText) = when (prop.javaField?.type)
        {
            Float::class.java       -> FLOAT to value?.toString()
            Double::class.java      -> FLOAT to value?.toString()
            Int::class.java         -> INTEGER to value?.toString()
            Long::class.java        -> INTEGER to value?.toString()
            Char::class.java        -> INTEGER to value?.toString()
            Boolean::class.java     -> BOOLEAN to value?.toString()
            FloatArray::class.java  -> FLOAT_ARRAY to (value as? FloatArray?)?.joinToString()
            DoubleArray::class.java -> FLOAT_ARRAY to (value as? DoubleArray?)?.joinToString()
            IntArray::class.java    -> INTEGER_ARRAY to (value as? IntArray?)?.joinToString()
            LongArray::class.java   -> INTEGER_ARRAY to (value as? LongArray?)?.joinToString()
            ShortArray::class.java  -> INTEGER_ARRAY to (value as? ShortArray?)?.joinToString()
            ByteArray::class.java   -> INTEGER_ARRAY to (value as? ByteArray?)?.joinToString()
            else                    -> TEXT to value?.toString()
        }

        return InputField(
            defaultText = defaultText ?: "",
            width = Size.relative(0.5f)
        ).apply {
            padding.top = 5f
            padding.bottom = 5f
            padding.right = 5f
            cornerRadius = 8f
            font = style.getFont()
            fontSize = 18f
            textColor = style.getColor("LABEL")
            bgColor = style.getColor("BUTTON")
            bgColorHover = style.getColor("BUTTON_HOVER")
            strokeColor = Color.BLANK
            contentType = type

            if (type == FLOAT || type == INTEGER)
            {
                obj.getPropInfo(prop)?.let()
                {
                    numberMinVal = it.min
                    numberMaxVal = it.max
                }
            }
        }
    }

    /**
     * Creates a property row UI header for the given [SceneEntity].
     */
    open fun createEntityHeader(entity: SceneEntity): UiElement
    {
        return HorizontalPanel(
            height = Size.absolute(style.getSize("PROP_HEADER_ROW_HEIGHT"))
        ).apply {
            padding.left = 5f
            padding.right = 5f
            padding.top = 5f
            cornerRadius = 12f
            color = style.getColor("HEADER")
            addChildren(
                Icon(width = Size.absolute(25f)).apply()
                {
                    padding.left = 5f
                    iconSize = 15f
                    iconFontName = style.iconFontName
                    iconCharacter = style.getIcon(entity::class.findAnnotation<ScnIcon>()?.iconName ?: "CUBE")
                    color = style.getColor("LABEL")
                },
                Label(
                    text = entity::class.getName(),
                    width = Size.relative(0.5f)
                ).apply {
                    padding.setAll(5f)
                    fontSize = 20f
                    font = style.getFont()
                    color = style.getColor("LABEL")
                },
            )
        }
    }

    /**
     * Creates a property row UI element for the given object.
     * Returns the main UI panel and the input UiElement
     */
    open fun createPropertyUI(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): Pair<HorizontalPanel, UiElement> {
        val propUiKey = propertyUiFactories.keys.firstOrNull { prop.javaField?.type?.kotlin?.isSubclassOf(it) == true }
        val propUi = propertyUiFactories[propUiKey]?.invoke(obj, prop, onChanged)
            ?: createInputFieldUI(obj, prop).apply { // Default UI when no factory exist
                setOnValidTextChanged {
                    obj.setPrimitiveProperty(prop, it.text)
                    onChanged(prop.name, it.lastValidText, it.text)
                }
                editable = obj.getPropInfo(prop)?.editable ?: true
            }

        val label = Label(text = prop.name.capitalize(), width = Size.relative(0.5f)).apply {
            padding.setAll(5f)
            padding.left = 10f
            fontSize = 20f
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val hPanel = HorizontalPanel(
            height = Size.absolute(style.getSize("PROP_ROW_HEIGHT"))
        ).apply {
            padding.left = 5f
            padding.right = 5f
            padding.top = 5f
            cornerRadius = 12f
            color = style.getColor("ITEM")
            addChildren(label, propUi)
        }

        return Pair(hPanel, propUi)
    }

    open fun createCategoryHeader(label: String) =
        HorizontalPanel(
            height = Size.absolute(style.getSize("PROP_HEADER_ROW_HEIGHT"))
        ).apply {
            padding.left = 5f
            padding.right = 5f
            padding.top = 5f
            cornerRadius = 12f
            color = style.getColor("HEADER")
            addChildren(
                Label(label, width = Size.relative(0.5f)).apply {
                    padding.setAll(5f)
                    padding.left = 10f
                    fontSize = 20f
                    font = style.getFont()
                    color = style.getColor("LABEL")
                }
            )
        }

    /**
     * Determines the dropdown menu size based on its content.
     */
    private fun getDropDownDimensions(
        font: Font,
        fontSize: Float,
        scrollBarWidth: Float,
        rowHeight: Float,
        maxItemCount: Int,
        items: List<String>
    ): Pair<Float, Float> {
        val padding = 5f
        val height = padding + min(items.size, maxItemCount) * rowHeight
        val width = 4 * padding + (items.maxOfOrNull { font.getWidth(it, fontSize) } ?: 100f) + scrollBarWidth
        return Pair(width, height)
    }
}