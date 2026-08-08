package no.njoh.pulseengine.modules.ui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.CornerRadius
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.modules.ui.Position
import no.njoh.pulseengine.modules.ui.ScaledValue
import no.njoh.pulseengine.modules.ui.ScrollDirection.VERTICAL
import no.njoh.pulseengine.modules.ui.Size
import no.njoh.pulseengine.modules.ui.UiElement
import no.njoh.pulseengine.modules.ui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.ui.layout.Panel
import no.njoh.pulseengine.modules.ui.layout.RowPanel
import no.njoh.pulseengine.modules.ui.layout.VerticalPanel
import no.njoh.pulseengine.modules.ui.layout.WindowPanel

class DropdownMenu <T> (
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto(),
    dropDownWidth: Size = Size.auto(),
    dropDownHeight: Size = Size.absolute(300f)
) : Button(x, y, width, height) {

    var dropdown = WindowPanel(width = dropDownWidth, height = dropDownHeight)
    var rowPanel: RowPanel
    var menuLabel: Label
    var scrollbar: Scrollbar
    var searchInput: InputField
    var searchHeader: Panel
    var selectedItem: T? = null
        set (value)
        {
            if (useSelectedItemAsMenuLabel)
                menuLabel.text = value?.let { onItemToString(it) } ?: ""
            if (field != null && value != null)
                onItemChanged(field, value)
            field = value
        }

    var itemBgColor = Color(0.5f, 0.5f, 0.5f)
    var itemBgHoverColor = Color(0.8f, 0.8f, 0.8f)
    var closeOnItemSelect = true
    var showArrow = true
    var useSelectedItemAsMenuLabel = true
    var rowHeight = ScaledValue.of(30f)
    var rowPadding = ScaledValue.of(5f)
    var searchable = false
        set(value)
        {
            searchHeader.hidden = !value
            field = value
        }

    private var onItemToString: (T) -> String = { it.toString() }
    private var onItemChanged: (lastItem: T?, newItem: T) -> Unit = { _, _ -> }
    private var onItemRowCreated: (item: T, row: Button) -> Unit = { _, _ -> }
    private var itemProvider: ((query: String) -> List<T>)? = null
    private var isMouseOver = false
    private val itemRows = mutableListOf<Pair<T, Button>>()

    init
    {
        menuLabel = Label("", x = Position.center(), y = Position.center())
        menuLabel.focusable = false
        menuLabel.color = Color(1f, 1f, 1f)

        rowPanel = RowPanel()
        rowPanel.padding.setAll(5f)

        scrollbar = Scrollbar(width = Size.absolute(10f))
        scrollbar.padding.top = ScaledValue.of(2f)
        scrollbar.padding.bottom = ScaledValue.of(2f)
        scrollbar.padding.right = ScaledValue.of(2f)
        scrollbar.sliderPadding = ScaledValue.of(1.5f)
        scrollbar.bind(rowPanel, direction = VERTICAL)

        searchInput = InputField("").apply()
        {
            placeHolderText = "Search ..."
            fontSize = ScaledValue.of(17f)
            padding.setAll(5f)
            setCornerRadius(ScaledValue.of(4f))
            setOnTextChanged { input ->
                if (!refreshProvidedItems(input.text))
                {
                    for ((item, row) in itemRows)
                    {
                        row.hidden = input.text.isNotBlank() && !onItemToString(item).contains(input.text, ignoreCase = true)
                    }
                }
            }
        }

        searchHeader = Panel(height = Size.absolute(30f)).apply()
        {
            hidden = true
            strokeBottom = true
            strokeTop = false
            strokeLeft = false
            strokeRight = false
            cornerRadiusTopRight = ScaledValue.of(4f)
            cornerRadiusTopLeft = ScaledValue.of(4f)
            addChildren(searchInput)
        }

        val itemPanel = HorizontalPanel().apply { addChildren(rowPanel, scrollbar) }
        val contentPanel = VerticalPanel().apply { addChildren(searchHeader, itemPanel) }

        dropdown.color = color
        dropdown.hidden = true
        dropdown.resizable = true
        dropdown.minWidth = ScaledValue.of(10f)
        dropdown.minHeight = ScaledValue.of(10f)
        dropdown.addChildren(contentPanel)

        addPopup(dropdown)
        addChildren(menuLabel)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        super.onUpdate(engine)
        if (dropdown.isVisible() && !hasFocus(engine))
            dropdown.hidden = true

        isMouseOver = engine.input.hasHoverFocus(area) && mouseInsideArea
    }

    private fun UiElement.hasFocus(engine: PulseEngine): Boolean =
        if (engine.input.hasFocus(this.area)) true
        else popup?.hasFocus(engine) ?: false || children.anyMatches { it.hasFocus(engine) }

    fun addItem(item: T)
    {
        val label = Label(onItemToString(item))
        label.focusable = false
        label.padding.left = ScaledValue.of(5f)
        label.font = menuLabel.font
        label.color = menuLabel.color
        label.fontSize = menuLabel.fontSize

        val button = Button(height = Size.absolute(rowHeight))
        button.color = itemBgColor
        button.hoverColor = itemBgHoverColor
        button.addChildren(label)
        button.setOnClicked() 
        {
            if (searchable)
                clearSearch()
            selectedItem = item
            if (closeOnItemSelect)
                dropdown.hidden = true
        }
        onItemRowCreated(item, button)

        rowPanel.children.lastOrNull()?.padding?.bottom = rowPadding
        rowPanel.addChildren(button)
        itemRows.add(item to button)

        if (selectedItem == null)
            selectedItem = item
    }

    fun clearItems()
    {
        rowPanel.clearChildren()
        itemRows.clear()
        selectedItem = null
    }

    override fun onMouseClicked(engine: PulseEngine)
    {
        super.onMouseClicked(engine)
        val isOpening = dropdown.hidden
        if (isOpening)
            refreshProvidedItems(searchInput.text)
        dropdown.hidden = !dropdown.hidden
        if (isOpening && searchable)
            engine.input.acquireFocus(searchInput.area)
    }

    override fun updatePopupLayout()
    {
        updateDropdownAlignment()
        super.updatePopupLayout()
    }

    private fun updateDropdownAlignment()
    {
        var root: UiElement = this
        while (root.parent != null)
            root = root.parent!!

        val isOnRightSide = x.value > root.x.value + root.width.value * 0.5f
        val isOnBottomSide = y.value > root.y.value + root.height.value * 0.5f

        dropdown.padding.left = ScaledValue.unscaled(if (isOnRightSide) -dropdown.width.value + width.value else 0f)
        dropdown.padding.top = ScaledValue.unscaled(if (isOnBottomSide) -dropdown.height.value else height.value)
    }

    fun setOnItemToString(callback: (T) -> String)
    {
        this.onItemToString = callback
    }

    fun setOnItemChanged(callback: (lastValue: T?, newValue: T) -> Unit)
    {
        this.onItemChanged = callback
    }

    fun setOnItemRowCreated(callback: (item: T, row: Button) -> Unit)
    {
        this.onItemRowCreated = callback
    }

    fun setItemProvider(provider: (query: String) -> List<T>)
    {
        itemProvider = provider
    }

    fun refreshProvidedItems(query: String): Boolean
    {
        val providedItems = itemProvider?.invoke(query) ?: return false
        rowPanel.clearChildren()
        itemRows.clear()
        providedItems.forEach(::addItem)
        scrollbar.setTargetSliderFraction(0f)
        scrollbar.hidden = providedItems.size <= MAX_VISIBLE_ITEMS
        return true
    }

    private fun clearSearch()
    {
        searchInput.setTextQuiet("")
        itemRows.forEach { (_, row) -> row.hidden = false }
    }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        val bgColor = if (isMouseOver) bgHoverColor else bgColor
        val cornerRadius = CornerRadius(cornerRadiusTopLeft.value, cornerRadiusTopRight.value, cornerRadiusBottomRight.value, cornerRadiusBottomLeft.value)
        surface.setDrawColor(bgColor)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius)

        if (showArrow && width.value - menuLabel.textWidth > 35f)
        {
            val xArrow = x.value + width.value - 15
            val yArrow = y.value + height.value / 2
            val size = 10f
            val length = 2.5f
            surface.setDrawColor(menuLabel.color)
            
            if (dropdown.isVisible())
            {
                surface.drawQuadVertex(xArrow - size / 2, yArrow - size / length)
                surface.drawQuadVertex(xArrow, yArrow + size / length)
                surface.drawQuadVertex(xArrow, yArrow + size / length)
                surface.drawQuadVertex(xArrow + size / 2, yArrow - size / length)
            }
            else
            {
                surface.drawQuadVertex(xArrow - size / 2, yArrow + size / length)
                surface.drawQuadVertex(xArrow + size / 2, yArrow + size / length)
                surface.drawQuadVertex(xArrow, yArrow - size / length)
                surface.drawQuadVertex(xArrow, yArrow - size / length)
            }
        }
    }

    private companion object
    {
        const val MAX_VISIBLE_ITEMS = 8
    }
}
