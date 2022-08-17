package no.njoh.pulseengine.widgets.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Scrollable
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.UiElement
import no.njoh.pulseengine.modules.gui.elements.*
import no.njoh.pulseengine.modules.gui.layout.*
import no.njoh.pulseengine.widgets.editor.EditorUtil.getPropInfo
import no.njoh.pulseengine.widgets.editor.EditorUtil.isEditable
import no.njoh.pulseengine.widgets.editor.EditorUtil.setProperty
import java.lang.IllegalArgumentException
import kotlin.math.min
import kotlin.reflect.KClass
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
        Color::class to { obj, prop -> createColorPickerUI(color = prop.getter.call(obj) as Color) },
        String::class to { _: Any, _: KMutableProperty<*> -> null } // Factory functions returning null will use default InputField UI
    )

    /**
     * Creates a [DropdownMenu] containing all the enum constants.
     */
    open fun createEnumPropertyUi(obj: Any, prop: KMutableProperty<*>) =
        createItemSelectionDropdownUI(
            selectedItem = prop.getter.call(obj),
            items = prop.javaField?.type?.enumConstants?.toList() ?: emptyList(),
            onItemToString = { it.toString() }
        ).apply {
            setOnItemChanged { value -> obj.setProperty(prop.name, value) }
        }

    /**
     * Creates a [DropdownMenu] containing TRUE and FALSE.
     */
    open fun createBooleanPropertyUi(obj: Any, prop: KMutableProperty<*>) =
        createItemSelectionDropdownUI(
            selectedItem = prop.getter.call(obj),
            items = listOf(true, false),
            onItemToString = { it.toString().capitalize() }
        ).apply {
            setOnItemChanged { value -> obj.setProperty(prop.name, value) }
        }

    /**
     * Creates a movable and resizable window panel.
     */
    open fun createWindowUI(title: String, x: Float = 0f, y: Float = 20f, width: Float = 300f, height: Float = 200f): WindowPanel
    {
        val windowPanel = WindowPanel(
            x = Position.fixed(x),
            y = Position.fixed(y),
            width = Size.absolute(width),
            height = Size.absolute(height)
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
            padding.top = -3f
            padding.left = 6f
            color = style.getColor("LABEL")
        }

        val exitButton = Button(width = Size.absolute(20f), height = Size.absolute(20f)).apply {
            padding.top = 5f
            padding.right = 5f
            color = Color.BLANK
            hoverColor = style.getColor("BUTTON_EXIT")
            setOnClicked { windowPanel.parent?.removeChildren(windowPanel) }
            addChildren(xLabel)
        }

        val headerPanel = HorizontalPanel(height = Size.absolute(30f)).apply {
            color = style.getColor("HEADER")
            focusable = false
            addChildren(label, exitButton)
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
        val (width, height) = getDropDownDimensions(font, fontSize, scrollBarWidth, 30f, maxItemCount, items)
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
            menuLabel.text = menuBarButton.labelText
            menuLabel.fontSize = fontSize
            menuLabel.font = font
            menuLabel.padding.top = 7f
            menuLabel.centerHorizontally = true
            menuLabel.font = style.getFont()
            rowPanel.rowHeight = 25f
            rowPanel.rowPadding = 5f
            dropdown.color = style.getColor("BG_LIGHT")
            dropdown.strokeColor = style.getColor("STROKE")
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
            setOnItemChanged { it.onClick() }
        }
    }

    /**
     * Creates a [DropdownMenu] with a generic type.
     */
    open fun <T> createItemSelectionDropdownUI(
        selectedItem: T,
        items: List<T>,
        onItemToString: (T) -> String
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
            rowPanel.rowPadding = 5f
            rowPanel.rowHeight = 30f
            menuLabel.font = font
            menuLabel.fontSize = fontSize
            menuLabel.color = style.getColor("LABEL")
            menuLabel.padding.left = 10f
            cornerRadius = 8f
            bgColor = style.getColor("BUTTON")
            bgHoverColor = style.getColor("BUTTON_HOVER")
            itemBgColor = Color.BLANK
            itemBgHoverColor = style.getColor("BUTTON_HOVER")
            dropdown.color = style.getColor("BG_DARK") // BG_LIGHT
            dropdown.strokeColor = style.getColor("STROKE")
            scrollbar.bgColor = style.getColor("ITEM")
            scrollbar.sliderColor = style.getColor("BUTTON")
            scrollbar.sliderColorHover = style.getColor("BUTTON_HOVER")
            scrollbar.hidden = !showScrollbar
            scrollbar.cornerRadius = 8f
            setOnItemToString(onItemToString)
            this.selectedItem = selectedItem
            items.forEach(this::addItem)
        }
    }

    /**
     * Creates an panel containing all loaded texture assets.
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
        image.bgColor = Color(0f, 0f, 0f, 1f)
        image.texture = engine.gfx.mainSurface.getTexture()

        val surfaceSelector = createItemSelectionDropdownUI(
            selectedItem = engine.gfx.mainSurface.name,
            onItemToString = { it },
            items = engine.gfx.getAllSurfaces().flatMap { it.getTextures().mapIndexed { i, tex -> "${it.name}  (${tex.name})  #$i" } }
        ).apply {
            width.updateType(Size.ValueType.AUTO)
            height.updateType(Size.ValueType.ABSOLUTE)
            height.value = 30f
            padding.setAll(0f)
            bgColor = image.bgColor
            setOnItemChanged { surfaceName ->
                val surface = surfaceName.substringBefore("  (")
                val index = surfaceName.substringAfterLast("#").toIntOrNull() ?: 0
                image.texture = engine.gfx.getSurface(surface)?.getTexture(index)
            }
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
        val buttonUI = createMenuBarButtonUI(button, 20f, showScrollBar).apply {
            width.setQuiet(Size.absolute(40f))
            height.setQuiet(Size.absolute(40f))
            dropdown.resizable = true
            menuLabel.fontSize = 40f
            menuLabel.padding.left = 6f
            menuLabel.padding.top = 6f
            padding.setAll(5f)
            bgColor = style.getColor("HEADER")
            hoverColor = style.getColor("BUTTON_HOVER")
            cornerRadius = 40f
        }

        return HorizontalPanel().apply {
            color = style.getColor("BG_DARK")
            cornerRadius = 4f
            addChildren(
                VerticalPanel().apply {
                    addChildren(propertiesRowPanel, buttonUI)
                },
                createScrollbarUI(propertiesRowPanel)
            )
        }
    }

    /**
     * Creates a list of property [UiElement]s for the given [SceneSystem].
     */
    open fun createSystemProperties(system: SceneSystem, isHidden: Boolean, onClose: (props: List<UiElement>) -> Unit): List<UiElement>
    {
        val headerText = system::class.findAnnotation<Name>()?.name
            ?: (system::class.java.simpleName ?: "")
                .split("(?=[A-Z])".toRegex())
                .joinToString(" ")

        val headerLabel = Label(headerText).apply {
            focusable = false
            padding.left = 10f
            fontSize = 20f
            color = style.getColor("LABEL")
        }

        val xLabel = Label("x").apply {
            focusable = false
            fontSize = 20f
            centerVertically = true
            centerHorizontally = true
            padding.top = -3f
            color = style.getColor("LABEL")
        }

        val exitButton = Button(width = Size.absolute(15f)).apply {
            padding.setAll(5f)
            color = Color.BLANK
            cornerRadius = 7.5f
            hoverColor = style.getColor("BUTTON_EXIT")
            addChildren(xLabel)
        }

        val headerPanel = HorizontalPanel().apply {
            focusable = false
            color = Color.BLANK
            addChildren(headerLabel)
            addChildren(exitButton)
        }

        val headerButton = Button().apply {
            id = system::class.simpleName
            toggleButton = true
            state = isHidden
            padding.left = 5f
            padding.right = 5f
            padding.top = 5f
            cornerRadius = 12f
            color = style.getColor("HEADER")
            activeColor = style.getColor("HEADER")
            hoverColor = style.getColor("HEADER_HOVER")
            addChildren(headerPanel)
        }

        val props = system::class.memberProperties
            .filter { it is KMutableProperty<*> && it.isEditable() && system.getPropInfo(it)?.hidden != true }
            .sortedBy { system.getPropInfo(it)?.i ?: 1000 }
            .map { prop ->
                val (panel, _) = createPropertyUI(system, prop as KMutableProperty<*>)
                panel.apply {
                    padding.left = 10f
                    padding.right = 10f
                    hidden = isHidden
                }
            }

        val uiElements = listOf(headerButton).plus(props)

        headerButton.setOnClicked { btn -> props.forEach { it.hidden = btn.state } }
        exitButton.setOnClicked { onClose(uiElements) }

        return uiElements
    }

    /**
     * Creates a UI panel with a horizontally aligned scrollbar.
     */
    open fun createScrollableSectionUI(panel: UiElement): HorizontalPanel
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
    open fun createScrollbarUI(scrollBinding: Scrollable): Scrollbar =
        Scrollbar(width = Size.absolute(15f)).apply {
            bgColor = style.getColor("ITEM")
            sliderColor = style.getColor("BUTTON")
            sliderColorHover = style.getColor("BUTTON_HOVER")
            cornerRadius = 8f
            padding.top = 5f
            padding.bottom = 5f
            padding.right = 5f
            sliderPadding = 3f
            bind(scrollBinding)
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
            hexInput.fontColor = style.getColor("LABEL")
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
                it.fontColor = style.getColor("LABEL")
                it.bgColor = style.getColor("BUTTON")
                it.bgColorHover = style.getColor("BUTTON_HOVER")
            }
        }

    /**
     * Creates a new [InputField] UI element.
     */
    open fun createInputFieldUI(obj: Any, prop: KMutableProperty<*>): InputField
    {
        val type = when (prop.javaField?.type)
        {
            Float::class.java, Double::class.java -> InputField.ContentType.FLOAT
            Int::class.java, Long::class.java, Char::class.java -> InputField.ContentType.INTEGER
            Boolean::class.java -> InputField.ContentType.BOOLEAN
            else -> InputField.ContentType.TEXT
        }

        return InputField(
            defaultText = prop.getter.call(obj)?.toString() ?: "",
            width = Size.relative(0.5f)
        ).apply {
            padding.top = 5f
            padding.bottom = 5f
            padding.right = 5f
            cornerRadius = 8f
            font = style.getFont()
            fontSize = 18f
            fontColor = style.getColor("LABEL")
            bgColor = style.getColor("BUTTON")
            bgColorHover = style.getColor("BUTTON_HOVER")
            strokeColor = Color.BLANK
            contentType = type

            if (type == InputField.ContentType.FLOAT || type == InputField.ContentType.INTEGER)
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
     * Creates a property row UI element for the given entity with a dropdown of [SceneEntity] types.
     */
    open fun createEntityTypePropertyUI(entity: SceneEntity, onItemChange: (KClass<out SceneEntity>) -> Unit): UiElement
    {
        val typeLabel = Label("Entity type", width = Size.relative(0.5f)).apply {
            padding.setAll(5f)
            padding.left = 10f
            fontSize = 18f
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val typeDropdown = createItemSelectionDropdownUI(entity::class, SceneEntity.REGISTERED_TYPES.toList()) { it.simpleName ?: "NO NAME" }
        typeDropdown.setOnItemChanged(onItemChange)

        return HorizontalPanel().apply {
            padding.left = 5f
            padding.right = 5f
            padding.top = 5f
            color = style.getColor("ITEM")
            cornerRadius = 12f
            addChildren(typeLabel, Panel(), typeDropdown)
        }
    }

    /**
     * Creates a property row UI element for the given object.
     * Returns the main UI panel and the input UiElement
     */
    open fun createPropertyUI(obj: Any, prop: KMutableProperty<*>): Pair<HorizontalPanel, UiElement>
    {
        val propUiKey = propertyUiFactories.keys.firstOrNull { prop.javaField?.type?.kotlin?.isSubclassOf(it) == true }
        val propUi = propertyUiFactories[propUiKey]?.invoke(obj, prop)
            ?: createInputFieldUI(obj, prop).apply { // Default UI when no factory exist
                setOnTextChanged { if (it.isValid) obj.setProperty(prop, it.text) }
                editable = obj.getPropInfo(prop)?.editable ?: true
            }

        val label = Label(text = prop.name.capitalize(), width = Size.relative(0.5f)).apply {
            padding.setAll(5f)
            padding.left = 10f
            fontSize = 20f
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val hPanel = HorizontalPanel().apply {
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
        HorizontalPanel().apply {
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
     * Determines the dropdown menu size based on the its content.
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