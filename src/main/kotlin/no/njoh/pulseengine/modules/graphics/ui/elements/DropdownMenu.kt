package no.njoh.pulseengine.modules.graphics.ui.elements

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.graphics.ui.layout.RowPanel
import no.njoh.pulseengine.modules.graphics.ui.layout.WindowPanel

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

    private var onItemToString: (T) -> String = { it.toString() }
    private var onItemChanged: (T) -> Unit = { }

    init
    {
        menuLabel = Label("", width = Size.relative(0.9f))
        menuLabel.focusable = false
        menuLabel.padding.setAll(5f)
        menuLabel.color = Color(1f, 1f, 1f)

        rowPanel = RowPanel()
        rowPanel.padding.setAll(5f)

        scrollbar = Scrollbar(width = Size.absolute(20f))
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
        dropdown.minWidth = 100f
        dropdown.minHeight = 100f
        dropdown.addChildren(hPanel)

        addPopup(dropdown)
        addChildren(menuLabel)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        super.onUpdate(engine)
        if (!dropdown.hidden && !hasFocus(engine))
            dropdown.hidden = true
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

        val button = Button()
        button.color = itemBgColor
        button.hoverColor = itemBgHoverColor
        button.addChildren(label)
        button.setOnClicked {
            selectedItem = item
            if (closeOnItemSelect)
                dropdown.hidden = true
        }

        rowPanel.addChildren(button)

        if (selectedItem == null)
            selectedItem = item
    }

    override fun onMouseClicked(engine: PulseEngine)
    {
        super.onMouseClicked(engine)
        dropdown.hidden = !dropdown.hidden
    }

    override fun updatePopupLayout()
    {
        dropdown.padding.top = height.value
        super.updatePopupLayout()
    }

    fun setOnItemToString(callback: (T) -> String)
    {
        this.onItemToString = callback
    }

    fun setOnItemChanged(callback: (T) -> Unit)
    {
        this.onItemChanged = callback
    }

    override fun onRender(surface: Surface2D)
    {
        val bgColor = if (mouseInsideArea) bgHoverColor else bgColor
        surface.setDrawColor(bgColor)
        surface.drawTexture(Texture.BLANK, x.value, y.value, width.value, height.value)

        if (showArrow)
        {
            val xArrow = x.value + width.value - 15
            val yArrow = y.value + height.value / 2
            val arrowWidth = 10f
            val arrowHeight = 10f

            val direction = if (dropdown.hidden) 2.5f else -2.5f
            surface.setDrawColor(menuLabel.color)
            surface.drawQuadVertex(xArrow, yArrow + arrowHeight / direction)
            surface.drawQuadVertex(xArrow, yArrow + arrowHeight / direction)
            surface.drawQuadVertex(xArrow - arrowWidth / 2, yArrow - arrowHeight / direction)
            surface.drawQuadVertex(xArrow + arrowWidth / 2, yArrow - arrowHeight / direction)
        }
    }
}