package no.njoh.pulseengine.modules.ui

import no.njoh.pulseengine.core.shared.annotations.Dsl
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.ui.elements.Button
import no.njoh.pulseengine.modules.ui.elements.ColorPicker
import no.njoh.pulseengine.modules.ui.elements.Icon
import no.njoh.pulseengine.modules.ui.elements.Image
import no.njoh.pulseengine.modules.ui.elements.InputField
import no.njoh.pulseengine.modules.ui.elements.Label
import no.njoh.pulseengine.modules.ui.elements.Scrollbar
import no.njoh.pulseengine.modules.ui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.ui.layout.Panel
import no.njoh.pulseengine.modules.ui.layout.RowPanel
import no.njoh.pulseengine.modules.ui.layout.TilePanel
import no.njoh.pulseengine.modules.ui.layout.VerticalPanel
import no.njoh.pulseengine.modules.ui.layout.WindowPanel
import no.njoh.pulseengine.modules.ui.layout.docking.DockingPanel

typealias DslInit<T> = @Dsl T.() -> Unit

/**
 * Contains all DSL functions for building UI elements.
 */
object UiDsl
{
    // Elements -------------------------------------------------------------------------------------------------------

    /**
     * Creates a new [Image] element.
     */
    inline fun image(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Image>): Image =
        Image(x, y, width, height).apply(id, init)

    /**
     * Creates a new [Image] element and adds it to the parent.
     */
    inline fun UiElement.image(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Image>): Image =
        Image(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [Icon] element.
     */
    inline fun icon(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Icon>): Icon =
        Icon(x, y, width, height).apply(id, init)

    /**
     * Creates a new [Icon] element and adds it to the parent.
     */
    inline fun UiElement.icon(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Icon>): Icon =
        Icon(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [Label] element.
     */
    inline fun label(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Label>): Label =
        Label("", x, y, width, height).apply(id, init)

    /**
     * Creates a new [Label] element and adds it to the parent.
     */
    inline fun UiElement.label(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Label>): Label =
        Label("", x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [Button] element.
     */
    inline fun button(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Button>): Button =
        Button(x, y, width, height).apply(id, init)

    /**
     * Creates a new [Button] element and adds it to the parent.
     */
    inline fun UiElement.button(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Button>): Button =
        Button(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [InputField] element.
     */
    inline fun inputField(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<InputField>): InputField =
        InputField("", x, y, width, height).apply(id, init)

    /**
     * Creates a new [InputField] element and adds it to the parent.
     */
    inline fun UiElement.inputField(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<InputField>): InputField =
        InputField("", x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [ColorPicker] element.
     */
    inline fun colorPicker(id: String = "", color: Color, x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<ColorPicker>): ColorPicker =
        ColorPicker(color, x, y, width, height).apply(id, init)

    /**
     * Creates a new [ColorPicker] element and adds it to the parent.
     */
    inline fun UiElement.colorPicker(id: String = "", color: Color, x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<ColorPicker>): ColorPicker =
        ColorPicker(color, x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [Scrollbar] element.
     */
    inline fun scrollbar(id: String = "", width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Scrollbar>): Scrollbar =
        Scrollbar(width, height).apply(id, init)

    /**
     * Creates a new [Scrollbar] element and adds it to the parent.
     */
    inline fun UiElement.scrollbar(id: String = "", width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Scrollbar>): Scrollbar =
        Scrollbar(width, height).apply(id, init).also { addChildren(it) }

    // Layout ---------------------------------------------------------------------------------------------------------

    /**
     * Creates a new [Panel] element.
     */
    inline fun panel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Panel>): Panel =
        Panel(x, y, width, height).apply(id, init)

    /**
     * Creates a new [Panel] element and adds it to the parent.
     */
    inline fun UiElement.panel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<Panel>): Panel =
        Panel(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [VerticalPanel] element.
     */
    inline fun verticalPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<VerticalPanel>): VerticalPanel =
        VerticalPanel(x, y, width, height).apply(id, init)

    /**
     * Creates a new [VerticalPanel] element and adds it to the parent.
     */
    inline fun UiElement.verticalPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<VerticalPanel>): VerticalPanel =
        VerticalPanel(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [HorizontalPanel] element.
     */
    inline fun horizontalPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<HorizontalPanel>): HorizontalPanel =
        HorizontalPanel(x, y, width, height).apply(id, init)

    /**
     * Creates a new [HorizontalPanel] element and adds it to the parent.
     */
    inline fun UiElement.horizontalPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<HorizontalPanel>): HorizontalPanel =
        HorizontalPanel(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [RowPanel] element.
     */
    inline fun rowPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<RowPanel>): RowPanel =
        RowPanel(x, y, width, height).apply(id, init)

    /**
     * Creates a new [RowPanel] element and adds it to the parent.
     */
    inline fun UiElement.rowPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<RowPanel>): RowPanel =
        RowPanel(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [TilePanel] element.
     */
    inline fun tilePanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<TilePanel>): TilePanel =
        TilePanel(x, y, width, height).apply(id, init)

    /**
     * Creates a new [TilePanel] element and adds it to the parent.
     */
    inline fun UiElement.tilePanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<TilePanel>): TilePanel =
        TilePanel(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [WindowPanel] element.
     */
    inline fun windowPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<WindowPanel>): WindowPanel =
        WindowPanel(x, y, width, height).apply(id, init)

    /**
     * Creates a new [WindowPanel] element and adds it to the parent.
     */
    inline fun UiElement.windowPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<WindowPanel>): WindowPanel =
        WindowPanel(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Creates a new [DockingPanel] element.
     */
    inline fun dockingPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<DockingPanel>): DockingPanel =
        DockingPanel(x, y, width, height).apply(id, init)

    /**
     * Creates a new [DockingPanel] element and adds it to the parent.
     */
    inline fun UiElement.dockingPanel(id: String = "", x: Position = Position.auto(), y: Position = Position.auto(), width: Size = Size.auto(), height: Size = Size.auto(), init: DslInit<DockingPanel>): DockingPanel =
        DockingPanel(x, y, width, height).apply(id, init).also { addChildren(it) }

    /**
     * Add the returned [UiElement] to the parent as a popup.
     */
    inline fun <T: UiElement> T.popup(init: () -> UiElement): UiElement
    {
        val content = init()
        addPopup(content)
        return content
    }

    /**
     * Applies the given [id] and [DslInit] to the [UiElement].
     */
    inline fun <T: UiElement> T.apply(id: String, init: DslInit<T>) = also { it.id = id }.apply(init)
}