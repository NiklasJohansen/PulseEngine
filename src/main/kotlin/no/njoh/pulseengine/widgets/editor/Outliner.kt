package no.njoh.pulseengine.widgets.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Key
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
){
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
            onEntityCreated: (KClass<out SceneEntity>) -> Unit
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
                    Label(text = "Name").apply()
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
                        var indent = 0f
                        var index = rowPanel.children.size
                        if (entity.parentId != INVALID_ID)
                        {
                            val parentId = entity.parentId.toString()
                            val parentIndex = rowPanel.children.indexOfFirst { it.id == parentId }
                            if (parentIndex >= 0)
                            {
                                indent = rowPanel.children[parentIndex].getIcon().padding.left - 2f + 15f
                                index = parentIndex + 1
                            }
                        }
                        rowPanel.addRowsForEntity(entity, engine, style, indent, ids, searchText, onEntitiesSelected, index)
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
            engine.scene.forEachEntityTypeList { list -> list.forEachFast()
            {
                if (it.parentId == INVALID_ID) // Top level entity
                {
                    index += this.addRowsForEntity(it, engine, style, 0f, ids, searchText, onEntitiesSelected, index)
                }
            }}
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
            row.hidden = searchText.isNotBlank() && !entity.matches(engine, searchText)
            this.insertChild(row, index)
            addedIds.add(entity.id)

            entity.childIds?.forEachFast()
            {
                engine.scene.getEntity(it)?.let { entity ->
                    rowsAdded += this.addRowsForEntity(entity, engine, style, indent + 15f, addedIds, searchText, onEntitiesSelected, index + rowsAdded)
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

            val annotation = entity::class.findAnnotation<ScnIcon>()
            val icon = Icon(width = Size.absolute(25f)).apply()
            {
                padding.left = 2f + indent
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
                    HorizontalPanel().apply { addChildren(visibilityButton, editDisabledButton, icon, nameLabel, typeLabel) }
                )
            }
        }

        private fun UiElement.getVisibilityButton() = this.children[0].children[0] as Button

        private fun UiElement.getEditDisabledButton() = this.children[0].children[1] as Button

        private fun UiElement.getIcon() = this.children[0].children[2] as Icon

        private fun UiElement.getNameLabel() = this.children[0].children[3] as Label

        private fun SceneEntity.createLabelText() = (name?.takeIf { it.isNotEmpty() } ?: typeName)

        private fun SceneEntity.matches(engine: PulseEngine, searchString: String): Boolean =
            typeName.contains(searchString, ignoreCase = true) ||
            name?.contains(searchString, ignoreCase = true) == true ||
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
            var indent = -1f
            for (row in this)
            {
                if (indent == -1f && row.id == id)
                {
                    indent = row.getIcon().padding.left
                    if (includeParent)
                        action(row)
                }
                else if (indent != -1f)
                {
                    if (row.getIcon().padding.left <= indent) return
                    action(row)
                }
            }
        }

        // Stores the ID of the last selected row
        private var lastSelectedId = ""
    }
}