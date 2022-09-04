package no.njoh.pulseengine.modules.gui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.UiElement
import no.njoh.pulseengine.modules.gui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.gui.layout.RowPanel
import no.njoh.pulseengine.modules.gui.layout.WindowPanel

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
    var selectedItem: T? = null
        set (value)
        {
            field = value
            if (useSelectedItemAsMenuLabel)
                menuLabel.text = value?.let { onItemToString(it) } ?: ""
            value?.let { onItemChanged(it) }
        }

    var itemBgColor = Color(0.5f, 0.5f, 0.5f)
    var itemBgHoverColor = Color(0.8f, 0.8f, 0.8f)
    var closeOnItemSelect = true
    var showArrow = true
    var useSelectedItemAsMenuLabel = true
    var rowHeight = 30f
    var rowPadding = 5f

    private var onItemToString: (T) -> String = { it.toString() }
    private var onItemChanged: (T) -> Unit = { }
    private var isMouseOver = false

    init
    {
        menuLabel = Label("", x = Position.center(), y = Position.center())
        menuLabel.focusable = false
        menuLabel.color = Color(1f, 1f, 1f)

        rowPanel = RowPanel()
        rowPanel.padding.setAll(5f)

        scrollbar = Scrollbar(width = Size.absolute(15f))
        scrollbar.padding.top = 5f
        scrollbar.padding.bottom = 5f
        scrollbar.padding.right = 5f
        scrollbar.sliderPadding = 3f
        scrollbar.bind(rowPanel)

        val hPanel = HorizontalPanel()
        hPanel.addChildren(rowPanel, scrollbar)

        dropdown.color = color
        dropdown.hidden = true
        dropdown.resizable = true
        dropdown.minWidth = 10f
        dropdown.minHeight = 10f
        dropdown.addChildren(hPanel)

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
        else popup?.hasFocus(engine) ?: false || children.any { it.hasFocus(engine) }

    fun addItem(item: T)
    {
        val label = Label(onItemToString(item))
        label.focusable = false
        label.padding.left = 5f
        label.font = menuLabel.font
        label.color = menuLabel.color
        label.fontSize = menuLabel.fontSize

        val button = Button(height = Size.absolute(rowHeight))
        button.color = itemBgColor
        button.hoverColor = itemBgHoverColor
        button.addChildren(label)
        button.setOnClicked {
            selectedItem = item
            if (closeOnItemSelect)
                dropdown.hidden = true
        }

        rowPanel.children.lastOrNull()?.padding?.bottom = rowPadding
        rowPanel.addChildren(button)

        if (selectedItem == null)
            selectedItem = item
    }

    fun clearItems()
    {
        rowPanel.clearChildren()
        selectedItem = null
    }

    override fun onMouseClicked(engine: PulseEngine)
    {
        super.onMouseClicked(engine)
        dropdown.hidden = !dropdown.hidden
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

        dropdown.padding.left = if (isOnRightSide) -dropdown.width.value + width.value else 0f
        dropdown.padding.top = if (isOnBottomSide) -dropdown.height.value else height.value
    }

    fun setOnItemToString(callback: (T) -> String)
    {
        this.onItemToString = callback
    }

    fun setOnItemChanged(callback: (T) -> Unit)
    {
        this.onItemChanged = callback
    }

    override fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        val bgColor = if (isMouseOver) bgHoverColor else bgColor
        surface.setDrawColor(bgColor)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value, cornerRadius = cornerRadius)

        if (showArrow && width.value - menuLabel.textWidth > 35f)
        {
            val xArrow = x.value + width.value - 15
            val yArrow = y.value + height.value / 2
            val arrowWidth = 10f
            val arrowHeight = 10f

            val direction = if (dropdown.isVisible()) -2.5f else 2.5f
            surface.setDrawColor(menuLabel.color)
            surface.drawQuadVertex(xArrow, yArrow + arrowHeight / direction)
            surface.drawQuadVertex(xArrow, yArrow + arrowHeight / direction)
            surface.drawQuadVertex(xArrow - arrowWidth / 2, yArrow - arrowHeight / direction)
            surface.drawQuadVertex(xArrow + arrowWidth / 2, yArrow - arrowHeight / direction)
        }
    }
}