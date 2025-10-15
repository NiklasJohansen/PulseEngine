package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.AssetRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.ReflectionUtil.findPropertyAnnotation
import no.njoh.pulseengine.modules.ui.*
import no.njoh.pulseengine.modules.ui.ScrollDirection.*
import no.njoh.pulseengine.modules.ui.elements.*
import no.njoh.pulseengine.modules.ui.elements.InputField.ContentType.*
import no.njoh.pulseengine.modules.ui.elements.Label.TextSizeStrategy.UPDATE_WIDTH
import no.njoh.pulseengine.modules.ui.layout.*
import no.njoh.pulseengine.modules.editor.EditorUtil.getPropInfo
import no.njoh.pulseengine.modules.editor.EditorUtil.isEditable
import no.njoh.pulseengine.modules.editor.EditorUtil.setArrayProperty
import no.njoh.pulseengine.modules.editor.EditorUtil.setPrimitiveProperty
import java.lang.IllegalArgumentException
import kotlin.collections.get
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
        String::class to ::createStringPropertyUi,
        Boolean::class to ::createBooleanPropertyUi,
        Enum::class to ::createEnumPropertyUi,
        Color::class to { obj, prop, onChanged -> createColorPickerUI(outputColor = prop.getter.call(obj) as Color) },
        LongArray::class to ::createInputFieldUI,
        IntArray::class to ::createInputFieldUI,
        ShortArray::class to ::createInputFieldUI,
        ByteArray::class to ::createInputFieldUI,
        FloatArray::class to ::createInputFieldUI,
        DoubleArray::class to ::createInputFieldUI,
    )

    /**
     * Creates an [AssetPicker] if the property is annotated with [AssetRef] or a default [InputField].
     */
    open fun createStringPropertyUi(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): UiElement =
        obj::class.findPropertyAnnotation<AssetRef>(prop.name)
            ?.let { createAssetPickerUI(it, obj, prop, onChanged) }
            ?: createInputFieldUI(obj, prop, onChanged)

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

        val icon = Icon(width = Size.absolute(15f)).apply()
        {
            padding.left = ScaledValue.of(10f)
            iconSize = ScaledValue.of(18f)
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon(iconName)
            color = style.getColor("LABEL")
        }

        val label = Label(title).apply()
        {
            padding.left = ScaledValue.of(10f)
            fontSize = ScaledValue.of(20f)
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val crossIcon = Icon(width = Size.absolute(15f)).apply()
        {
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon("CROSS")
            color = style.getColor("LABEL")
            padding.top = ScaledValue.of(2f)
        }

        val exitButton = Button(width = Size.absolute(20f), height = Size.absolute(20f)).apply()
        {
            padding.top = ScaledValue.of(5f)
            padding.right = ScaledValue.of(5f)
            cornerRadius = ScaledValue.of(4f)
            color = Color.BLANK
            hoverColor = style.getColor("BUTTON_EXIT")
            setOnClicked {
                windowPanel.parent?.removeChildren(windowPanel)
                onClosed()
            }
            addChildren(crossIcon)
        }

        val headerPanel = HorizontalPanel(height = Size.absolute(30f)).apply()
        {
            color = style.getColor("WINDOW_HEADER")
            strokeColor = style.getColor("STROKE")
            strokeTop = false
            strokeLeft = false
            strokeRight = false
            strokeBottom = true
            focusable = false
            cornerRadius = ScaledValue.of(2f)
            addChildren(icon, label, exitButton)
        }

        windowPanel.color = Color.BLANK
        windowPanel.strokeColor = style.getColor("STROKE")
        windowPanel.movable = true
        windowPanel.resizable = true
        windowPanel.minHeight = ScaledValue.of(130f)
        windowPanel.minWidth = ScaledValue.of(150f)
        windowPanel.id = title
        windowPanel.header.addChildren(headerPanel)
        windowPanel.cornerRadius = ScaledValue.of(3f)
        windowPanel.body.color = style.getColor("DARK_BG")
        windowPanel.body.cornerRadius = ScaledValue.of(2f)
        windowPanel.body.padding.top = ScaledValue.of(0f)

        return windowPanel
    }

    /**
     * Creates a menu bar containing buttons with dropdown menus.
     */
    open fun createMenuBarUI(vararg buttons: MenuBarButton): UiElement =
        HorizontalPanel(height = Size.absolute(25f)).apply()
        {
            color = style.getColor("HEADER_FOOTER")
            strokeColor = style.getColor("STROKE")
            addChildren(
                *buttons.map { createMenuBarButtonUI(it, 18f, false) }.toTypedArray(), Panel()
            )
        }

    open fun createFooter(): Pair<HorizontalPanel, (totalEntities: Int, selectedEntities: Int, sceneName: String) -> Unit>
    {
        val icon = Icon(width = Size.absolute(15f)).apply()
        {
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon("CUBE")
            iconSize = ScaledValue.of(13f)
            color = style.getColor("LABEL")
            padding.left = ScaledValue.of(10f)
        }

        val entityCountLabel = Label("2/12311").apply {
            padding.left = ScaledValue.of(7f)
            fontSize = ScaledValue.of(15f)
            color = style.getColor("LABEL")
        }

        val sceneNameLabel = Label(width = Size.absolute(400f), text = "default.scn").apply {
            padding.left = ScaledValue.of(7f)
            fontSize = ScaledValue.of(15f)
            padding.right = ScaledValue.of(10f)
            textResizeStrategy = UPDATE_WIDTH
            color = style.getColor("LABEL")
        }

        val footer = HorizontalPanel(height = Size.absolute(25f)).apply()
        {
            color = style.getColor("HEADER_FOOTER")
            strokeColor = style.getColor("STROKE")
            addChildren(
                HorizontalPanel().apply { addChildren(icon, entityCountLabel) },
                sceneNameLabel,
                Panel()
            )
        }

        val onUpdate = { totalEntities: Int, totalSelected: Int, sceneName: String ->
            entityCountLabel.text = "$totalSelected/$totalEntities"
            sceneNameLabel.text = sceneName
        }

        return Pair(footer, onUpdate)
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
            rowHeight = ScaledValue.of(dropdownRowHeight)
            rowPadding = ScaledValue.of(dropdownRowPadding)
            menuLabel.text = menuBarButton.labelText
            menuLabel.fontSize = ScaledValue.of(fontSize)
            menuLabel.font = font
            menuLabel.horizontalAlignment = 0.5f
            menuLabel.verticalAlignment = 0.5f
            menuLabel.font = style.getFont()
            dropdown.color = style.getColor("LIGHT_BG")
            dropdown.cornerRadius = ScaledValue.of(2f)
            dropdown.minHeight = ScaledValue.of(0f)
            dropdown.minWidth = ScaledValue.of(10f)
            dropdown.resizable = false
            scrollbar.bgColor = style.getColor("SCROLLBAR_BG")
            scrollbar.sliderColor = style.getColor("SCROLLBAR")
            scrollbar.sliderColorHover = style.getColor("SCROLLBAR_HOVER")
            scrollbar.hidden = !showScrollbar
            scrollbar.cornerRadius = ScaledValue.of(2f)
            setOnItemToString { it.labelText }
            menuBarButton.items.forEachFast { addItem(it) }
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
            rowHeight = ScaledValue.of(style.getSize("DROPDOWN_ROW_HEIGHT"))
            menuLabel.font = font
            menuLabel.fontSize = ScaledValue.of(fontSize)
            menuLabel.color = style.getColor("LABEL")
            menuLabel.padding.left = ScaledValue.of(10f)
            cornerRadius = ScaledValue.of(2f)
            bgColor = style.getColor("INPUT_BG")
            bgHoverColor = style.getColor("BUTTON_HOVER")
            itemBgColor = Color.BLANK
            itemBgHoverColor = style.getColor("BUTTON_HOVER")
            dropdown.color = style.getColor("DARK_BG")
            dropdown.strokeColor = Color.BLANK
            dropdown.cornerRadius = ScaledValue.of(2f)
            scrollbar.bgColor = style.getColor("SCROLLBAR_BG")
            scrollbar.sliderColor = style.getColor("SCROLLBAR")
            scrollbar.sliderColorHover = style.getColor("SCROLLBAR_HOVER")
            scrollbar.hidden = !showScrollbar
            scrollbar.cornerRadius = ScaledValue.of(2f)
            setOnItemToString(onItemToString)
            setOnItemChanged(onItemChanged)
            this.selectedItem = selectedItem
            items.forEach(this::addItem)
        }
    }

    /**
     * Creates a [Surface2D] viewport.
     */
    open fun createViewportUI(engine: PulseEngine): VerticalPanel
    {
        val image = Image()
        image.bgColor = Color.BLACK
        image.renderTexture = engine.gfx.mainSurface.getTexture()

        val surfaceSelector = createItemSelectionDropdownUI(
            selectedItem = engine.gfx.mainSurface.config.name,
            items = engine.gfx.getAllSurfaces().flatMap { it.getTextures().mapIndexed { i, tex -> "${it.config.name}  (${tex.name})  #$i" } },
            onItemToString = { it },
            onItemChanged = { _, surfaceName ->
                val surface = surfaceName.substringBefore("  (")
                val index = surfaceName.substringAfterLast("#").toIntOrNull() ?: 0
                image.renderTexture = engine.gfx.getSurface(surface)?.getTexture(index)
            }
        ).apply {
            width.updateType(Size.ValueType.AUTO)
            height.updateType(Size.ValueType.ABSOLUTE)
            height.value = 30f
            padding.setAll(0f)
            bgColor = style.getColor("HEADER")
            hoverColor = style.getColor("BUTTON_HOVER")

            setOnClicked {
                val selected = selectedItem
                clearItems()
                engine.gfx.getAllSurfaces().forEachFast { surface ->
                    surface.getTextures().forEachIndexed { i, tex -> addItem("${surface.config.name}  (${tex.name})  #$i") }
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
        val menuItems = SceneSystem.REGISTERED_TYPES.map()
        {
            val systemName = it.findAnnotation<Name>()?.name ?: it.simpleName!!.split("(?=[A-Z])".toRegex()).joinToString(" ").trim()
            MenuBarItem(systemName)
            {
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
            width.setQuiet(Size.absolute(30f))
            height.setQuiet(Size.absolute(30f))
            dropdown.resizable = true
            dropdown.color = style.getColor("BUTTON")
            menuLabel.fontSize = ScaledValue.of(40f)
            menuLabel.padding.top = ScaledValue.of(4f)
            menuLabel.padding.right = ScaledValue.of(2f)
            padding.setAll(5f)
            bgColor = style.getColor("HEADER")
            hoverColor = style.getColor("BUTTON_HOVER")
            cornerRadius = ScaledValue.of(2f)
        }

        return HorizontalPanel().apply()
        {
            cornerRadius = ScaledValue.of(4f)
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
        val icon = system::class.findAnnotation<Icon>()
        val headerIcon = Icon(width = Size.absolute(30f))
        headerIcon.iconFontName = style.iconFontName
        headerIcon.iconCharacter = style.getIcon(icon?.iconName ?: "COG")
        headerIcon.iconSize = ScaledValue.of(15f)

        val headerText = system::class.findAnnotation<Name>()?.name
            ?: (system::class.java.simpleName ?: "")
                .split("(?=[A-Z])".toRegex())
                .joinToString(" ")
                .trim()

        val headerLabel = Label(headerText).apply()
        {
            fontSize = ScaledValue.of(20f)
            color = style.getColor("LABEL")
        }

        val exitButton = Button(
            width = Size.absolute(style.getSize("PROP_HEADER_ROW_HEIGHT") - 8f)
        ).apply {
            padding.setAll(4f)
            color = style.getColor("LABEL")
            hoverColor = style.getColor("LABEL")
            bgColor = Color.BLANK
            bgHoverColor = style.getColor("BUTTON_EXIT")
            cornerRadius = ScaledValue.of(2f)
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon("CROSS")
            xOrigin = 0.35f
            yOrigin = 0.55f
            iconSize = ScaledValue.of(15f)
        }

        val headerPanel = HorizontalPanel().apply()
        {
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
            padding.left = ScaledValue.of(5f)
            padding.right = ScaledValue.of(5f)
            padding.top = ScaledValue.of(5f)
            cornerRadius = ScaledValue.of(4f)
            color = style.getColor("HEADER")
            activeColor = style.getColor("HEADER")
            hoverColor = style.getColor("HEADER_HOVER")
            activeHoverColor = style.getColor("HEADER_HOVER")
            addChildren(headerPanel)
        }

        val nopCallback = { _: String, _: Any?, _: Any? -> }
        val props = system::class.memberProperties
            .filter { it is KMutableProperty<*> && it.isEditable() && system.getPropInfo(it)?.hidden != true }
            .sortedBy { system.getPropInfo(it)?.i ?: 1000 }
            .map { prop ->
                val (panel, _) = createPropertyUI(system, prop as KMutableProperty<*>, nopCallback)
                panel.apply()
                {
                    padding.left = ScaledValue.of(10f)
                    padding.right = ScaledValue.of(10f)
                    hidden = isHidden
                }
            }

        val uiElements = listOf(headerButton).plus(props)

        headerButton.setOnClicked { btn -> props.forEachFast { it.hidden = btn.isPressed } }
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

        return outerPanel!!
    }

    /**
     * Creates a default [Scrollbar] UI element.
     */
    open fun createScrollbarUI(scrollBinding: Scrollable, direction: ScrollDirection): Scrollbar
    {
        val width = if (direction == VERTICAL) Size.absolute(10f) else Size.auto()
        val height = if (direction == HORIZONTAL) Size.absolute(10f) else Size.auto()
        return Scrollbar(width, height).apply()
        {
            bgColor = style.getColor("SCROLLBAR_BG")
            sliderColor = style.getColor("SCROLLBAR")
            sliderColorHover = style.getColor("SCROLLBAR_HOVER")
            cornerRadius = ScaledValue.of(0f)
            padding.top = ScaledValue.of(0f)
            sliderPadding = ScaledValue.of(1.5f)
            bind(scrollBinding, direction)
        }
    }

    /**
     * Creates a new [ColorPicker] UI element.
     */
    open fun createColorPickerUI(outputColor: Color) =
        ColorPicker(outputColor).apply()
        {
            cornerRadius = ScaledValue.of(2f)
            color = style.getColor("INPUT_BG")
            bgColor = style.getColor("BUTTON")
            hexInput.fontSize = ScaledValue.of(20f)
            hexInput.textColor = style.getColor("LABEL")
            hexInput.bgColorHover = style.getColor("BUTTON_HOVER")
            hexInput.bgColor = style.getColor("INPUT_BG")
            hexInput.strokeColor = Color.BLANK
            hexInput.cornerRadius = ScaledValue.of(2f)
            colorPreviewButton.bgColor = style.getColor("INPUT_BG")
            colorPreviewButton.bgHoverColor = style.getColor("BUTTON_HOVER")
            colorEditor.color = style.getColor("LIGHT_BG")
            colorEditor.strokeColor = style.getColor("STROKE")
            saturationBrightnessPicker.strokeColor = style.getColor("HEADER")
            huePicker.strokeColor = style.getColor("STROKE")
            hsbSection.color = style.getColor("DARK_BG")
            rgbaSection.color = style.getColor("DARK_BG")
            listOf(redInput, greenInput, blueInput, alphaInput).forEach()
            {
                it.fontSize = ScaledValue.of(20f)
                it.textColor = style.getColor("LABEL")
                it.bgColor = style.getColor("INPUT_BG")
                it.bgColorHover = style.getColor("BUTTON_HOVER")
            }
        }

    open fun createAssetPickerUI(
        annotation: AssetRef,
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): UiElement =
        AssetPicker(
            initialAssetName = prop.getter.call(obj) as? String ?: ""
        ).apply {
            previewIconCharacter = style.iconFontName
            previewIconCharacter = style.getIcon(annotation.type.findAnnotation<Icon>()?.iconName ?: "BOX")
            nameInput.fontSize = ScaledValue.of(20f)
            nameInput.textColor = style.getColor("LABEL")
            nameInput.bgColorHover = style.getColor("BUTTON_HOVER")
            nameInput.bgColor = style.getColor("INPUT_BG")
            nameInput.strokeColor = Color.BLANK
            nameInput.cornerRadius = ScaledValue.of(2f)
            previewButton.bgColor = style.getColor("INPUT_BG")
            previewButton.bgHoverColor = style.getColor("BUTTON_HOVER")
            previewButton.color = Color.WHITE
            previewButton.hoverColor = Color.WHITE
            previewButton.iconFontName = style.iconFontName
            previewButton.iconCharacter = previewIconCharacter
            pickerWindow.color = style.getColor("LIGHT_BG")
            pickerWindow.strokeColor = style.getColor("HEADER")
            pickerWindow.strokeRight = false
            rows.color = style.getColor("DARK_BG")
            scrollbar.bgColor = style.getColor("SCROLLBAR_BG")
            scrollbar.sliderColor = style.getColor("SCROLLBAR")
            scrollbar.sliderColorHover = style.getColor("SCROLLBAR_HOVER")
            headerPanel.color = style.getColor("HEADER")
            headerPanel.strokeColor = style.getColor("STROKE")
            searchInput.font = style.getFont()
            searchInput.textColor = style.getColor("LABEL")
            searchInput.bgColor = style.getColor("BUTTON")
            searchInput.bgColorHover = style.getColor("BUTTON_HOVER")
            searchInput.strokeColor = Color.BLANK

            PulseEngine.INSTANCE.asset.getAllOfType(annotation.type.java).forEachFast { addAssetRow(it, style) }

            setOnValueChanged()
            {
                obj.setPrimitiveProperty(prop, it)
                onChanged(prop.name, nameInput.text, it)
            }
        }

    open fun createInputFieldUI(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): InputField {
        val propInfo = obj.getPropInfo(prop)
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
            cornerRadius = ScaledValue.of(2f)
            font = style.getFont()
            fontSize = ScaledValue.of(18f)
            textColor = style.getColor("LABEL")
            bgColor = style.getColor("INPUT_BG")
            bgColorHover = style.getColor("BUTTON_HOVER")
            strokeColor = Color.BLANK
            contentType = type
            editable = propInfo?.editable ?: true

            if (type == FLOAT || type == INTEGER)
            {
                propInfo?.let()
                {
                    numberMinVal = it.min
                    numberMaxVal = it.max
                }
            }

            setOnValidTextChanged()
            {
                if (type == FLOAT_ARRAY || type == INTEGER_ARRAY)
                    obj.setArrayProperty(prop, it.text)
                else
                    obj.setPrimitiveProperty(prop, it.text)

                onChanged(prop.name, it.lastValidText, it.text)
            }
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
        val propUi = propertyUiFactories[propUiKey]
            ?.invoke(obj, prop, onChanged)
            ?: createInputFieldUI(obj, prop, onChanged)

        val label = Label(text = prop.name.capitalize(), width = Size.relative(0.5f)).apply {
            padding.setAll(5f)
            padding.left = ScaledValue.of(10f)
            fontSize = ScaledValue.of(19f)
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val hPanel = HorizontalPanel(
            height = Size.absolute(style.getSize("PROP_ROW_HEIGHT"))
        ).apply {
            padding.left = ScaledValue.of(12f)
            padding.right = ScaledValue.of(12f)
            padding.top = ScaledValue.of(4f)
            cornerRadius = ScaledValue.of(4f)
            color = style.getColor( "BUTTON")
            addChildren(label, propUi)
        }

        return Pair(hPanel, propUi)
    }

    open fun createCategoryHeader(label: String, isCollapsed: Boolean, onClicked: (btn: Button) -> Unit) =
        Button(
            height = Size.absolute(style.getSize("PROP_HEADER_ROW_HEIGHT"))
        ).apply {
            id = "header_$label"
            toggleButton = true
            isPressed = isCollapsed
            padding.left = ScaledValue.of(5f)
            padding.right = ScaledValue.of(5f)
            padding.top = ScaledValue.of(5f)
            cornerRadius = ScaledValue.of(4f)
            color = style.getColor("HEADER")
            activeColor = style.getColor("HEADER")
            hoverColor = style.getColor("HEADER_HOVER")
            activeHoverColor = style.getColor("HEADER_HOVER")

            val icon = Icon(width = Size.absolute(25f)).apply()
            {
                padding.left = ScaledValue.of(-6f)
                padding.top = ScaledValue.of(3f)
                iconSize = ScaledValue.of(17f)
                iconFontName = style.iconFontName
                iconCharacter = style.getIcon(if (isPressed) "ARROW_RIGHT" else "ARROW_DOWN")
                color = style.getColor("LABEL")
            }

            setOnClicked {
                icon.iconCharacter = style.getIcon(if (it.isPressed) "ARROW_RIGHT" else "ARROW_DOWN")
                onClicked(it)
            }

            addChildren(
                icon,
                Label(label, width = Size.relative(0.5f)).apply()
                {
                    padding.setAll(5f)
                    padding.left = ScaledValue.of(20f)
                    fontSize = ScaledValue.of(20f)
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