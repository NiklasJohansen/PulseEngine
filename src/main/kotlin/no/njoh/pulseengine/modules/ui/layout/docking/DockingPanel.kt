package no.njoh.pulseengine.modules.ui.layout.docking

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.modules.ui.Position.PositionType
import no.njoh.pulseengine.modules.ui.Position.PositionType.*
import no.njoh.pulseengine.modules.ui.Size.ValueType.*
import no.njoh.pulseengine.modules.ui.Size.ValueType.AUTO
import no.njoh.pulseengine.modules.ui.UiUtils.findElement
import no.njoh.pulseengine.modules.ui.layout.*
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.sumByFloat
import no.njoh.pulseengine.modules.ui.*
import kotlin.math.min

/**
 * Docking functionality modeled after:
 * https://traineq.org/HelloImGui/hello_imgui_demos/hello_imgui_demodocking/hello_imgui_demodocking.html
 */
class DockingPanel(
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var viewport: Panel = WindowPanel()

    private var dockingPanel = this
    private val dockingButtons = DockingButtons()
    private var hoverTarget: UiElement = viewport
    private var grabbed: WindowPanel? = null
    private var lastGrabbed: WindowPanel? = null

    init
    {
        id = "docking_panel"
        popup = dockingButtons
        popup?.hidden = true

        viewport.id = "viewport"
        viewport.focusable = false
        viewport.clearChildren()
        addChildren(viewport)
    }

    override fun onRender(engine: PulseEngine, surface: Surface) { }

    override fun onUpdate(engine: PulseEngine)
    {
        grabbed = grabbed ?: findGrabbed()

        if (grabbed != null)
        {
            hoverTarget = findOnHoverTarget(this) ?: viewport

            dockingButtons.targetPanel = hoverTarget
            dockingButtons.showCenterButton = (hoverTarget == viewport)

            val panel = grabbed!!

            if (grabbed != lastGrabbed)
            {
                panel.x.updateType(FIXED)
                panel.y.updateType(FIXED)

                if (panel.width.type != ABSOLUTE)
                {
                    panel.width.updateType(ABSOLUTE)
                    panel.width.value = min(panel.width.value, dockingPanel.width.value / 2f)
                }

                if (panel.height.type != ABSOLUTE)
                {
                    panel.height.updateType(ABSOLUTE)
                    panel.height.value = min(panel.height.value, dockingPanel.height.value / 2f)
                }

                if (panel.parent != dockingPanel)
                {
                    // Center on mouse when window is dragged out of docking
                    panel.x.value = engine.input.xMouse - panel.width.value / 2f
                    panel.y.value = engine.input.yMouse - 10f
                }

                val parent = panel.parent

                panel.removeFromParent()
                addChildren(panel)

                parent?.destroy()
                parent?.setMinSizeFromChildren()

                popup?.hidden = false
            }

            lastGrabbed = grabbed

            if (!grabbed!!.isGrabbed)
                grabbed = null
        }
        else if (lastGrabbed != null)
        {
            popup?.hidden = true
            val panel = lastGrabbed!!

            when
            {
                dockingButtons.leftEdgeHover -> insertLeft(panel)
                dockingButtons.rightEdgeHover -> insertRight(panel)
                dockingButtons.topEdgeHover -> insertTop(panel)
                dockingButtons.bottomEdgeHover -> insertBottom(panel)
                dockingButtons.centerHover -> hoverTarget.insertInsideCenter(panel)
                dockingButtons.leftHover -> hoverTarget.insertInsideHorizontal(panel, leftSide = true)
                dockingButtons.rightHover -> hoverTarget.insertInsideHorizontal(panel, leftSide = false)
                dockingButtons.topHover -> hoverTarget.insertInsideVertical(panel, topSide = true)
                dockingButtons.bottomHover -> hoverTarget.insertInsideVertical(panel, topSide = false)
            }

            lastGrabbed = null
        }
    }

    override fun updateChildLayout()
    {
        super.updateChildLayout()
        keepViewportAutoSize()
    }

    //////////////////////////////////// INSERTION ////////////////////////////////////

    fun insertLeft(panel: WindowPanel) = insertOutsideHorizontal(panel, leftSide = true)
    fun insertRight(panel: WindowPanel) = insertOutsideHorizontal(panel, leftSide = false)
    fun insertTop(panel: WindowPanel) = insertOutsideVertical(panel, topSide = true)
    fun insertBottom(panel: WindowPanel) = insertOutsideVertical(panel, topSide = false)
    fun insertInsideLeft(target: WindowPanel, panel: WindowPanel) = target.insertInsideHorizontal(panel, leftSide = true)
    fun insertInsideRight(target: WindowPanel, panel: WindowPanel) = target.insertInsideHorizontal(panel, leftSide = false)
    fun insertInsideTop(target: WindowPanel, panel: WindowPanel) = target.insertInsideVertical(panel, topSide = true)
    fun insertInsideBottom(target: WindowPanel, panel: WindowPanel) = target.insertInsideVertical(panel, topSide = false)

    private fun insertOutsideHorizontal(panel: WindowPanel, leftSide: Boolean)
    {
        panel.removeFromParent()
        panel.setAuto()
        panel.width.type = ABSOLUTE
        val hPanel = HorizontalPanel()
        hPanel.focusable = false
        hPanel.addPopup(HorizontalPanelResizer(hPanel))
        val childrenToMove = getDockedElements()
        if (leftSide)
            hPanel.addChildren(panel, *childrenToMove.toTypedArray())
        else
            hPanel.addChildren(*childrenToMove.toTypedArray(), panel)
        hPanel.setMinSizeFromChildren()
        this.children.removeAll(childrenToMove)
        this.insertChild(hPanel, 0)
    }

    private fun insertOutsideVertical(panel: WindowPanel, topSide: Boolean)
    {
        panel.removeFromParent()
        panel.setAuto()
        panel.height.type = ABSOLUTE
        val vPanel = VerticalPanel()
        vPanel.focusable = false
        vPanel.addPopup(VerticalPanelResizer(vPanel))
        val childrenToMove = getDockedElements()
        if (topSide)
            vPanel.addChildren(panel, *childrenToMove.toTypedArray())
        else
            vPanel.addChildren(*childrenToMove.toTypedArray(), panel)
        vPanel.setMinSizeFromChildren()
        this.children.removeAll(childrenToMove)
        this.insertChild(vPanel, 0)
    }

    private fun UiElement.insertInsideHorizontal(panel: WindowPanel, leftSide: Boolean)
    {
        panel.removeFromParent()
        val parent = this.parent
        val hPanel = HorizontalPanel()
        hPanel.focusable = false
        hPanel.height.setQuiet(this.height)
        hPanel.width.setQuiet(this.width)
        if (leftSide)
            hPanel.addChildren(panel, this)
        else
            hPanel.addChildren(this, panel)
        hPanel.addPopup(HorizontalPanelResizer(hPanel))
        parent?.replaceChild(this, hPanel)
        hPanel.setMinSizeFromChildren()
        panel.setAuto()
        this.setAuto()
        if (this.width.value - this.minWidth > panel.width.value)
        {
            panel.width.type = ABSOLUTE
            this.width.type = AUTO
        }
    }

    private fun UiElement.insertInsideVertical(panel: WindowPanel, topSide: Boolean)
    {
        panel.removeFromParent()
        val parent = this.parent
        val vPanel = VerticalPanel()
        vPanel.focusable = false
        vPanel.width.setQuiet(this.width)
        vPanel.height.setQuiet(this.height)
        if (topSide)
            vPanel.addChildren(panel, this)
        else
            vPanel.addChildren(this, panel)
        vPanel.addPopup(VerticalPanelResizer(vPanel))
        parent?.replaceChild(this, vPanel)
        vPanel.setMinSizeFromChildren()
        panel.setAuto()
        this.setAuto()
        if (this.height.value - this.minHeight > panel.height.value)
        {
            panel.height.type = ABSOLUTE
            this.height.type = AUTO
        }
    }

    private fun UiElement.insertInsideCenter(panel: WindowPanel)
    {
        if (this == viewport)
        {
            panel.removeFromParent()
            panel.setAuto()
            viewport.addChildren(panel)
            if (viewport.parent == dockingPanel)
            {
                // Move viewport to the back
                viewport.parent?.children?.remove(viewport)
                viewport.parent?.children?.add(0, viewport)
            }
        }
    }

    //////////////////////////////////// UTIL ////////////////////////////////////

    private fun getDockedElements(): List<UiElement> =
        this.children.filter { it !is WindowPanel || it === viewport }

    private fun UiElement.removeFromParent()
    {
        this.parent?.children?.let {
            it.remove(this)
            if (it.isEmpty() && this.parent != viewport)
                this.parent?.removeFromParent()
        }
    }

    private fun UiElement.setAuto()
    {
        this.x.type = PositionType.AUTO
        this.y.type = PositionType.AUTO
        this.width.type = AUTO
        this.height.type = AUTO
    }

    private fun UiElement.destroy()
    {
        if (this.parent != null && this.children.size == 1 && (this is VerticalPanel || this is HorizontalPanel))
        {
            val child = children.first()
            child.width.setQuiet(this.width)
            child.height.setQuiet(this.height)
            this.parent?.replaceChild(this, child)
            this.parent?.destroy()
        }
    }

    private fun UiElement.findGrabbed(): WindowPanel?
    {
        for (child in this.children)
        {
            if (child is WindowPanel)
            {
                if (child.movable && child.isGrabbed)
                {
                    return child
                }
                else if (child === viewport) // Search for maximized windows inside viewport
                {
                    child.findGrabbed()?.let { return it }
                }
            }
            else child.findGrabbed()?.let { return it }
        }

        return null
    }

    private fun findOnHoverTarget(element: UiElement): UiElement?
    {
        if (element === grabbed)
            return null

        for (child in element.children)
        {
            if (child is WindowPanel)
            {
                val isFreeFloatingWindow = (child.movable && element === this) // 'this' is docking root
                if (child.mouseInsideArea && child !== grabbed && !isFreeFloatingWindow)
                    return child
            }
            else findOnHoverTarget(child)?.let { return it }
        }

        return null
    }

    private fun UiElement.setMinSizeFromChildren()
    {
        if (this is VerticalPanel)
        {
            minWidth = ScaledValue.of(children.maxOfOrNull { it.minWidth + it.padding.left + it.padding.right } ?: minWidth.value)
            minHeight = ScaledValue.of(children.sumByFloat { it.minHeight + it.padding.top + it.padding.bottom })
            parent?.setMinSizeFromChildren()
        }
        else if (this is HorizontalPanel)
        {
            minWidth = ScaledValue.of(children.sumByFloat { it.minWidth + it.padding.left + it.padding.right })
            minHeight = ScaledValue.of(children.maxOfOrNull { it.minHeight + it.padding.top + it.padding.bottom } ?: minHeight.value)
            parent?.setMinSizeFromChildren()
        }
    }

    private fun keepViewportAutoSize()
    {
        if (viewport.width.type != AUTO)
        {
            viewport.width.type = AUTO
            if (viewport.parent != dockingPanel)
            {
                viewport.parent?.children
                    ?.firstOrNull { it != viewport }
                    ?.let { it.width.type = ABSOLUTE }
            }
        }

        if (viewport.height.type != AUTO)
        {
            viewport.height.type = AUTO
            if (viewport.parent != dockingPanel)
            {
                viewport.parent?.children
                    ?.firstOrNull { it != viewport }
                    ?.let { it.height.type = ABSOLUTE }
            }
        }
    }

    //////////////////////////////////// SAVING/LOADING LAYOUT ////////////////////////////////////

    fun saveLayout(engine: PulseEngine, layoutFileName: String)
    {
        val layoutGraph = createLayoutGraph(dockingPanel)
        engine.data.saveObject(layoutGraph, layoutFileName)
    }

    fun loadLayout(engine: PulseEngine, layoutFileName: String)
    {
        engine.data.loadObject<LayoutNode>(layoutFileName)?.let { layoutGraph ->
            rebuildLayoutFromGraph(dockingPanel, layoutGraph)?.let { newDockingPanel ->
                dockingPanel.clearChildren()
                dockingPanel.addChildren(*newDockingPanel.children.toTypedArray())
                dockingPanel.findElement("viewport")?.let {
                    viewport = it as Panel
                }
            }
        }
    }

    private fun rebuildLayoutFromGraph(root: UiElement, node: LayoutNode): UiElement? =
        when (node.type)
        {
            DockingPanel::class.simpleName ->
            {
                DockingPanel(node.x, node.y, node.width, node.height).apply()
                {
                    clearChildren() // Removes default viewport
                    node.children
                        .mapNotNull { rebuildLayoutFromGraph(root, it) }
                        .forEachFast { addChildren(it) }
                }
            }
            HorizontalPanel::class.simpleName ->
            {
                HorizontalPanel().apply()
                {
                    addPopup(HorizontalPanelResizer(this))
                    focusable = false
                    width.setQuiet(node.width)
                    height.setQuiet(node.height)
                    node.children
                        .mapNotNull { rebuildLayoutFromGraph(root, it) }
                        .forEachFast { addChildren(it) }
                    setMinSizeFromChildren()
                }
            }
            VerticalPanel::class.simpleName ->
            {
                VerticalPanel().apply {
                    addPopup(VerticalPanelResizer(this))
                    focusable = false
                    width.setQuiet(node.width)
                    height.setQuiet(node.height)
                    node.children
                        .mapNotNull { rebuildLayoutFromGraph(root, it) }
                        .forEachFast { addChildren(it) }

                    setMinSizeFromChildren()
                }
            }
            WindowPanel::class.simpleName ->
            {
                root.findElement(node.id)?.apply {
                    width.setQuiet(node.width)
                    height.setQuiet(node.height)
                    x.setQuiet(node.x)
                    y.setQuiet(node.y)
                }
            }
            else -> null.also { Logger.error { "Found unsupported UI element type in layout config: ${node.type}" } }
        }

    private fun createLayoutGraph(element: UiElement): LayoutNode?
    {
        val children = when
        {
            element is WindowPanel && !element.id.isNullOrBlank() -> emptyList() // Leaf node
            element is DockingPanel || element is HorizontalPanel || element is VerticalPanel ->
            {
                element.children
                    .mapNotNull { createLayoutGraph(it) }
                    .also { if (it.isEmpty()) return null }
            }
            else -> return null
        }

        return LayoutNode(
            id = element.id ?: "",
            type = element::class.simpleName.toString(),
            x = element.x,
            y = element.y,
            width = element.width,
            height = element.height,
            children = children
        )
    }

    data class LayoutNode(
        val id: String,
        val type: String,
        val x: Position,
        val y: Position,
        val width: Size,
        val height: Size,
        val children: List<LayoutNode>
    )
}