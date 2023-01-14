package no.njoh.pulseengine.widgets.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.EDITABLE
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.INVALID_ID
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SELECTED
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.mapToSet
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.UiElement
import no.njoh.pulseengine.modules.gui.elements.Button
import no.njoh.pulseengine.modules.gui.elements.Icon
import no.njoh.pulseengine.modules.gui.elements.InputField
import no.njoh.pulseengine.modules.gui.elements.Label
import no.njoh.pulseengine.modules.gui.layout.*
import kotlin.reflect.full.findAnnotation
import no.njoh.pulseengine.core.shared.annotations.ScnIcon
import no.njoh.pulseengine.modules.gui.ScrollbarVisibility.ALWAYS_VISIBLE
import no.njoh.pulseengine.widgets.editor.EditorUtil.getName
import kotlin.reflect.KClass

data class Outliner(
    val ui: Panel,
    private val onEntitiesSelected: (entities: List<SceneEntity>) -> Unit,
    private val onEntitiesRemoved: (entities: List<SceneEntity>) -> Unit,
    private val onEntitiesAdded: (entities: List<SceneEntity>) -> Unit,
    private val onEntityPropertyChanged: (entity: SceneEntity, propName: String) -> Unit,
    private val onReload: () -> Unit
) {
    fun selectEntities(entities: List<SceneEntity>) = onEntitiesSelected(entities)
    fun removeEntities(entities: List<SceneEntity>) = onEntitiesRemoved(entities)
    fun addEntities(entities: List<SceneEntity>) = onEntitiesAdded(entities)
    fun updateEntityProperty(entity: SceneEntity, propName: String) = onEntityPropertyChanged(entity, propName)
    fun reloadEntitiesFromActiveScene() = onReload()

    companion object
    {
        fun build(
            engine: PulseEngine,
            uiElementFactory: UiElementFactory,
            onEntitiesSelected: () -> Unit,
            onEntityCreated: (KClass<out SceneEntity>) -> Unit,
            onEntityDeleted: () -> Unit
        ): Outliner {

            // ---------------------------------- Entity rows ----------------------------------

            val rowPanel = RowPanel(width = Size.auto(), height = Size.auto()).apply()
            {
                verticalScrollbarVisibility = ALWAYS_VISIBLE
            }

            // ---------------------------------- Header panel ----------------------------------

            val style = uiElementFactory.style
            val visibilityButton = Button(width = Size.absolute(25f)).apply()
            {
                iconFontName = style.iconFontName
                iconCharacter = style.getIcon("HIDDEN")
                color = style.getColor("LABEL")
                hoverColor = style.getColor("LABEL_DARK")
                activeColor = style.getColor("LABEL_DARK")
                setOnClicked {
                    val allVisible = !rowPanel.children.any { (it as Button).isPressed && it.getVisibilityButton().isPressed }
                    rowPanel.children.forEachFast()
                    {
                        if ((it as Button).isPressed)
                        {
                            it.getVisibilityButton().isPressed = allVisible
                            engine.withEntity(it.id) { e -> if (allVisible) e.set(HIDDEN) else e.setNot(HIDDEN) }
                        }
                    }
                }
            }

            val editDisabledButton = Button(width = Size.absolute(25f)).apply()
            {
                iconFontName = style.iconFontName
                iconCharacter = style.getIcon("EDIT_DISABLED")
                color = style.getColor("LABEL")
                hoverColor = style.getColor("LABEL_DARK")
                activeColor = style.getColor("LABEL_DARK")
                setOnClicked {
                    val allEditable = !rowPanel.children.any { (it as Button).isPressed && it.getEditDisabledButton().isPressed }
                    rowPanel.children.forEachFast()
                    {
                        if ((it as Button).isPressed)
                        {
                            it.getEditDisabledButton().isPressed = allEditable
                            engine.withEntity(it.id) { e -> if (allEditable) e.setNot(EDITABLE) else e.set(EDITABLE) }
                        }
                    }
                }
            }

            val headerPanel = HorizontalPanel(height = Size.absolute(25f)).apply()
            {
                color = style.getColor("HEADER")
                addChildren(
                    visibilityButton,
                    Panel(width = Size.absolute(25f)).apply()
                    {
                        strokeColor = style.getColor("STROKE")
                        strokeTop = false
                        strokeBottom = false
                        addChildren(editDisabledButton)
                    },
                    Label(text = "Entity").apply()
                    {
                        padding.left = 10f
                        fontSize = 18f
                        color = style.getColor("LABEL")
                    },
                    Panel(width = Size.absolute(120f + 14f)).apply()
                    {
                        strokeColor = style.getColor("STROKE")
                        strokeTop = false
                        strokeBottom = false
                        strokeRight = false
                        addChildren(Label(text = "Type").apply()
                        {
                            padding.left = 10f
                            fontSize = 18f
                            color = style.getColor("LABEL")
                        })
                    }
                )
            }

            // ---------------------------------- Search bar ----------------------------------

            val searchInputField = InputField(defaultText = "").apply()
            {
                placeHolderText = "Search ..."
                cornerRadius = 8f
                font = style.getFont()
                fontSize = 17f
                textColor = style.getColor("LABEL")
                bgColor = style.getColor("BUTTON")
                bgColorHover = style.getColor("BUTTON_HOVER")
                strokeColor = Color.BLANK
                padding.setAll(5f)
                setOnTextChanged { inputField ->
                    val text = inputField.text
                    rowPanel.children.forEachFast()
                    {
                        it.hidden = text.isNotBlank() && engine.getEntity(it.id)?.matches(engine, text) != true
                    }
                }
            }

            val menuItems = SceneEntity.REGISTERED_TYPES
                .map { it.getName() to it }
                .sortedBy { it.first }
                .map { (name, type) -> MenuBarItem(name) { onEntityCreated(type) } }

            val button = MenuBarButton(labelText = "+", items = menuItems)
            val showScrollBar = menuItems.size > 8
            val buttonUI = uiElementFactory.createMenuBarButtonUI(button, 18f, showScrollBar).apply {
                width.setQuiet(Size.absolute(20f))
                dropdown.resizable = true
                dropdown.color = style.getColor("BUTTON")
                menuLabel.fontSize = 32f
                menuLabel.padding.top = 4f
                padding.top = 5f
                padding.bottom = 5f
                padding.right = 5f
                bgColor = style.getColor("HEADER")
                hoverColor = style.getColor("BUTTON_HOVER")
                cornerRadius = 8f
            }

            val searchPanel = HorizontalPanel(height = Size.absolute(30f)).apply()
            {
                color = style.getColor("HEADER")
                strokeColor = style.getColor("STROKE")
                strokeBottom = true
                strokeTop = false
                strokeLeft = false
                strokeRight = false
                addChildren(searchInputField, buttonUI)
            }

            // ---------------------------------- Outliner with event handlers ----------------------------------

            return Outliner(
                ui = VerticalPanel().apply()
                {
                    addChildren(searchPanel, headerPanel, uiElementFactory.createScrollableSectionUI(rowPanel))
                    setOnKeyPress { key ->
                        if (key == Key.DELETE) true.also { onEntityDeleted() } else false
                    }
                },
                onEntitiesSelected = { entities ->
                    val selectedIds = entities.mapToSet { it.id.toString() }
                    rowPanel.children.forEachFast { (it as Button).isPressed = (it.id in selectedIds) }
                },
                onEntitiesRemoved = { entities ->
                    val idsToRemove = mutableSetOf<String>()
                    entities.forEachFast { it.sourceIdsToRemove(engine, idsToRemove) }
                    val rowsToRemove = rowPanel.children.filter { it.id in idsToRemove }
                    rowPanel.removeChildren(rowsToRemove)
                },
                onEntitiesAdded = { entities ->
                    val searchText = searchInputField.text.trim()
                    val ids = mutableSetOf<Long>()
                    for (entity in entities)
                    {
                        if (entity.isSet(DEAD) || entity.id in ids)
                            continue

                        val parentEntity = if (entity.parentId != INVALID_ID) engine.scene.getEntity(entity.parentId) else null
                        if (parentEntity != null)
                        {
                            val parentId = entity.parentId.toString()
                            val parentIndex = rowPanel.children.indexOfFirst { it.id == parentId }
                            if (parentIndex >= 0)
                            {
                                // Remove and reinsert parent
                                val parentRow = rowPanel.children[parentIndex]
                                rowPanel.removeChildren(rowPanel.getChildrenOf(parentRow))
                                rowPanel.removeChildren(parentRow)
                                rowPanel.addRowsForEntity(parentEntity, engine, style, parentRow.getRowIndentation(), ids, searchText, onEntitiesSelected, parentIndex)
                            }
                        }
                        else rowPanel.addRowsForEntity(entity, engine, style, indent = 0f, ids, searchText, onEntitiesSelected, index = rowPanel.children.size)
                    }
                },
                onEntityPropertyChanged = { entity, propName ->
                    val entityId = entity.id.toString()
                    if (propName == "name")
                        rowPanel.children.find { it.id == entityId }?.getNameLabel()?.text = entity.createLabelText()
                },
                onReload = {
                    rowPanel.clearChildren()
                    rowPanel.addRowsFromActiveScene(engine, style, searchInputField, onEntitiesSelected)
                    closedParents.forEach { id ->
                        rowPanel.children.forEachChildOf(id.toString())
                        {
                            it.hidden = true
                            it.getCollapseButton()?.isPressed = true
                        }
                    }
                }
            )
        }

        private fun SceneEntity.sourceIdsToRemove(engine: PulseEngine, ids: MutableSet<String>)
        {
            val id = this.id.toString()
            if (!ids.contains(id))
            {
                ids.add(id)
                this.childIds?.forEachFast { engine.scene.getEntity(it)?.sourceIdsToRemove(engine, ids) }
            }
        }

        private fun RowPanel.addRowsFromActiveScene(
            engine: PulseEngine,
            style: EditorStyle,
            searchInputField: InputField,
            onEntitiesSelected: () -> Unit
        ) {
            val searchText = searchInputField.text.trim()
            val ids = mutableSetOf<Long>()
            var index = 0
            engine.scene.forEachEntity()
            {
                if (it.parentId == INVALID_ID) // Top level entity
                {
                    index += this.addRowsForEntity(it, engine, style, 0f, ids, searchText, onEntitiesSelected, index)
                }
            }
        }

        private fun RowPanel.addRowsForEntity(
            entity: SceneEntity,
            engine: PulseEngine,
            style: EditorStyle,
            indent: Float,
            addedIds: MutableSet<Long>,
            searchText: String,
            onEntitiesSelected: () -> Unit, // Called when entities are selected in the outliner
            index: Int
        ): Int {
            if (entity.isSet(DEAD) || addedIds.contains(entity.id))
                return 0

            var rowsAdded = 1
            val row = createRowUi(entity, engine, style, indent, this.children, onEntitiesSelected)
            row.hidden = (searchText.isNotBlank() && !entity.matches(engine, searchText)) || entity.parentId in closedParents
            this.insertChild(row, index)
            addedIds.add(entity.id)

            entity.childIds?.forEachFast()
            {
                engine.scene.getEntity(it)?.let { entity ->
                    rowsAdded += this.addRowsForEntity(entity, engine, style, indent + CHILD_INDENTATION, addedIds, searchText, onEntitiesSelected, index + rowsAdded)
                }
            }

            return rowsAdded
        }

        private fun createRowUi(
            entity: SceneEntity,
            engine: PulseEngine,
            style: EditorStyle,
            indent: Float,
            rows: List<UiElement>,
            onEntitiesSelected: () -> Unit // Called when entities are selected in the outliner
        ): UiElement {
            val entityId = entity.id
            val visibilityButton = Button(width = Size.absolute(25f)).apply()
            {
                isPressed = entity.isSet(HIDDEN)
                toggleButton = true
                iconFontName = style.iconFontName
                iconCharacter = style.getIcon("HIDDEN")
                bgHoverColor = style.getColor("BUTTON_HOVER")
                activeColor = style.getColor("LABEL")
                hoverColor = style.getColor("LABEL_DARK")
                setOnClicked { btn ->
                    rows.forEachChildOf(entityId.toString(), includeParent = true) { row ->
                        engine.withEntity(row.id) { if (btn.isPressed) it.set(HIDDEN) else it.setNot(HIDDEN) }
                        row.getVisibilityButton().isPressed = btn.isPressed
                    }
                }
            }

            val editDisabledButton = Button(width = Size.absolute(25f)).apply()
            {
                isPressed = entity.isNot(EDITABLE)
                toggleButton = true
                iconFontName = style.iconFontName
                iconCharacter = style.getIcon("EDIT_DISABLED")
                bgHoverColor = style.getColor("BUTTON_HOVER")
                activeColor = style.getColor("LABEL")
                hoverColor = style.getColor("LABEL_DARK")
                setOnClicked { btn ->
                    rows.forEachChildOf(entityId.toString(), includeParent = true) { row ->
                        engine.withEntity(row.id) { if (btn.isPressed) it.setNot(EDITABLE) else it.set(EDITABLE) }
                        row.getEditDisabledButton().isPressed = btn.isPressed
                    }
                }
            }

            val closeParentButton = if (entity.childIds?.isNotEmpty() == true)
            {
                Button(width = Size.absolute(10f)).apply()
                {
                    padding.left = indent
                    isPressed = entityId in closedParents
                    toggleButton = true
                    iconFontName = style.iconFontName
                    iconSize = 15f
                    iconCharacter = style.getIcon("ARROW_DOWN")
                    xOrigin = 0.25f
                    pressedIconCharacter = style.getIcon("ARROW_RIGHT")
                    color = style.getColor("LABEL")
                    activeColor = style.getColor("LABEL")
                    activeHoverColor = style.getColor("LABEL_DARK")
                    hoverColor = style.getColor("LABEL_DARK")
                    setOnClicked { btn ->
                        rows.forEachChildOf(entityId.toString(), includeParent = false) {
                            it.getCollapseButton()?.isPressed = true // Set collapse button to closed for all children
                            if (btn.isPressed) // Parent being closed
                            {
                                it.hidden = true
                            }
                            else // Parent being opened
                            {
                                // Show only the first layer
                                if (it.getRowIndentation() <= indent + CHILD_INDENTATION)
                                {
                                    it.hidden = false
                                    // If it is a parent, add it to the closedParents list
                                    if (it.getCollapseButton() != null)
                                        it.id?.toLongOrNull()?.let { id -> closedParents.add(id) }
                                }
                                else it.hidden = true
                            }
                        }

                        if (btn.isPressed) closedParents.add(entityId) else closedParents.remove(entityId)
                    }
                }
            }
            else // When entity has no children use basic panel instead of button
            {
                Panel(width = Size.absolute(10f)).apply()
                {
                    padding.left = indent
                    focusable = false
                }
            }

            val annotation = entity::class.findAnnotation<ScnIcon>()
            val icon = Icon(width = Size.absolute(25f)).apply()
            {
                iconSize = 15f
                iconFontName = style.iconFontName
                iconCharacter = style.getIcon(annotation?.iconName ?: "CUBE")
                color = style.getColor("LABEL")
            }

            val nameLabel = Label(entity.createLabelText()).apply()
            {
                padding.left = 4f
                fontSize = 18f
                color = style.getColor("LABEL")
            }

            val typeLabel = Label("  " + entity.typeName, width = Size.absolute(120f)).apply()
            {
                padding.left = 0f
                fontSize = 18f
                color = style.getColor("LABEL")
            }

            val onSelectedCallback = { btn: Button ->
                val isCurrentlyPressed = btn.isPressed
                val isShiftPressed = engine.input.isPressed(Key.LEFT_SHIFT)
                var selectedCount = 0
                if (isShiftPressed && btn.id != lastSelectedId)
                {
                    // Select multiple when shift is pressed
                    var select = false
                    rows.forEachFast()
                    {
                        if (!it.hidden)
                        {
                            val button = it as Button
                            if (button.id == lastSelectedId || button === btn)
                                select = !select
                            else
                            {
                                button.isPressed = select
                                engine.withEntity(button.id) { e -> if (select) e.set(SELECTED) else e.setNot(SELECTED) }
                            }
                        }
                    }
                }
                else if (!engine.input.isPressed(Key.LEFT_CONTROL))
                {
                    // Deselect previously selected rows when left CTRL is not pressed
                    rows.forEachFast()
                    {
                        val button = it as Button
                        if (button.isPressed)
                        {
                            engine.withEntity(button.id) { e -> e.setNot(SELECTED) }
                            button.isPressed = false
                            selectedCount++
                        }
                    }
                }

                btn.isPressed = isCurrentlyPressed || selectedCount > 1
                engine.withEntity(entityId) { e ->  if (btn.isPressed) e.set(SELECTED) else e.setNot(SELECTED) }
                onEntitiesSelected()
                if (btn.isPressed && !isShiftPressed)
                    lastSelectedId = btn.id ?: ""
            }

            return Button(height = Size.absolute(25f)).apply()
            {
                id = entityId.toString()
                toggleButton = true
                isPressed = entity.isSet(SELECTED)
                bgColor = if (rows.size % 2 == 0) style.getColor( "BUTTON") else Color.BLANK
                bgHoverColor = style.getColor("BUTTON_HOVER")
                activeColor = style.getColor("ITEM_HOVER")
                padding.bottom = 1f
                setOnClicked(onSelectedCallback)
                addChildren(
                    HorizontalPanel().apply { addChildren(visibilityButton, editDisabledButton, closeParentButton, icon, nameLabel, typeLabel) }
                )
            }
        }

        private fun UiElement.getVisibilityButton() = this.children[0].children[0] as Button

        private fun UiElement.getEditDisabledButton() = this.children[0].children[1] as Button

        private fun UiElement.getCollapseButton() = this.children[0].children[2] as? Button

        private fun UiElement.getRowIndentation() = this.children[0].children[2].padding.left

        private fun UiElement.getNameLabel() = this.children[0].children[4] as Label

        private fun SceneEntity.createLabelText() = ((this as? Named)?.name?.takeIf { it.isNotEmpty() } ?: typeName)

        private fun SceneEntity.matches(engine: PulseEngine, searchString: String): Boolean =
            typeName.contains(searchString, ignoreCase = true) ||
            (this is Named && name.contains(searchString, ignoreCase = true)) ||
            id.toString().contains(searchString) ||
            childIds?.any { engine.scene.getEntity(it)?.matches(engine, searchString) == true } == true

        private fun PulseEngine.getEntity(id: String?) =
            id?.toLongOrNull()?.let { this.scene.getEntity(it) }

        private inline fun PulseEngine.withEntity(id: String?, action: (SceneEntity) -> Unit) =
            id?.toLongOrNull()?.let { this.scene.getEntity(it)?.let(action) }

        private inline fun PulseEngine.withEntity(id: Long, action: (SceneEntity) -> Unit) =
            this.scene.getEntity(id)?.let(action)

        private inline fun List<UiElement>.forEachChildOf(id: String, includeParent: Boolean = false, action: (UiElement) -> Unit)
        {
            var parentIndent = -1f
            for (row in this)
            {
                if (parentIndent == -1f && row.id == id)
                {
                    parentIndent = row.getRowIndentation()
                    if (includeParent)
                        action(row)
                }
                else if (parentIndent != -1f)
                {
                    if (row.getRowIndentation() <= parentIndent) return
                    action(row)
                }
            }
        }

        private fun RowPanel.getChildrenOf(parent: UiElement): List<UiElement> {
            var i = children.indexOfFirst { it.id == parent.id } + 1
            if (i == 0) return emptyList() // Did not find parent, return
            val parentIndentation = parent.getRowIndentation()
            val children = mutableListOf<UiElement>()
            while (i < this.children.size)
            {
                val child = this.children[i++]
                if (child.getRowIndentation() <= parentIndentation)
                    break
                children.add(child)
            }
            return children
        }

        // Stores the ID of the last selected row
        private var lastSelectedId = ""
        private val closedParents = mutableSetOf<Long>()
        private const val CHILD_INDENTATION = 15f
    }
}