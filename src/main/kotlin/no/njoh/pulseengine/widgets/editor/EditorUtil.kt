package no.njoh.pulseengine.widgets.editor

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Scrollable
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.Size.ValueType.ABSOLUTE
import no.njoh.pulseengine.modules.gui.Size.ValueType.AUTO
import no.njoh.pulseengine.modules.gui.UiElement
import no.njoh.pulseengine.modules.gui.elements.*
import no.njoh.pulseengine.modules.gui.elements.InputField.ContentType.*
import no.njoh.pulseengine.modules.gui.layout.*
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Property
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import java.lang.IllegalArgumentException
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

object EditorUtil
{
    val style = EditorStyle().apply()
    {
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
    }

    /** Property UI creation functions for specific class types. */
    val propertyUiFactories = mutableMapOf(
        // Create dropdown with all enum types
        Enum::class to { obj: Any, prop: KMutableProperty<*> ->
            createItemSelectionDropdownUI(
                selectedItem = prop.getter.call(obj),
                items = prop.javaField?.type?.enumConstants?.toList() ?: emptyList(),
                onItemToString = { it.toString() }
            ).apply {
                setOnItemChanged { value -> value?.let { obj.setProperty(prop.name, value) } }
            }
        },

        // Create dropdown with TRUE/FALSE types for booleans
        Boolean::class to { obj: Any, prop: KMutableProperty<*> ->
            createItemSelectionDropdownUI(
                selectedItem = prop.getter.call(obj),
                items = listOf(true, false),
                onItemToString = { it.toString().capitalize() }
            ).apply {
                setOnItemChanged { value -> value?.let { obj.setProperty(prop.name, value) } }
            }
        },

        // Create color picker for Color class
        Color::class to { obj: Any, prop: KMutableProperty<*> ->
            createColorPickerUI(color = prop.getter.call(obj) as Color)
        },

        // Use default factory for strings (factory functions returning null will use default property UI)
        String::class to { _: Any, _: KMutableProperty<*> -> null }
    )

    /**
     * Creates a movable and resizable window panel.
     */
    fun createWindowUI(title: String, x: Float = 0f, y: Float = 20f, width: Float = 300f, height: Float = 200f): WindowPanel
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
    fun createMenuBarUI(vararg buttons: MenuBarButton): UiElement
    {
        return HorizontalPanel(height = Size.absolute(25f)).apply {
            color = style.getColor("BG_LIGHT")
            strokeColor = style.getColor("STROKE")
            addChildren(*buttons.map { createMenuBarButtonUI(it, 18f, false) }.toTypedArray(), Panel())
        }
    }

    data class MenuBarButton(val labelText: String, val items: List<MenuBarItem>)
    data class MenuBarItem(val labelText: String, val onClick: () -> Unit)

    /**
     * Creates a menu button with a dropdown.
     */
    private fun createMenuBarButtonUI(
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
    private fun <T> createItemSelectionDropdownUI(
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
    fun createViewportUI(engine: PulseEngine): VerticalPanel
    {
        val image = Image()
        image.bgColor = Color(0f, 0f, 0f, 1f)
        image.texture = engine.gfx.mainSurface.getTexture()

        val surfaceSelector = createItemSelectionDropdownUI(
            selectedItem = engine.gfx.mainSurface.name,
            onItemToString = { it },
            items = engine.gfx.getAllSurfaces().flatMap { it.getTextures().mapIndexed { i, tex -> "${it.name}  (${tex.name})  #$i" } }
        ).apply {
            width.updateType(AUTO)
            height.updateType(ABSOLUTE)
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
    fun createSystemPropertiesPanelUI(engine: PulseEngine, propertiesRowPanel: RowPanel): HorizontalPanel
    {
        propertiesRowPanel.insertSceneSystemProperties(engine)

        val menuItems = SceneSystem.REGISTERED_TYPES.map {
            MenuBarItem(it.simpleName ?: "") {
                val newSystem = it.createInstance()
                newSystem.init(engine)
                engine.scene.addSystem(newSystem)
                propertiesRowPanel.insertSceneSystemProperties(
                    system = newSystem,
                    onClose = {
                        newSystem.onDestroy(engine)
                        engine.scene.removeSystem(newSystem)
                    }
                )
            }
        }

        val button = MenuBarButton("+", menuItems)
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
     * Reinserts properties into [RowPanel] for all the given [SceneSystem]s.
     */
    fun RowPanel.insertSceneSystemProperties(engine: PulseEngine)
    {
        val hiddenSystems = this.children
            .filterIsInstance<Button>()
            .filter { it.state }
            .mapNotNull { it.id }

        this.clearChildren()
        for (system in engine.scene.activeScene.systems)
        {
            val isHidden = system::class.simpleName in hiddenSystems
            this.insertSceneSystemProperties(system, isHidden, onClose = {
                system.onDestroy(engine)
                engine.scene.removeSystem(system)
            })
        }
    }

    /**
     * Creates and inserts properties into the [RowPanel] for the given [SceneSystem]
     */
    fun RowPanel.insertSceneSystemProperties(system: SceneSystem, isHidden: Boolean = true, onClose: () -> Unit)
    {
        val props = system::class.memberProperties
            .filter { it is KMutableProperty<*> && isPropertyEditable(it) }
            .sortedBy { it.findAnnotation<Property>()?.order ?: 1000 }
            .map { prop ->
                createPropertyUI(system, prop as KMutableProperty<*>).first.apply {
                    padding.left = 10f
                    padding.right = 10f
                    hidden = isHidden
                }
            }

        val headerText = system::class.findAnnotation<Name>()?.name
            ?: (system::class.java.simpleName ?: "")
                .split("(?=[A-Z])".toRegex())
                .joinToString(" ")

        val label = Label(headerText).apply {
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

        val header = HorizontalPanel().apply {
            focusable = false
            color = Color.BLANK
            addChildren(label)
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
            setOnClicked { btn -> props.forEach { it.hidden = btn.state } }
            addChildren(header)
        }

        exitButton.setOnClicked {
            onClose()
            this.removeChildren(headerButton, *props.toTypedArray())
            this.setLayoutDirty()
        }

        this.addChildren(headerButton, *props.toTypedArray())
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
        return Scrollbar(width = Size.absolute(15f)).apply {
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
    }

    /**
     * Creates a [ColorPicker] UI element.
     */
    private fun createColorPickerUI(color: Color) =
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
            cornerRadius = 8f
            font = style.getFont()
            fontSize = 18f
            fontColor = style.getColor("LABEL")
            bgColor = style.getColor("BUTTON")
            bgColorHover = style.getColor("BUTTON_HOVER")
            strokeColor = Color.BLANK
            contentType = type

            if (type == FLOAT || type == INTEGER)
            {
                prop.findAnnotation<Property>()?.let {
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
    fun createPropertyUI(obj: Any, prop: KMutableProperty<*>): Pair<HorizontalPanel, UiElement>
    {
        val propUiKey = propertyUiFactories.keys.firstOrNull { prop.javaField?.type?.kotlin?.isSubclassOf(it) == true }
        val propUi = propertyUiFactories[propUiKey]?.invoke(obj, prop)
            ?: createInputFieldUI(value = prop.getter.call(obj), prop).apply { // Default UI when no factory exist
                setOnTextChanged { if (it.isValid) obj.setProperty(prop, it.text) }
                editable = prop.name != "id"
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

    fun createCategoryHeader(label: String) =
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
     * Parses the given string value into a the class type given by the [KMutableProperty].
     * Sets the named property of the obj to the parsed value.
     */
    private fun Any.setProperty(property: KMutableProperty<*>, value: String) =
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
            }?.let { property.setter.call(this, it) }
        }
        catch (e: Exception)
        {
            Logger.error("Failed to parse value: $value into required type: ${property.javaField?.type}, reason: ${e.message}")
        }

    /**
     * Sets the named property of the object to the given value.
     */
    fun Any.setProperty(name: String, value: Any)
    {
        val prop = this::class.memberProperties.find { it.name == name }
        if (prop != null && prop is KMutableProperty<*>)
        {
            try { prop.setter.call(this, value) }
            catch (e: Exception) { Logger.error("Failed to set property with name: $name, reason: ${e.message}") }
        }
    }

    fun isPropertyEditable(prop: KMutableProperty<*>) =
        prop.visibility != KVisibility.PRIVATE &&
        prop.visibility != KVisibility.PROTECTED &&
        prop.javaField?.getAnnotation(JsonIgnore::class.java) == null

    fun KMutableProperty<*>.isPrimitiveValue() =
        this.javaField?.type?.kotlin?.let {
            it.isSubclassOf(Number::class) ||
            it.isSubclassOf(Boolean::class) ||
            it.isSubclassOf(Char::class) ||
            it.isSubclassOf(CharSequence::class) ||
            it.isSubclassOf(Enum::class)
        } ?: false
}