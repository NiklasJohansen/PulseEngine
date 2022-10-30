package no.njoh.pulseengine.widgets.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.EDITABLE
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
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

data class Outliner(
    val ui: Panel,
    private val onEntitiesSelected: (entities: List<SceneEntity>) -> Unit,
    private val onEntitiesRemoved: (entities: List<SceneEntity>) -> Unit,
    private val onEntitiesAdded: (entities: List<SceneEntity>) -> Unit,
    private val onEntityPropertyChanged: (entity: SceneEntity, propName: String) -> Unit,
    private val onReload: () -> Unit,
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
            onEntitiesSelected: () -> Unit
        ): Outliner {

            // ---------------------------------- Entity rows ----------------------------------

            val rowPanel = RowPanel(width = Size.auto(), height = Size.auto())

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
                        it.hidden = text.isNotBlank() && engine.getEntity(it.id)?.matches(text) != true
                    }
                }
            }

            val searchPanel = Panel(height = Size.absolute(30f)).apply()
            {
                color = style.getColor("HEADER")
                strokeColor = style.getColor("STROKE")
                strokeBottom = true
                strokeTop = false
                strokeLeft = false
                strokeRight = false
                addChildren(searchInputField)
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
                    val removedIds = entities.mapToSet { it.id.toString() }
                    val rowsToRemove = rowPanel.children.filter { it.id in removedIds }
                    rowPanel.removeChildren(rowsToRemove)
                },
                onEntitiesAdded = { entities ->
                    val newRows = entities.map { createRowForEntity(it, engine, style, rowPanel.children, onEntitiesSelected) }
                    rowPanel.addChildren(newRows)
                },
                onEntityPropertyChanged = { entity, propName ->
                    val entityId = entity.id.toString()
                    if (propName == "name")
                        rowPanel.children.find { it.id == entityId }?.getRowLabel()?.text = entity.createLabelText()
                },
                onReload = {
                    val newRows = createRowsFromActiveScene(engine, style, searchInputField, onEntitiesSelected)
                    rowPanel.clearChildren()
                    rowPanel.addChildren(newRows)
                }
            )
        }

        private fun createRowsFromActiveScene(
            engine: PulseEngine,
            style: EditorStyle,
            searchInputField: InputField,
            onEntitiesSelected: () -> Unit
        ): List<UiElement> {
            val searchText = searchInputField.text.trim()
            val rows = mutableListOf<UiElement>()
            for (typeList in engine.scene.getAllEntitiesByType())
            {
                for (entity in typeList)
                {
                    if (entity.isSet(DEAD))
                        continue

                    val row = createRowForEntity(entity, engine, style, rows, onEntitiesSelected)
                    if (searchText.isNotBlank() && !entity.matches(searchText))
                        row.hidden = true
                    rows.add(row)
                }
            }
            rows.sortBy { it.id?.toLongOrNull() ?: 0 }
            return rows
        }

        private fun createRowForEntity(
            entity: SceneEntity,
            engine: PulseEngine,
            style: EditorStyle,
            existingRows: List<UiElement>,
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
                setOnClicked { btn -> engine.withEntity(entityId) { if (btn.isPressed) it.set(HIDDEN) else it.setNot(HIDDEN) } }
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
                setOnClicked { btn -> engine.withEntity(entityId) { if (btn.isPressed) it.setNot(EDITABLE) else it.set(EDITABLE) } }
            }

            val annotation = entity::class.findAnnotation<ScnIcon>()
            val icon = Icon(width = Size.absolute(25f)).apply()
            {
                padding.left = 2f
                iconSize = 15f
                iconFontName = style.iconFontName
                iconCharacter = style.getIcon(annotation?.iconName ?: "CUBE")
                color = style.getColor("LABEL")
            }

            val entityLabel = Label(entity.createLabelText()).apply()
            {
                padding.left = 4f
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
                    existingRows.forEachFast()
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
                    existingRows.forEachFast()
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
                bgColor = if (existingRows.size % 2 == 0) style.getColor( "BUTTON") else Color.BLANK
                bgHoverColor = style.getColor("BUTTON_HOVER")
                activeColor = style.getColor("ITEM_HOVER")
                padding.bottom = 1f
                setOnClicked(onSelectedCallback)
                addChildren(
                    HorizontalPanel().apply { addChildren(visibilityButton, editDisabledButton, icon, entityLabel) }
                )
            }
        }

        private fun UiElement.getVisibilityButton() = this.children[0].children[0] as Button

        private fun UiElement.getEditDisabledButton() = this.children[0].children[1] as Button

        private fun UiElement.getRowLabel() = this.children[0].children[3] as Label

        private fun SceneEntity.createLabelText() = typeName + (name?.let { "  [$it]" } ?: "")

        private fun SceneEntity.matches(searchString: String): Boolean =
            typeName.contains(searchString, ignoreCase = true) ||
            name?.contains(searchString, ignoreCase = true) == true ||
            id.toString().contains(searchString)

        private fun PulseEngine.getEntity(id: String?) =
            id?.toLongOrNull()?.let { this.scene.getEntity(it) }

        private inline fun PulseEngine.withEntity(id: String?, action: (SceneEntity) -> Unit) =
            id?.toLongOrNull()?.let { this.scene.getEntity(it)?.let(action) }

        private inline fun PulseEngine.withEntity(id: Long, action: (SceneEntity) -> Unit) =
            this.scene.getEntity(id)?.let(action)

        // Stores the ID of the last selected row
        private var lastSelectedId = ""
    }
}