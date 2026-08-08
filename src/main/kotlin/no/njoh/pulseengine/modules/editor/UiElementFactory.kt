package no.njoh.pulseengine.modules.editor

import gnu.trove.map.hash.THashMap
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.*
import no.njoh.pulseengine.core.scene.SceneManager
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.EntityNameRef
import no.njoh.pulseengine.core.shared.annotations.SceneRef
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.FileChooser
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.ReflectionUtil.findPropertyAnnotation
import no.njoh.pulseengine.modules.ui.*
import no.njoh.pulseengine.modules.ui.UiDsl.button
import no.njoh.pulseengine.modules.ui.UiDsl.horizontalPanel
import no.njoh.pulseengine.modules.ui.UiDsl.icon
import no.njoh.pulseengine.modules.ui.UiDsl.label
import no.njoh.pulseengine.modules.ui.UiDsl.panel
import no.njoh.pulseengine.modules.ui.ScrollDirection.*
import no.njoh.pulseengine.modules.ui.elements.*
import no.njoh.pulseengine.modules.ui.elements.InputField.ContentType.*
import no.njoh.pulseengine.modules.ui.elements.Label.TextSizeStrategy.UPDATE_WIDTH
import no.njoh.pulseengine.modules.ui.layout.*
import no.njoh.pulseengine.modules.ui.layout.docking.DockingPanel
import no.njoh.pulseengine.modules.editor.EditorUtil.getPropInfo
import no.njoh.pulseengine.modules.editor.EditorUtil.getName
import no.njoh.pulseengine.modules.editor.EditorUtil.isEditable
import no.njoh.pulseengine.modules.editor.EditorUtil.setArrayProperty
import no.njoh.pulseengine.modules.editor.EditorUtil.setPrimitiveProperty
import java.lang.IllegalArgumentException
import java.io.File
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import org.joml.Vector3f

/**
 * Factory for building [UiElement]s used in the [SceneEditor].
 */
open class UiElementFactory(
    val style: EditorStyle = EditorStyle()
) {
    private val entityTypeNameCache = HashMap<KClass<*>, String>()
    private var sceneManager: SceneManager? = null

    /**
     * Property UI factory functions for specific class types.
     */
    val propertyUiFactories = THashMap(mapOf(
        String::class      to ::createStringPropertyUi,
        AssetHandle::class to ::createAssetPickerUI,
        Boolean::class     to ::createBooleanPropertyUi,
        Enum::class        to ::createEnumPropertyUi,
        Color::class       to ::createColorPickerUI,
        Vector3f::class    to ::createVector3PropertyUi,
        LongArray::class   to ::createInputFieldUI,
        IntArray::class    to ::createInputFieldUI,
        ShortArray::class  to ::createInputFieldUI,
        ByteArray::class   to ::createInputFieldUI,
        FloatArray::class  to ::createInputFieldUI,
        DoubleArray::class to ::createInputFieldUI,
    ))

    /**
     * Binds the scene used to resolve properties annotated with [EntityRef].
     */
    fun bindSceneManager(sceneManager: SceneManager)
    {
        this.sceneManager = sceneManager
    }

    /**
     * Creates a specialized reference picker or a default [InputField].
     */
    open fun createStringPropertyUi(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): UiElement {
        obj::class.findPropertyAnnotation<SceneRef>(prop.name)?.let { return createSceneReferenceUI(obj, prop, onChanged) }
        obj::class.findPropertyAnnotation<EntityNameRef>(prop.name)?.let { return createSceneEntityNameReferenceUI(it, obj, prop, onChanged) }
        return createInputFieldUI(obj, prop, onChanged)
    }

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
     * Creates a typed entity dropdown for a [Long] property annotated with [EntityRef]. 
     */
    open fun createEntityReferenceUI(
        reference: EntityRef,
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): UiElement {
        val currentId = prop.getter.call(obj) as? Long ?: INVALID_ID
        val selected = when (currentId)
        {
            INVALID_ID -> EntityReferenceItem(INVALID_ID, "None")
            else -> sceneManager?.getEntity(currentId)
                ?.takeIf { reference.type.java.isInstance(it) }
                ?.let(::createEntityReferenceItem)
                ?: EntityReferenceItem(currentId, "$currentId - Missing entity")
        }

        return createItemSelectionDropdownUI(
            selectedItem = selected,
            items = listOf(selected),
            searchable = true,
            minimumDropDownWidth = ENTITY_REFERENCE_DROPDOWN_WIDTH,
            minimumVisibleItemCount = DROPDOWN_MAX_VISIBLE_ITEMS,
            onItemToString = { it.label },
            onItemChanged = { lastValue, newValue ->
                prop.setter.call(obj, newValue.id)
                onChanged(prop.name, lastValue?.id, newValue.id)
            }
        ).apply {
            setItemProvider { query -> queryEntityReferences(reference, selected, query) }
        }
    }

    /**
     * Creates a movable and resizable window panel.
     */
    open fun createWindowUI(
        title: CharSequence,
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
            fontSize = ScaledValue.of(style.getSize("HEADER_FONT_SIZE"))
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val crossIcon = Icon(width = Size.absolute(15f)).apply()
        {
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon("CROSS")
            color = style.getColor("LABEL")
            padding.top = ScaledValue.of(2f)
            padding.left = ScaledValue.of(2f)
        }

        val exitButton = Button(width = Size.absolute(20f), height = Size.absolute(20f)).apply()
        {
            padding.top = ScaledValue.of(5f)
            padding.right = ScaledValue.of(5f)
            color = Color.BLANK
            hoverColor = style.getColor("BUTTON_EXIT")
            setCornerRadius(ScaledValue.of(4f))
            setOnClicked()
            {
                removeWindow(windowPanel)
                onClosed()
            }
            addChildren(crossIcon)
        }

        val borderWidth = ScaledValue.of(1f)
        val cornerRadius = ScaledValue.of(5f)
        
        val headerPanel = HorizontalPanel(height = Size.absolute(30f)).apply()
        {
            color = style.getColor("WINDOW_HEADER")
            focusable = false
            cornerRadiusTopLeft  = cornerRadius
            cornerRadiusTopRight = cornerRadius
            padding.top   = borderWidth
            padding.right = borderWidth
            padding.left  = borderWidth
            addChildren(icon, label, exitButton)
        }

        windowPanel.setCornerRadius(cornerRadius)
        windowPanel.color = style.getColor("DARK_BG")
        windowPanel.strokeColor = style.getColor("STROKE")
        windowPanel.strokeWidth = borderWidth
        windowPanel.movable = true
        windowPanel.resizable = true
        windowPanel.minHeight = ScaledValue.of(130f)
        windowPanel.minWidth = ScaledValue.of(150f)
        windowPanel.id = title.toString()

        windowPanel.header.addChildren(headerPanel)
        
        windowPanel.body.padding.top    = ScaledValue.of(0f)
        windowPanel.body.padding.left   = borderWidth
        windowPanel.body.padding.right  = borderWidth
        windowPanel.body.padding.bottom = borderWidth

        return windowPanel
    }

    open fun createSceneTabsUI(engine: PulseEngine, tabs: List<EditorSceneTab>) =
        horizontalPanel(height = Size.absolute(40f)) {
            color = style.getColor("HEADER_FOOTER")
            strokeColor = style.getColor("STROKE")
            strokeWidth = ScaledValue.of(1f)
            strokeBottom = true
            strokeLeft = false
            strokeRight = false
            strokeTop = false
            focusable = false
            populateSceneTabsUI(engine, this, tabs)
        }

    open fun populateSceneTabsUI(engine: PulseEngine, sceneTabsUI: HorizontalPanel, tabs: List<EditorSceneTab>)
    {
        sceneTabsUI.clearChildren()
        for (tabData in tabs)
        {
            var exitButton: Button? = null
            
            val textWidth = style.getFont().getWidth(tabData.label, style.getSize("CONTENT_FONT_SIZE"))
            
            sceneTabsUI.button(
                width = Size.absolute((textWidth + if (tabData.onClosed != null) 65f else 45f).coerceIn(70f, 350f)),
                height = Size.relative(1f)
            ) {
                bgColor = if (tabData.selected) style.getColor("BUTTON_HOVER") else Color.BLANK
                bgHoverColor = style.getColor("BUTTON_HOVER")
                padding.left = ScaledValue.of(5f)
                padding.top = ScaledValue.of(5f)
                padding.bottom = ScaledValue.of(5f)
                cornerRadiusTopLeft = ScaledValue.of(4f)
                cornerRadiusTopRight = ScaledValue.of(4f)
                cornerRadiusBottomLeft = ScaledValue.of(4f)
                cornerRadiusBottomRight = ScaledValue.of(4f)

                horizontalPanel()
                {
                    icon(width = Size.absolute(15f))
                    {
                        iconFontName = style.iconFontName
                        iconCharacter = style.getIcon("TEXT")
                        color = style.getColor("LABEL")
                        padding.top = ScaledValue.of(1f)
                        padding.left = ScaledValue.of(10f)
                    }

                    label(width = Size.relative(1f), height = Size.relative(1f))
                    {
                        text = tabData.label
                        verticalAlignment = 0.5f
                        color = style.getColor("LABEL")
                        fontSize = ScaledValue.of(style.getSize("CONTENT_FONT_SIZE"))
                        padding.left = ScaledValue.of(10f)
                        focusable = false
                    }

                    if (tabData.onClosed != null)
                    {
                        exitButton = button(width = Size.absolute(20f), height = Size.absolute(20f))
                        {
                            padding.top = ScaledValue.of(6f)
                            padding.right = ScaledValue.of(5f)
                            setCornerRadius(ScaledValue.of(4f))
                            color = Color.BLANK
                            hoverColor = style.getColor("BUTTON_EXIT")
                            setOnClicked { tabData.onClosed.invoke() }

                            icon(width = Size.absolute(15f))
                            {
                                iconFontName = style.iconFontName
                                iconCharacter = style.getIcon("CROSS")
                                color = style.getColor("LABEL")
                                padding.top = ScaledValue.of(2f)
                                padding.left = ScaledValue.of(3f)
                            }
                        }
                    }
                }

                setOnClicked() 
                {
                    val closeButtonClicked = exitButton?.area?.isInside(engine.input.xMouse, engine.input.yMouse) == true
                    if (!closeButtonClicked)
                        tabData.onSelected()
                }
            }
        }
        sceneTabsUI.panel {}
    }

    /**
     * Creates a menu bar containing buttons with dropdown menus.
     */
    open fun createMenuBarUI(vararg buttons: MenuBarButton): UiElement =
        HorizontalPanel(height = Size.absolute(25f)).apply()
        {
            color        = style.getColor("HEADER_FOOTER")
            strokeColor  = style.getColor("STROKE")
            strokeWidth  = ScaledValue.of(1f)
            strokeBottom = true
            strokeLeft   = false
            strokeRight  = false
            strokeTop    = false
            addChildren(
                *buttons.map { createMenuBarButtonUI(it, showScrollbar = false) }.toTypedArray(), Panel()
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
            fontSize = ScaledValue.of(style.getSize("CONTENT_FONT_SIZE"))
            color = style.getColor("LABEL")
        }

        val sceneNameLabel = Label(width = Size.absolute(400f), text = "default.scn").apply {
            padding.left = ScaledValue.of(7f)
            fontSize = ScaledValue.of(style.getSize("CONTENT_FONT_SIZE"))
            padding.right = ScaledValue.of(10f)
            textResizeStrategy = UPDATE_WIDTH
            color = style.getColor("LABEL")
        }

        val footer = HorizontalPanel(height = Size.absolute(25f)).apply()
        {
            color = style.getColor("HEADER_FOOTER")
            strokeColor = style.getColor("STROKE")
            strokeTop = true
            strokeLeft = false
            strokeRight = false
            strokeBottom = false
            
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
        fontSize: Float = style.getSize("CONTENT_FONT_SIZE"),
        showScrollbar: Boolean = true,
        searchable: Boolean = false
    ): DropdownMenu<MenuBarItem> {
        val font = style.getFont()
        val scrollBarWidth = if (showScrollbar) 25f else 0f
        val maxItemCount = if (showScrollbar) 8 else 100
        val items = menuBarButton.items.map { it.labelText }
        val dropdownRowHeight = style.getSize("DROPDOWN_ROW_HEIGHT")
        val dropdownRowPadding = 5f
        val (contentWidth, height) = getDropDownDimensions(font, fontSize, scrollBarWidth, dropdownRowHeight + dropdownRowPadding, maxItemCount, items)
        val hasCheckableItems = menuBarButton.items.any { it.isChecked != null }
        val width = contentWidth +
            (if (hasCheckableItems) MENU_CHECK_MARK_WIDTH else 0f) +
            (if (menuBarButton.items.any { it.items.isNotEmpty() }) MENU_SUBMENU_ARROW_WIDTH else 0f)
        return DropdownMenu<MenuBarItem>(
            width = Size.absolute(55f),
            dropDownWidth = Size.absolute(width),
            dropDownHeight = Size.absolute(height + if (searchable) DROPDOWN_SEARCH_HEIGHT else 0f)
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
            dropdown.color = style.getColor("DROPDOWN_BG")
            dropdown.setCornerRadius(ScaledValue.of(2f))
            dropdown.minHeight = ScaledValue.of(0f)
            dropdown.minWidth = ScaledValue.of(10f)
            dropdown.resizable = false
            scrollbar.bgColor = style.getColor("SCROLLBAR_BG")
            scrollbar.sliderColor = style.getColor("SCROLLBAR")
            scrollbar.sliderColorHover = style.getColor("SCROLLBAR_HOVER")
            scrollbar.hidden = !showScrollbar
            scrollbar.cornerRadius = ScaledValue.of(2f)
            configureDropdownSearch(this, searchable)

            val submenus = mutableListOf<UiElement>()
            setOnItemToString { it.labelText }
            setOnItemRowCreated { item, row ->
                row.setOnMouseEnter { submenus.forEach(::hideMenuTree) }
                if (hasCheckableItems)
                    addMenuCheckMark(row, item.isChecked)
                if (item.items.isNotEmpty())
                {
                    addSubmenuArrow(row, fontSize)
                    val submenu = createSubmenuUI(item.items, this, font, fontSize)
                    submenus += submenu
                    row.addPopup(submenu)
                    row.setOnMouseEnter() 
                    {
                        submenus.filterNot { it === submenu }.forEach(::hideMenuTree)
                        positionSubmenu(row, submenu)
                        submenu.hidden = false
                    }
                    row.setOnClicked() 
                    {
                        positionSubmenu(row, submenu)
                        submenu.hidden = false
                    }
                }
            }

            setOnClicked { submenus.forEach(::hideMenuTree) }
            menuBarButton.items.forEachFast { addItem(it) }
            setOnItemChanged { _, item -> if (item.items.isEmpty()) item.onClick() }
        }
    }

    private fun createSubmenuUI(items: List<MenuBarItem>, rootMenu: DropdownMenu<MenuBarItem>, font: Font, fontSize: Float): VerticalPanel
    {
        val rowHeight = style.getSize("DROPDOWN_ROW_HEIGHT")
        val rowPadding = 5f
        val (contentWidth, height) = getDropDownDimensions(
            font = font,
            fontSize = fontSize,
            scrollBarWidth = 0f,
            rowHeight = rowHeight + rowPadding,
            maxItemCount = 100,
            items = items.map { it.labelText }
        )
        val hasCheckableItems = items.any { it.isChecked != null }
        val width = contentWidth +
            (if (hasCheckableItems) MENU_CHECK_MARK_WIDTH else 0f) +
            (if (items.any { it.items.isNotEmpty() }) MENU_SUBMENU_ARROW_WIDTH else 0f)

        val siblingSubmenus = mutableListOf<UiElement>()

        return VerticalPanel(width = Size.absolute(width), height = Size.absolute(height)).apply()
        {
            hidden = true
            focusable = true
            color = style.getColor("DROPDOWN_BG")
            setCornerRadius(ScaledValue.of(2f))

            items.forEachIndexed { index, item ->

                val row = Button(height = Size.absolute(rowHeight)).apply()
                {
                    color = Color.BLANK
                    hoverColor = style.getColor("BUTTON_HOVER")
                    padding.left = ScaledValue.of(5f)
                    padding.right = ScaledValue.of(5f)
                    if (index == 0) padding.top = ScaledValue.of(5f)
                    padding.bottom = ScaledValue.of(rowPadding)

                    addChildren(Label(item.labelText).apply()
                    {
                        focusable = false
                        padding.left = ScaledValue.of(5f)
                        this.font = font
                        this.fontSize = ScaledValue.of(fontSize)
                        this.color = style.getColor("LABEL")
                    })
                }

                if (hasCheckableItems)
                    addMenuCheckMark(row, item.isChecked)
                
                row.setOnMouseEnter { siblingSubmenus.forEach(::hideMenuTree) }
                
                if (item.items.isEmpty())
                {
                    row.setOnClicked() 
                    {
                        item.onClick()
                        rootMenu.dropdown.hidden = true
                        hideMenuTree(this)
                    }
                }
                else
                {
                    addSubmenuArrow(row, fontSize)
                    val submenu = createSubmenuUI(item.items, rootMenu, font, fontSize)
                    siblingSubmenus += submenu
                    row.addPopup(submenu)
                    row.setOnMouseEnter() 
                    {
                        siblingSubmenus.filterNot { it === submenu }.forEach(::hideMenuTree)
                        positionSubmenu(row, submenu)
                        submenu.hidden = false
                    }
                    row.setOnClicked() 
                    {
                        positionSubmenu(row, submenu)
                        submenu.hidden = false
                    }
                }
                addChildren(row)
            }
        }
    }

    private fun addMenuCheckMark(row: Button, isChecked: (() -> Boolean)?)
    {
        row.children.firstOrNull()?.padding?.left = ScaledValue.of(MENU_CHECK_MARK_WIDTH + 5f)
        if (isChecked == null)
            return

        val checkMark = CheckMark(
            isChecked = isChecked,
            x = Position.alignLeft(),
            width = Size.absolute(MENU_CHECK_MARK_WIDTH)
        ).apply {
            padding.left = ScaledValue.of(4f)
            color = style.getColor("LABEL")
        }

        row.addChildren(checkMark)
    }

    private fun addSubmenuArrow(row: Button, fontSize: Float)
    {
        val icon = Icon(
            x = Position.alignRight(),
            width = Size.absolute(MENU_SUBMENU_ARROW_WIDTH)
        ).apply {
            padding.right = ScaledValue.of(4f)
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon("ARROW_RIGHT")
            iconSize = ScaledValue.of(fontSize * 0.75f)
            color = style.getColor("LABEL")
        }

        row.children.firstOrNull()?.padding?.right = ScaledValue.of(MENU_SUBMENU_ARROW_WIDTH)
        row.addChildren(icon)
    }

    private fun positionSubmenu(row: Button, submenu: UiElement)
    {
        var root: UiElement = row
        while (root.parent != null) root = root.parent!!

        var parentMenu: UiElement = row
        while (parentMenu.parent != null && parentMenu.parent?.popup !== parentMenu)
            parentMenu = parentMenu.parent!!

        val parentMenuLeft = parentMenu.x.value
        val parentMenuRight = parentMenuLeft + parentMenu.width.value
        val openToLeft = parentMenuRight + submenu.width.value > root.x.value + root.width.value
        val submenuX = if (openToLeft) parentMenuLeft - submenu.width.value else parentMenuRight
        submenu.padding.left = ScaledValue.unscaled(submenuX - row.x.value)
    }

    private fun hideMenuTree(menu: UiElement)
    {
        menu.hidden = true
        menu.popup?.let(::hideMenuTree)
        menu.children.forEach { child -> child.popup?.let(::hideMenuTree) }
    }

    /**
     * Creates a [DropdownMenu] with a generic type.
     */
    open fun <T> createItemSelectionDropdownUI(
        selectedItem: T,
        items: List<T>,
        onItemToString: (T) -> String,
        onItemChanged: (lastValue: T?, newValue: T) -> Unit,
        searchable: Boolean = false,
        minimumDropDownWidth: Float = 0f,
        minimumVisibleItemCount: Int = 0
    ): DropdownMenu<T> {
        val fontSize = style.getSize("CONTENT_FONT_SIZE")
        val font = style.getFont()
        val showScrollbar = items.size > 8
        val scrollBarWidth = if (showScrollbar) 25f else 0f
        val stringItems = items.map { onItemToString(it) }
        val (contentWidth, contentHeight) = getDropDownDimensions(font, fontSize, scrollBarWidth, 35f, DROPDOWN_MAX_VISIBLE_ITEMS, stringItems)
        val width = contentWidth.coerceAtLeast(minimumDropDownWidth)
        val height = contentHeight.coerceAtLeast(5f + min(minimumVisibleItemCount, DROPDOWN_MAX_VISIBLE_ITEMS) * 35f)
        return DropdownMenu<T>(
            dropDownWidth = Size.absolute(width),
            dropDownHeight = Size.absolute(height + if (searchable) DROPDOWN_SEARCH_HEIGHT else 0f)
        ).apply {
            rowHeight = ScaledValue.of(style.getSize("DROPDOWN_ROW_HEIGHT"))
            setCornerRadius(ScaledValue.of(4f))
            menuLabel.font = font
            menuLabel.fontSize = ScaledValue.of(fontSize)
            menuLabel.color = style.getColor("LABEL")
            menuLabel.padding.left = ScaledValue.of(10f)
            bgColor = style.getColor("INPUT_BG")
            bgHoverColor = style.getColor("BUTTON_HOVER")
            itemBgColor = Color.BLANK
            itemBgHoverColor = style.getColor("BUTTON_HOVER")
            dropdown.color = style.getColor("DROPDOWN_BG")
            dropdown.strokeColor = Color.BLANK
            dropdown.setCornerRadius(ScaledValue.of(4f))
            scrollbar.bgColor = style.getColor("SCROLLBAR_BG")
            scrollbar.sliderColor = style.getColor("SCROLLBAR")
            scrollbar.sliderColorHover = style.getColor("SCROLLBAR_HOVER")
            scrollbar.hidden = !showScrollbar
            scrollbar.cornerRadius = ScaledValue.of(2f)
            configureDropdownSearch(this, searchable)
            setOnItemToString(onItemToString)
            setOnItemChanged(onItemChanged)
            this.selectedItem = selectedItem
            items.forEach(this::addItem)
        }
    }

    private fun queryEntityReferences(reference: EntityRef, selected: EntityReferenceItem, query: String): List<EntityReferenceItem> 
    {
        val normalizedQuery = query.trim()
        val queriedId = normalizedQuery.toLongOrNull()
        val results = ArrayList<EntityReferenceItem>(ENTITY_REFERENCE_MAX_RESULTS)
        val includedIds = HashSet<Long>(ENTITY_REFERENCE_MAX_RESULTS)

        fun addIfMatching(item: EntityReferenceItem)
        {
            if (results.size < ENTITY_REFERENCE_MAX_RESULTS && item.label.contains(normalizedQuery, ignoreCase = true) && includedIds.add(item.id))
                results.add(item)
        }

        addIfMatching(EntityReferenceItem(INVALID_ID, "None"))
        addIfMatching(selected)

        val remaining = ENTITY_REFERENCE_MAX_RESULTS - results.size
        if (remaining > 0)
        {
            val entities = queryEntities(reference.type, remaining) { entity ->
                if (queriedId != null)
                {
                    entity.id == queriedId
                }
                else if (normalizedQuery.isEmpty())
                {
                    true
                }
                else
                {
                    val name = (entity as? Named)?.name
                    name?.contains(normalizedQuery, ignoreCase = true) == true || getEntityTypeName(entity).contains(normalizedQuery, ignoreCase = true)
                }
            }

            entities.asSequence()
                .map(::createEntityReferenceItem)
                .filter { includedIds.add(it.id) }
                .sortedWith(compareBy({ it.label.substringAfter(" - ") }, { it.id }))
                .take(remaining)
                .forEach(results::add)
        }

        return results
    }

    private fun queryEntities(type: KClass<*>, limit: Int, predicate: (SceneEntity) -> Boolean): List<SceneEntity> 
    {
        val sceneManager = sceneManager
        if (sceneManager == null || limit <= 0)
            return emptyList()

        val matches = ArrayList<SceneEntity>(limit)
        for (entities in sceneManager.getAllEntitiesByType())
        {
            val first = entities.firstOrNull() ?: continue
            if (!type.java.isInstance(first)) continue

            for (entity in entities)
            {
                if (!predicate(entity)) continue

                matches.add(entity)
                if (matches.size == limit)
                    return matches
            }
        }

        return matches
    }

    private fun createEntityReferenceItem(entity: SceneEntity): EntityReferenceItem
    {
        val name = (entity as? Named)?.name?.takeIf { it.isNotBlank() } ?: getEntityTypeName(entity)
        return EntityReferenceItem(entity.id, "${entity.id} - $name")
    }

    private fun getEntityTypeName(entity: SceneEntity) = entityTypeNameCache.getOrPut(entity::class) { entity::class.getName() }

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
    open fun createSystemPropertiesPanelUI(engine: PulseEngine, propertiesRowPanel: RowPanel, onChanged: () -> Unit = {}): HorizontalPanel 
    {
        val menuItems = SceneSystem.REGISTERED_TYPES
            .map { it to (it.findAnnotation<Name>()?.name ?: it.simpleName!!) }
            .sortedBy { it.second }
            .map { (systemType, systemName) ->
                MenuBarItem(systemName)
                {
                    val newSystem = systemType.createInstance()
                    newSystem.init(engine)
                    engine.scene.addSystem(newSystem)
                    onChanged()
                    val props = createSystemProperties(
                        system = newSystem,
                        isHidden = false,
                        onClose = { props ->
                            newSystem.onDestroy(engine)
                            engine.scene.removeSystem(newSystem)
                            propertiesRowPanel.removeChildren(*props.toTypedArray())
                            onChanged()
                        },
                        onChanged = onChanged
                    )
                    propertiesRowPanel.addChildren(*props.toTypedArray())
                }
        }

        val button = MenuBarButton(labelText = "+", items = menuItems)
        val showScrollBar = menuItems.size > 8
        val buttonUI = createMenuBarButtonUI(button, showScrollbar = showScrollBar, searchable = true).apply()
        {
            width.setQuiet(Size.absolute(30f))
            height.setQuiet(Size.absolute(30f))
            dropdown.resizable = true
            dropdown.color = style.getColor("DROPDOWN_BG")
            menuLabel.fontSize = ScaledValue.of(style.getSize("BUTTON_FONT_SIZE"))
            menuLabel.padding.top = ScaledValue.of(4f)
            menuLabel.padding.right = ScaledValue.of(2f)
            padding.setAll(5f)
            bgColor = style.getColor("HEADER")
            hoverColor = style.getColor("BUTTON_HOVER")
            setCornerRadius(ScaledValue.of(2f))
        }

        return HorizontalPanel().apply()
        {
            setCornerRadius(ScaledValue.of(4f))
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
    open fun createSystemProperties(system: SceneSystem, isHidden: Boolean, onClose: (props: List<UiElement>) -> Unit, onChanged: () -> Unit = {}): List<UiElement> 
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
            fontSize = ScaledValue.of(style.getSize("HEADER_FONT_SIZE"))
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
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon("CROSS")
            xOrigin = 0.45f
            yOrigin = 0.55f
            iconSize = ScaledValue.of(15f)
            setCornerRadius(ScaledValue.of(2f))
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
            setCornerRadius(ScaledValue.of(4f))
            color = style.getColor("HEADER")
            activeColor = style.getColor("HEADER")
            hoverColor = style.getColor("HEADER_HOVER")
            activeHoverColor = style.getColor("HEADER_HOVER")
            addChildren(headerPanel)
        }

        val propertyChangedCallback = { _: String, _: Any?, _: Any? -> onChanged() }
        val props = system::class.memberProperties
            .filter { it is KMutableProperty<*> && it.isEditable() && system.getPropInfo(it)?.hidden != true }
            .sortedBy { system.getPropInfo(it)?.i ?: 1000 }
            .map { prop ->
                val (panel, _) = createPropertyUI(system, prop as KMutableProperty<*>, propertyChangedCallback)
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
            cornerRadius = ScaledValue.of(4f)
            bind(scrollBinding, direction)
        }
    }

    /**
     * Creates a new [ColorPicker] UI element.
     */
    open fun createColorPickerUI(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): UiElement = 
        ColorPicker(outputColor = prop.getter.call(obj) as Color).apply()
        {
            fontSize = ScaledValue.of(style.getSize("CONTENT_FONT_SIZE"))
            setCornerRadius(ScaledValue.of(4f))
            color = style.getColor("INPUT_BG")
            bgColor = style.getColor("BUTTON")
            hexInput.textColor = style.getColor("LABEL")
            hexInput.bgColorHover = style.getColor("BUTTON_HOVER")
            hexInput.bgColor = style.getColor("INPUT_BG")
            hexInput.strokeColor = Color.BLANK
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
                it.textColor = style.getColor("LABEL")
                it.bgColor = style.getColor("INPUT_BG")
                it.bgColorHover = style.getColor("BUTTON_HOVER")
            }
            setOnChanged { color -> onChanged(prop.name, null, color) }
        }

    open fun createVector3PropertyUi(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): UiElement =
        Vector3Input(vector = prop.getter.call(obj) as Vector3f).apply() 
        {
            val propInfo = obj.getPropInfo(prop)
            editable = propInfo?.editable ?: true
            numberMinVal = propInfo?.min ?: Float.NEGATIVE_INFINITY
            numberMaxVal = propInfo?.max ?: Float.POSITIVE_INFINITY
            font = style.getFont()
            fontSize = ScaledValue.of(style.getSize("CONTENT_FONT_SIZE"))
            textColor = style.getColor("LABEL")
            inputBgColor = style.getColor("INPUT_BG")
            inputBgColorHover = style.getColor("BUTTON_HOVER")
            inputStrokeColor = Color.BLANK
            setOnValueChanged { lastValue, newValue ->
                onChanged(prop.name, lastValue, newValue)
            }
        }

    /**
     * Creates an asset picker for a typed [AssetHandle]. 
     */
    @Suppress("UNCHECKED_CAST")
    open fun createAssetPickerUI(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): UiElement {
        val handle = prop.getter.call(obj) as? AssetHandle<*> ?: return createInputFieldUI(obj, prop, onChanged)
        val assetType = (prop.returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*>)
            ?.takeIf { it.isSubclassOf(Asset::class) } as? KClass<out Asset>
            ?: Asset::class

        return AssetPicker(
            initialAssetName = handle.name,
            fontSize = ScaledValue.of(style.getSize("CONTENT_FONT_SIZE"))
        ).apply {
            previewIconCharacter = style.iconFontName
            previewIconCharacter = style.getIcon(assetType.findAnnotation<Icon>()?.iconName ?: "BOX")
            nameInput.textColor = style.getColor("LABEL")
            nameInput.bgColorHover = style.getColor("BUTTON_HOVER")
            nameInput.bgColor = style.getColor("INPUT_BG")
            nameInput.strokeColor = Color.BLANK
            nameInput.cornerRadiusTopLeft    = ScaledValue.of(4f)
            nameInput.cornerRadiusBottomLeft = ScaledValue.of(4f)
            previewButton.bgColor = style.getColor("INPUT_BG")
            previewButton.bgHoverColor = style.getColor("BUTTON_HOVER")
            previewButton.color = Color.WHITE
            previewButton.hoverColor = Color.WHITE
            previewButton.iconFontName = style.iconFontName
            previewButton.iconCharacter = previewIconCharacter
            pickerWindow.color = style.getColor("DROPDOWN_BG")
            pickerWindow.strokeColor = style.getColor("HEADER")
            pickerWindow.strokeRight = true
            pickerWindow.setCornerRadius(ScaledValue.of(4f))

            rows.cornerRadiusBottomLeft = ScaledValue.of(4f)
            scrollbar.bgColor = style.getColor("SCROLLBAR_BG")
            scrollbar.sliderColor = style.getColor("SCROLLBAR")
            scrollbar.sliderColorHover = style.getColor("SCROLLBAR_HOVER")
            headerPanel.strokeColor = style.getColor("STROKE")
            headerPanel.cornerRadiusTopLeft = ScaledValue.of(4f)
            headerPanel.cornerRadiusTopRight = ScaledValue.of(4f)
            headerPanel.cornerRadiusBottomRight = ScaledValue.of(0f)
            headerPanel.cornerRadiusBottomLeft = ScaledValue.of(0f)
            searchInput.font = style.getFont()
            searchInput.textColor = style.getColor("LABEL")
            searchInput.bgColor = style.getColor("BUTTON")
            searchInput.bgColorHover = style.getColor("BUTTON_HOVER")
            searchInput.strokeColor = Color.BLANK

            PulseEngine.INSTANCE.asset
                .getAllOfType(assetType.java)
                .sortedBy { it.name }
                .forEachFast { addAssetRow(it, style) }

            var currentName = handle.name
            setOnValueChanged { newName ->
                val lastName = currentName
                currentName = newName
                (prop.getter.call(obj) as? AssetHandle<*>)?.name = newName
                onChanged(prop.name, lastName, newName)
            }
        }
    }

    open fun createSceneReferenceUI(
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): UiElement {
        val input = createInputFieldUI(obj, prop, onChanged).apply { width.setQuiet(Size.relative(1f)) }
        val browse = Button(width = Size.absolute(30f)).apply {
            bgColor = style.getColor("INPUT_BG")
            bgHoverColor = style.getColor("BUTTON_HOVER")
            iconFontName = style.iconFontName
            iconCharacter = style.getIcon("FOLDER")
            color = style.getColor("LABEL")
            hoverColor = style.getColor("LABEL")
            setOnClicked {
                val engine = PulseEngine.INSTANCE
                FileChooser.showOpenFileDialog(engine.config.saveDirectory) { selected ->
                    val saveDirectory = File(engine.config.saveDirectory).absoluteFile
                    val selectedFile = File(selected).absoluteFile
                    val storedValue = runCatching { selectedFile.relativeTo(saveDirectory).path }
                        .getOrElse { selectedFile.path }
                        .replace('\\', '/')
                    val previous = prop.getter.call(obj)
                    prop.setter.call(obj, storedValue)
                    input.text = storedValue
                    onChanged(prop.name, previous, storedValue)
                }
            }
        }
        return HorizontalPanel(width = Size.relative(0.5f)).apply { addChildren(input, browse) }
    }

    open fun createSceneEntityNameReferenceUI(
        reference: EntityNameRef,
        obj: Any,
        prop: KMutableProperty<*>,
        onChanged: (propName: String, lastValue: Any?, newValue: Any?) -> Unit
    ): UiElement {
        val currentName = prop.getter.call(obj) as? String ?: ""

        val sceneFile = obj::class.memberProperties
            .firstOrNull { it.name == reference.sceneFileProperty }
            ?.getter?.call(obj) as? String ?: ""

        val entries = mutableListOf(SceneEntityNameItem("", "Entire scene"))
        if (sceneFile.isNotBlank())
        {
            PulseEngine.INSTANCE.scene.load(sceneFile)
                ?.getEntities()
                ?.filterIsInstance<Named>()
                ?.sortedWith(compareBy<Named> { it.name }.thenBy { (it as SceneEntity).id })
                ?.forEach { entity -> entries += SceneEntityNameItem(entity.name, "${entity.name} (${(entity as SceneEntity).id})") }
        }

        val selected = entries.firstOrNull { it.name == currentName }
            ?: SceneEntityNameItem(currentName, "$currentName (missing)").also { entries.add(1, it) }

        return createItemSelectionDropdownUI(
            selectedItem = selected,
            items = entries,
            searchable = true,
            onItemToString = { it.label },
            onItemChanged = { last, new ->
                prop.setter.call(obj, new.name)
                onChanged(prop.name, last?.name, new.name)
            }
        )
    }

    private fun configureDropdownSearch(dropdown: DropdownMenu<*>, searchable: Boolean)
    {
        dropdown.searchable = searchable
        dropdown.searchHeader.color = style.getColor("DROPDOWN_HEADER")
        dropdown.searchHeader.strokeColor = style.getColor("STROKE")
        dropdown.searchInput.font = style.getFont()
        dropdown.searchInput.fontSize = ScaledValue.of(style.getSize("CONTENT_FONT_SIZE"))
        dropdown.searchInput.textColor = style.getColor("LABEL")
        dropdown.searchInput.bgColor = style.getColor("BUTTON")
        dropdown.searchInput.bgColorHover = style.getColor("BUTTON_HOVER")
        dropdown.searchInput.strokeColor = Color.BLANK
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

        return InputField(defaultText = defaultText ?: "").apply() 
        {
            setCornerRadius(ScaledValue.of(4f))
            font = style.getFont()
            fontSize = ScaledValue.of(style.getSize("CONTENT_FONT_SIZE"))
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
        val entityReference = obj::class.findPropertyAnnotation<EntityRef>(prop.name)
        val propUiKey = propertyUiFactories.keys.firstOrNull { prop.javaField?.type?.kotlin?.isSubclassOf(it) == true }
        val propUi = if (entityReference != null && prop.returnType.classifier == Long::class)
            createEntityReferenceUI(entityReference, obj, prop, onChanged)
        else
            propertyUiFactories[propUiKey]
                ?.invoke(obj, prop, onChanged)
                ?: createInputFieldUI(obj, prop, onChanged)

        val label = Label(text = prop.name.capitalize(), width = Size.relative(0.45f)).apply {
            padding.setAll(5f)
            padding.left = ScaledValue.of(10f)
            fontSize = ScaledValue.of(style.getSize("CONTENT_FONT_SIZE"))
            font = style.getFont()
            color = style.getColor("LABEL")
        }

        val hPanel = HorizontalPanel(
            height = Size.absolute(style.getSize("PROP_ROW_HEIGHT"))
        ).apply {
            padding.left = ScaledValue.of(12f)
            padding.right = ScaledValue.of(12f)
            padding.top = ScaledValue.of(4f)
            setCornerRadius(ScaledValue.of(4f))
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
            setCornerRadius(ScaledValue.of(4f))
            color = style.getColor("HEADER")
            activeColor = style.getColor("HEADER")
            hoverColor = style.getColor("HEADER_HOVER")
            activeHoverColor = style.getColor("HEADER_HOVER")

            val icon = Icon(width = Size.absolute(25f)).apply()
            {
                padding.left = ScaledValue.of(-2f)
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
                    fontSize = ScaledValue.of(style.getSize("HEADER_FONT_SIZE"))
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

    private fun removeWindow(window: WindowPanel)
    {
        var parent = window.parent
        while (parent != null && parent !is DockingPanel)
            parent = parent.parent

        if (parent is DockingPanel)
            parent.removeWindow(window)
        else
            window.parent?.removeChildren(window)
    }

    private data class EntityReferenceItem(val id: Long, val label: String)
    private data class SceneEntityNameItem(val name: String, val label: String)

    companion object
    {
        private const val DROPDOWN_SEARCH_HEIGHT = 30f
        private const val DROPDOWN_MAX_VISIBLE_ITEMS = 8
        private const val MENU_CHECK_MARK_WIDTH = 20f
        private const val MENU_SUBMENU_ARROW_WIDTH = 20f
        private const val ENTITY_REFERENCE_DROPDOWN_WIDTH = 350f
        private const val ENTITY_REFERENCE_MAX_RESULTS = 100
    }
}

data class EditorSceneTab(
    val label: String,
    val selected: Boolean,
    val onSelected: () -> Unit,
    val onClosed: (() -> Unit)? = null
)