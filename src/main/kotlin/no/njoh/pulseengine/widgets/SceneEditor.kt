package no.njoh.pulseengine.widgets

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.*
import no.njoh.pulseengine.data.CursorType.*
import no.njoh.pulseengine.data.assets.Font
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.console.CommandResult
import no.njoh.pulseengine.modules.graphics.CameraInterface
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.modules.graphics.ui.Position
import no.njoh.pulseengine.modules.graphics.ui.Scrollable

import no.njoh.pulseengine.modules.graphics.ui.Size
import no.njoh.pulseengine.modules.graphics.ui.elements.*
import no.njoh.pulseengine.modules.graphics.ui.elements.Button
import no.njoh.pulseengine.modules.graphics.ui.elements.InputField.ContentType.*
import no.njoh.pulseengine.modules.graphics.ui.layout.*
import no.njoh.pulseengine.modules.graphics.ui.layout.docking.DockingPanel
import no.njoh.pulseengine.modules.scene.Scene
import no.njoh.pulseengine.modules.scene.SceneEntity
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.modules.scene.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.util.Camera2DController
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.modules.scene.SpatialIndex
import java.lang.IllegalArgumentException
import kotlin.math.*
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

class SceneEditor: Widget
{
    // UI
    private lateinit var rootUI: DockingPanel
    private lateinit var menuBarUI: HorizontalPanel
    private lateinit var propertiesUI: RowPanel
    private lateinit var propertiesBodyUI: UiElement
    private lateinit var screenArea: FocusArea
    private lateinit var dragAndDropArea: FocusArea
    private var propertyUiRows = mutableMapOf<String, UiElement>()
    private val font = Font.DEFAULT
    private var isVisible = true

    // Camera
    private val cameraController = Camera2DController(Mouse.MIDDLE)
    private lateinit var activeCamera: CameraInterface

    // Scene
    private var scene: Scene? = null
    private val entitySelection = mutableListOf<SceneEntity>()
    private var dragAndDropEntity: SceneEntity? = null
    private var changeToType: KClass<out SceneEntity>? = null

    // Moving and copying
    private var isMoving = false
    private var isCopying = false

    // Rotation
    private var isRotating = false
    private var mouseStartAngle = 0f
    private var entityStartAngle = 0f
    private var entityStartHeight = 0f
    private var entityStartWidth = 0f

    // Resizing
    private var isResizingVertically = false
    private var isResizingHorizontally = false
    private var xResizeDirection = 0f
    private var yResizeDirection = 0f
    private var resizeIconAngle = 0f
    private var xMouseStart = 0f
    private var yMouseStart = 0f

    // Selecting
    private var isSelecting = false
    private var xStartSelect = 0f
    private var yStartSelect = 0f
    private var xEndSelect = 0f
    private var yEndSelect = 0f

    // Loading and saving
    private var shouldLoadAndSaveLayout = false

    override fun onCreate(engine: PulseEngine)
    {
        screenArea = FocusArea(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
        dragAndDropArea = FocusArea(0f, 0f, 0f, 0f)
        activeCamera = engine.gfx.mainCamera

        engine.config.load("/pulseengine/config/editor_config.cfg")
        isVisible = engine.config.getBool("openEditorOnStart") ?: false
        shouldLoadAndSaveLayout = engine.config.getBool("persistEditorLayout") ?: false

        engine.gfx.createSurface2D("sceneEditorSurface")
        engine.console.registerCommand("showSceneEditor") {
            isVisible = !isVisible
            if (!isVisible)
            {
                setScene(null)
                engine.input.setCursor(ARROW)
                if (engine.scene.sceneState == SceneState.STOPPED) {
                    engine.scene.save()
                    engine.scene.start()
                }
            }
            else
            {
                engine.scene.stop()
                engine.scene.reload()
            }

            CommandResult("", showCommand = false)
        }

        createMenuBarUI(engine)
        createSceneEditorUI(engine)

        if (shouldLoadAndSaveLayout)
            rootUI.loadLayout(engine, "/editor_layout.cfg")
    }

    override fun onUpdate(engine: PulseEngine)
    {
         if (!isVisible) return

        if (engine.scene.sceneState == SceneState.STOPPED)
        {
            screenArea.update(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())
            engine.input.requestFocus(screenArea)
        }

        cameraController.update(engine, activeCamera)

        if (engine.scene.activeScene != scene)
            engine.scene.activeScene?.let { setScene(it) }

        if (engine.input.wasClicked(Key.P))
            rootUI.printStructure()

        if (engine.input.wasClicked(Key.G))
            SpatialIndex.draw = !SpatialIndex.draw

        changeToType?.let {
            handleEntityTypeChanged(it)
            changeToType = null
        }

        if (entitySelection.size == 1)
            entitySelection.first().handleEntityTransformation(engine)

        handleEntityDeleting(engine)
        handleEntityCopying(engine)
        handleEntitySelection(engine)
        dragAndDropEntity?.handleEntityDragAndDrop(engine)

        if (isMoving)
        {
            engine.input.setCursor(MOVE)
            entitySelection.forEach { it.handleEntityMoving(engine) }
        }

        rootUI.update(engine)
        menuBarUI.update(engine)
    }

    override fun onRender(engine: PulseEngine)
    {
        if (!isVisible) return

        val uiSurface = engine.gfx.getSurface2D("sceneEditorSurface")
        val showResizeDots = (entitySelection.size == 1)
        entitySelection.forEach { it.renderGizmo(uiSurface, showResizeDots) }
        renderSelection(uiSurface)
        rootUI.render(uiSurface)
        menuBarUI.render(uiSurface)
    }

    //////////////////////////////  CREATE UI  //////////////////////////////

    private fun createMenuBarUI(engine: PulseEngine)
    {
        val filePopup = RowPanel(height = Size.absolute(300f), width = Size.absolute(200f))
        filePopup.padding.top = 25f
        filePopup.color = BG2_COLOR
        filePopup.hidden = true

        val fileLabel = Label("File", x = Position.center(), width = Size.relative(0.5f))
        fileLabel.intractable = false
        fileLabel.padding.top = 2f
        fileLabel.font = font
        fileLabel.color = FONT_COLOR

        val fileButton = Button(width = Size.absolute(70f))
        fileButton.color = TILE_COLOR
        fileButton.colorHover = TILE_COLOR_HOVER
        fileButton.addChildren(fileLabel)
        fileButton.addPopup(filePopup)
        fileButton.setOnClicked { filePopup.hidden = !filePopup.hidden }

        menuBarUI = HorizontalPanel(
            x = Position.alignLeft(),
            y = Position.alignTop(),
            width = Size.auto(),
            height = Size.absolute(25f)
        )
        menuBarUI.color = HEADER_COLOR
        menuBarUI.addChildren(fileButton, Panel())
        menuBarUI.hidden = true
    }

    private fun createSceneEditorUI(engine: PulseEngine)
    {
        // Properties
        propertiesUI = RowPanel()
        propertiesUI.rowHeight = 50f
        propertiesUI.rowPadding = 0f

        val assetPanel = createAssetPanelUI(engine)
        val propertyPanel = createScrollableSectionUI(propertiesUI)

        // Sections
//        val (assetHeader, assetBody) = createClosableSectionUI("Assets", false, assetPanel)
//        val (propHeader, propBody) = createClosableSectionUI("Properties", true, propertyPanel)
//        propertiesBodyUI = propBody

//        val assetUI = createWindowUI("Scene Assets")
//        assetUI.body.addChildren(assetPanel)
//        val entityPropertiesGraphUI = createWindowUI("Entity Properties")
//        entityPropertiesGraphUI.body.addChildren(propertyPanel)
//        propertiesBodyUI = entityPropertiesGraphUI.body
//        val scenePropertiesUI = createWindowUI("Scene Properties")

//        val assetUI = createWindowUI("SCENE EDITOR")
//        sceneEditorUI.body.addChildren(assetHeader, assetBody, propHeader, propBody)
//        val scenePropertiesGUI = createWindowUI("SCENE GRAPH")
//        val entityPropertiesGraphUI = createWindowUI("SCENE PROPERTIES")

//        val assetUI = createSimpleWindowUI("Assets", 300f)
//        val scenePropertiesUI = createSimpleWindowUI("Scene Properties", 300f)
//        val entityPropertiesGraphUI = createSimpleWindowUI("Entity Properties", 100f)
//        val entityPropertiesGraphUI1 = createSimpleWindowUI("Entity Properties1", 100f)
//        val entityPropertiesGraphUI2 = createSimpleWindowUI("Entity Properties2", 100f)
//        val entityPropertiesGraphUI3 = createSimpleWindowUI("Entity Properties3", 100f)

        val assetUI = createSimpleWindowUI("Assets", 50f)
        val scenePropertiesUI = createSimpleWindowUI("Scene Properties", 50f)
        val entityPropertiesGraphUI = createSimpleWindowUI("Entity Properties", 50f)
        val entityPropertiesGraphUI1 = createSimpleWindowUI("Entity Properties1", 50f)
        val entityPropertiesGraphUI2 = createSimpleWindowUI("Entity Properties2", 50f)
        val entityPropertiesGraphUI3 = createSimpleWindowUI("Entity Properties3", 50f)

        propertiesBodyUI = entityPropertiesGraphUI.body

        rootUI = DockingPanel()
        rootUI.update(engine)
        rootUI.insertLeft(entityPropertiesGraphUI)
        rootUI.insertRight(scenePropertiesUI)
        rootUI.insertBottom(assetUI)
        rootUI.insertTop(entityPropertiesGraphUI1)
        rootUI.insertLeft(entityPropertiesGraphUI2)
        rootUI.insertRight(entityPropertiesGraphUI3)
//        editor.addChildren(assetUI, scenePropertiesGUI, entityPropertiesGraphUI)
    }

    private fun createSimpleWindowUI(title: String, minWidth: Float): WindowPanel
    {
        val windowPanel = WindowPanel(
            x = Position.fixed(0f),
            y = Position.fixed(20f),
            width = Size.absolute(200f),
            height = Size.absolute(200f)
        )
        val color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat())
        windowPanel.id = title
        windowPanel.color = color
        windowPanel.header.color = Color(color.red * 0.7f, color.green * 0.7f, color.blue * 0.7f)
        windowPanel.movable = true
        windowPanel.resizable = true
        windowPanel.minHeight = 200f // 430
        windowPanel.minWidth = minWidth
        windowPanel.minHeight = windowPanel.header.height.value
        return windowPanel
    }

    private fun createWindowUI(title: String): WindowPanel
    {
        val label = Label(title)
        label.padding.left = 10f
        label.font = font
        label.color = FONT_COLOR
        label.intractable = false

        val exitButton = Button(width = Size.absolute(15f), height = Size.absolute(15f))
        exitButton.padding.top = 7.5f
        exitButton.padding.right = 10f
        exitButton.color = Color(0.8f, 0.2f, 0.2f)
        exitButton.colorHover = Color(1f, 0.4f, 0.4f)
        exitButton.setOnClicked { println("EXIT") }

        val headerPanel = HorizontalPanel(height = Size.absolute(30f))
        headerPanel.color = HEADER_COLOR
        headerPanel.intractable = false
        headerPanel.addChildren(label, exitButton)

        val windowPanel = WindowPanel(
            x = Position.fixed(0f),
            y = Position.fixed(20f),
            width = Size.absolute(300f),
            height = Size.absolute(200f)
        )

        windowPanel.color = BG_COLOR
        windowPanel.strokeColor = HEADER_COLOR
        windowPanel.movable = true
        windowPanel.resizable = true
        windowPanel.minHeight = windowPanel.header.height.value
        windowPanel.minWidth = 50f
        windowPanel.id = title

        windowPanel.header.addChildren(headerPanel)

        return windowPanel
    }

    private fun createClosableSectionUI(sectionName: String, hidden: Boolean, vararg children: UiElement): Array<UiElement>
    {
        val body = VerticalPanel()
        body.color = BG2_COLOR
        body.hidden = hidden
        body.addChildren(*children)

        val label = Label(sectionName)
        label.padding.left = 10f
        label.font = font
        label.color = FONT_COLOR
        label.intractable = false

        val header = Button(height = Size.absolute(30f))
        header.padding.top = 10f
        header.color = HEADER_COLOR
        header.colorHover = HEADER_COLOR_HOVER_DARK
        header.addChildren(label)
        header.setOnClicked {
            body.hidden = !body.hidden
        }

        return arrayOf(header, body)
    }

    private fun createScrollableSectionUI(panel: UiElement): HorizontalPanel
    {
        if (panel !is Scrollable)
            throw IllegalArgumentException("${panel::class.simpleName} is not Scrollable")

        val hPanel = HorizontalPanel()
        hPanel.color = BG2_COLOR
        hPanel.addChildren(panel, createScrollbarUI(panel))

        return hPanel
    }

    private fun createScrollbarUI(scrollBinding: Scrollable): Scrollbar
    {
        val scrollbar = Scrollbar(width = Size.absolute(20f))
        scrollbar.bgColor = TILE_COLOR
        scrollbar.sliderColor = HEADER_COLOR
        scrollbar.sliderColorHover = HEADER_COLOR_HOVER_DARK
        scrollbar.padding.top = 5f
        scrollbar.padding.bottom = 5f
        scrollbar.padding.right = 5f
        scrollbar.sliderPadding = 3f
        scrollbar.bind(scrollBinding)
        return scrollbar
    }

    private fun createAssetPanelUI(engine: PulseEngine): UiElement
    {
        val tilePanel = TilePanel()
        tilePanel.horizontalTiles = 5
        tilePanel.maxTileSize = 80f
        tilePanel.tilePadding = 5f

        val textureAssets = engine.asset.getAll(Texture::class.java)
        for (texture in textureAssets)
        {
            val tile = Button()
            tile.bgColor = TILE_COLOR
            tile.bgColorHover = TILE_COLOR_HOVER
            tile.textureScale = 0.9f
            tile.texture = texture
            tile.setOnClicked { createDragAndDropEntity(engine, texture) }
            tilePanel.addChildren(tile)
        }

        return createScrollableSectionUI(tilePanel)
    }

    private fun <T> createDropdownUI(selectedItem: T, items: List<T>, onItemToString: (T) -> String): DropdownMenu<T>
    {
        val dropdown = DropdownMenu<T>(
            width = Size.relative(0.5f),
            dropDownWidth = Size.absolute(250f),
            dropDownHeight = Size.absolute(180f)
        )

        dropdown.padding.top = 5f
        dropdown.padding.bottom = 5f
        dropdown.padding.right = 5f
        dropdown.rowPanel.rowPadding = 5f
        dropdown.menuLabel.font = font
        dropdown.menuLabel.color = FONT_COLOR
        dropdown.menuLabel.padding.left = 10f
        dropdown.bgColor = INPUT_COLOR
        dropdown.bgColorHover = INPUT_COLOR_HOVER_LIGHT
        dropdown.dropdown.color = BG_COLOR
        dropdown.scrollbar.sliderColor = HEADER_COLOR
        dropdown.scrollbar.sliderColorHover = HEADER_COLOR_HOVER_DARK
        dropdown.scrollbar.bgColor = TILE_COLOR
        dropdown.setOnItemToString(onItemToString)
        dropdown.selectedItem = selectedItem
        items.forEach(dropdown::addItem)

        return dropdown
    }

    private fun createEntityTypePropertyUI(entity: SceneEntity)
    {
        val typeLabel = Label("Entity type", width = Size.relative(0.5f))
        typeLabel.padding.setAll(5f)
        typeLabel.padding.left = 10f
        typeLabel.font = font
        typeLabel.color = FONT_COLOR

        val typeDropdown = createDropdownUI(entity::class, SceneEntity.REGISTERED_TYPES.toList()) { it.simpleName ?: "NO NAME" }
        typeDropdown.setOnItemChanged { changeToType = it }

        val typeHPanel = HorizontalPanel()
        typeHPanel.padding.left = 5f
        typeHPanel.padding.right = 5f
        typeHPanel.padding.top = 5f
        typeHPanel.color = TILE_COLOR
        typeHPanel.addChildren(typeLabel, Panel(), typeDropdown)

        propertiesUI.addChildren(typeHPanel)
    }

    private fun createPropertyUI(entity: SceneEntity, prop: KMutableProperty<*>)
    {
        val propValue = prop.getter.call(entity)?.toString() ?: "nan"
        val propType = prop.javaField?.type

        val propUi: UiElement = if (propType?.kotlin?.isSubclassOf(Enum::class) == true)
        {
            val items = propType.enumConstants?.toList() ?: emptyList()
            val dropdownMenu = createDropdownUI(prop.getter.call(entity), items) { it.toString() }
            dropdownMenu.setOnItemChanged { it?.let { entity.setProperty(prop.name, it) } }
            dropdownMenu
        }
        else
        {
            val contentType = when (propType)
            {
                Float::class.java, Double::class.java -> FLOAT
                Int::class.java, Long::class.java, Char::class.java -> INTEGER
                Boolean::class.java -> BOOLEAN
                else -> TEXT
            }

            val inputField = InputField(propValue, width = Size.relative(0.5f))
            inputField.padding.top = 5f
            inputField.padding.bottom = 5f
            inputField.padding.right = 5f
            inputField.font = font
            inputField.fontColor = FONT_COLOR
            inputField.bgColor = INPUT_COLOR
            inputField.editable = true
            inputField.contentType = contentType
            inputField.setOnTextChanged { if (it.isValid) entity.setPropertyFromString(prop, it.text) }

            if (contentType == FLOAT || contentType == INTEGER)
                prop.findAnnotation<ValueRange>()?.let {

                    inputField.numberMinVal = it.min
                    inputField.numberMaxVal = it.max
                }

            inputField
        }

        val label = Label(prop.name, width = Size.relative(0.5f))
        label.padding.setAll(5f)
        label.padding.left = 10f
        label.font = font
        label.color = FONT_COLOR

        val hPanel = HorizontalPanel()
        hPanel.padding.left = 5f
        hPanel.padding.right = 5f
        hPanel.padding.top = 5f
        hPanel.color = TILE_COLOR
        hPanel.addChildren(label, propUi)

        propertiesUI.addChildren(hPanel)
        propertyUiRows[prop.name] = propUi
    }

    ////////////////////////////// EDIT TOOLS  //////////////////////////////

    private fun handleEntityDeleting(engine: PulseEngine)
    {
        if (engine.input.isPressed(Key.DELETE))
        {
            if (entitySelection.isNotEmpty())
            {
                entitySelection.forEach { it.set(DEAD) }
                entitySelection.clear()
            }
        }
    }

    private fun handleEntityCopying(engine: PulseEngine)
    {
        if (engine.input.isPressed(Key.LEFT_CONTROL))
        {
            if (isMoving && !isCopying)
            {
                val copies = entitySelection.map { it.createCopy() }

                if (copies.size == 1)
                    selectSingleEntity(copies.first())
                else
                {
                    entitySelection.clear()
                    propertyUiRows.clear()
                    propertiesUI.clearChildren()
                    entitySelection.addAll(copies)
                }

                copies.forEach { scene?.addEntity(it) }
                isCopying = true
            }
        } else isCopying = false
    }

    private fun handleEntitySelection(engine: PulseEngine)
    {
        val xMouse = engine.input.xWorldMouse
        val yMouse = engine.input.yWorldMouse

        if (engine.input.wasClicked(Mouse.LEFT))
        {
            // Select entity
            if (!isMoving && !isSelecting && !isRotating && !isResizingVertically && !isResizingHorizontally)
            {
                scene?.let { scene ->

                    scene.entityTypes.forEach { (type, entities) ->

                        for (i in entities.size - 1 downTo 0)
                        {
                            val entity = entities[i]
                            if (entity.isInside(xMouse, yMouse))
                            {
                                if (entity !in entitySelection)
                                {
                                    entitySelection.clear()
                                    selectSingleEntity(entity)
                                }

                                isMoving = true
                                break
                            }
                        }
                    }
                }
            }
        }

        if (engine.input.isPressed(Mouse.LEFT))
        {
            if (!isMoving && !isRotating && !isResizingVertically && !isResizingHorizontally)
            {
                xEndSelect = xMouse
                yEndSelect = yMouse

                if (!isSelecting)
                {
                    xStartSelect = xEndSelect
                    yStartSelect = yEndSelect
                    isSelecting = true
                }
            }
        }
        else isSelecting = false

        if (isSelecting)
        {
            scene?.let {

                val xStart = min(xStartSelect, xEndSelect)
                val yStart = min(yStartSelect, yEndSelect)
                val width  = abs(xEndSelect - xStartSelect)
                val height  = abs(yEndSelect - yStartSelect)
                val selectedEntity = entitySelection.firstOrNull()

                entitySelection.clear()

                it.entityTypes.forEach { (type, entities) ->
                    for (entity in entities)
                    {
                        if (entity.isOverlapping(xStart, yStart, width, height))
                            entitySelection.add(entity)
                    }
                }

                if (entitySelection.size == 1)
                {
                    if (entitySelection.first() != selectedEntity)
                        selectSingleEntity(entitySelection.first())
                }
                else propertiesUI.clearChildren()
            }
        }

        var xMove = 0f
        var yMove = 0f
        if (engine.input.wasClicked(Key.UP)) yMove -= 1
        if (engine.input.wasClicked(Key.DOWN)) yMove += 1
        if (engine.input.wasClicked(Key.LEFT)) xMove -= 1
        if (engine.input.wasClicked(Key.RIGHT)) xMove += 1
        if (xMove != 0f || yMouse != 0f)
        {
            entitySelection.forEach {
                it.x += xMove
                it.y += yMove
                it.set(POSITION_UPDATED)
            }
        }
    }

    private fun SceneEntity.handleEntityMoving(engine: PulseEngine)
    {
        if (isMoving && !engine.input.isPressed(Mouse.LEFT))
        {
            engine.input.setCursor(ARROW)
            isMoving = false
        }

        this.x += engine.input.xdMouse / activeCamera.xScale
        this.y += engine.input.ydMouse / activeCamera.yScale
        this.set(POSITION_UPDATED)

        updatePropertiesPanel(::x.name, x)
        updatePropertiesPanel(::y.name, y)
    }

    private fun SceneEntity.handleEntityDragAndDrop(engine: PulseEngine)
    {
        if (this !in entitySelection)
        {
            selectSingleEntity(this)
        }

        engine.input.acquireFocus(dragAndDropArea)
        this.x = engine.input.xWorldMouse
        this.y = engine.input.yWorldMouse

        if (!engine.input.isPressed(Mouse.LEFT))
        {
            dragAndDropEntity = null
            scene?.addEntity(this)
        }
    }

    private fun SceneEntity.handleEntityTransformation(engine: PulseEngine)
    {
        val border = min(abs(width), abs(height)) * 0.1f
        val rotateArea = max(abs(width), abs(height)) * 0.2f

        val xDiff = engine.input.xWorldMouse - x
        val yDiff = engine.input.yWorldMouse - y
        val mouseEntityAngle = atan2(yDiff, xDiff)
        val angle = mouseEntityAngle - (this.rotation / 180f * PI.toFloat())
        val len = sqrt(xDiff * xDiff + yDiff * yDiff)
        val xMouse = x + cos(angle) * len
        val yMouse = y + sin(angle) * len
        val w = (abs(width) + GIZMO_PADDING * 2) / 2
        val h = (abs(height) + GIZMO_PADDING * 2) / 2

        val resizeBottom = xMouse >= x - w - border && xMouse <= x + w + border && yMouse >= y + h - border && yMouse <= y + h + border
        val resizeTop = xMouse >= x - w - border && xMouse <= x + w + border && yMouse >= y - h - border && yMouse <= y - h + border
        val resizeLeft = xMouse >= x - w - border && xMouse <= x - w + border && yMouse >= y - h - border && yMouse <= y + h + border
        val resizeRight = xMouse >= x + w - border && xMouse <= x + w + border && yMouse >= y - h - border && yMouse <= y + h + border

        val rotateTopLeft = xMouse >= x - w - rotateArea && xMouse <= x - w && yMouse >= y - h - rotateArea && yMouse <= y - h
        val rotateBottomLeft = xMouse >= x - w - rotateArea && xMouse <= x - w && yMouse >= y + h && yMouse <= y + h + rotateArea
        val rotateTopRight = xMouse >= x + w && xMouse <= x + w + rotateArea && yMouse >= y - h - rotateArea && yMouse <= y - h
        val rotateBottomRight = xMouse >= x + w && xMouse <= x + w + rotateArea && yMouse >= y + h && yMouse <= y + h + rotateArea

        if (engine.input.isPressed(Mouse.LEFT))
        {
            if (!isMoving && !isSelecting && !isRotating && !isResizingHorizontally && !isResizingVertically)
            {
                if (resizeBottom || resizeTop)
                {
                    isResizingVertically = true
                    entityStartWidth = width
                    entityStartHeight = height
                    xMouseStart = xMouse
                    yMouseStart = yMouse
                    yResizeDirection = (if (yMouse > y) -1f else 1f) * (if (height < 0) -1f else 1f)  // Inverts scaling when mouse is on opposite side or height is inverted
                    resizeIconAngle = getIconAngle(rotation, resizeLeft, resizeRight, resizeTop, resizeBottom)
                }

                if (resizeLeft || resizeRight)
                {
                    isResizingHorizontally = true
                    entityStartWidth = width
                    entityStartHeight = height
                    xMouseStart = xMouse
                    yMouseStart = yMouse
                    xResizeDirection = (if (xMouse > x) -1f else 1f) * (if (width < 0) -1f else 1f)
                    resizeIconAngle = getIconAngle(rotation, resizeLeft, resizeRight, resizeTop, resizeBottom)
                }

                if (!isResizingVertically && !isResizingHorizontally && (rotateTopLeft || rotateBottomLeft || rotateTopRight || rotateBottomRight))
                {
                    isRotating = true
                    entityStartAngle = this.rotation
                    mouseStartAngle = mouseEntityAngle
                }
            }
        }
        else
        {
            isResizingVertically = false
            isResizingHorizontally = false
            isRotating = false
        }

        val ctrPressed = engine.input.isPressed(Key.LEFT_CONTROL)
        val shiftPressed = engine.input.isPressed(Key.LEFT_SHIFT)

        when
        {
            // Rotating
            isRotating -> {
                val diff = (mouseEntityAngle - mouseStartAngle) / PI.toFloat() * 180f
                rotation = if (ctrPressed)
                    ((entityStartAngle + diff).toInt() / 45 * 45).toFloat()
                else
                    entityStartAngle + diff
            }

            // Horizontal / corner resize, keep ratio
            isResizingHorizontally && shiftPressed -> {
                val xd = (xMouseStart - xMouse)
                val ratio = entityStartHeight / if (entityStartWidth == 0f) entityStartHeight else entityStartWidth
                width = entityStartWidth + xd * xResizeDirection
                height = entityStartHeight + ratio * xd * xResizeDirection
            }

            // Vertical resize, keep ratio
            isResizingVertically && shiftPressed -> {
                val yd = (yMouseStart - yMouse)
                val ratio = entityStartWidth / if (entityStartHeight == 0f) entityStartWidth else entityStartHeight
                height = entityStartHeight + yd * yResizeDirection
                width = entityStartWidth + ratio * yd * yResizeDirection
            }

            // Corner resize
            isResizingHorizontally && isResizingVertically -> {
                width = entityStartWidth + (xMouseStart - xMouse) * xResizeDirection
                height = entityStartHeight + (yMouseStart - yMouse) * yResizeDirection
            }

            // Horizontal resize
            isResizingHorizontally -> width = entityStartWidth + (xMouseStart - xMouse) * xResizeDirection

            // Vertical resize
            isResizingVertically -> height = entityStartHeight + (yMouseStart - yMouse) * yResizeDirection
        }

        // Update resize icon angle
        if (!isResizingHorizontally && !isResizingVertically)
            resizeIconAngle = getIconAngle(rotation, resizeLeft, resizeRight, resizeTop, resizeBottom)

        // Determine icon type
        val cursorType =
            if (!isRotating && (isResizingHorizontally || isResizingVertically || resizeBottom || resizeTop || resizeLeft || resizeRight))
                when (resizeIconAngle)
                {
                    in   0f ..  22f -> HORIZONTAL_RESIZE
                    in  22f ..  68f -> TOP_LEFT_RESIZE
                    in  68f .. 112f -> VERTICAL_RESIZE
                    in 112f .. 158f -> TOP_RIGHT_RESIZE
                    in 158f .. 202f -> HORIZONTAL_RESIZE
                    in 202f .. 248f -> TOP_LEFT_RESIZE
                    in 248f .. 292f -> VERTICAL_RESIZE
                    in 292f .. 338f -> TOP_RIGHT_RESIZE
                    in 338f .. 360f -> HORIZONTAL_RESIZE
                    else            -> ARROW
                }
            else if (isRotating || rotateTopLeft || rotateBottomLeft || rotateTopRight || rotateBottomRight)
                ROTATE
            else if (xMouse > x - w  && xMouse < x + w && yMouse > y - h && yMouse < y + h)
                MOVE
            else ARROW

        engine.input.setCursor(cursorType)

        if (isRotating || isResizingHorizontally || isResizingVertically)
        {
            updatePropertiesPanel(::rotation.name, rotation)
            updatePropertiesPanel(::width.name, width)
            updatePropertiesPanel(::height.name, height)
            set(SIZE_UPDATED)
            set(ROTATION_UPDATED)
        }
    }

    private fun getIconAngle(startAngle: Float, resizeLeft: Boolean, resizeRight: Boolean, resizeTop: Boolean, resizeBottom: Boolean): Float
    {
        var iconAngle = startAngle + when
        {
            resizeTop && resizeRight -> 135
            resizeRight && resizeBottom -> 225
            resizeBottom && resizeLeft -> 315
            resizeLeft && resizeTop -> 45
            resizeRight -> 180
            resizeLeft -> 0
            resizeTop -> 90
            resizeBottom -> 270
            else -> 0
        }

        iconAngle %= 360
        if (iconAngle < 0)
            iconAngle += 360

        return iconAngle
    }

    private fun createDragAndDropEntity(engine: PulseEngine, texture: Texture)
    {
        if (dragAndDropEntity == null)
        {
            val type = SceneEntity.REGISTERED_TYPES
                .find { it.memberProperties.any { it.name == "textureName" } }
                ?: SceneEntity.REGISTERED_TYPES.firstOrNull()

            type?.let {
                    val entity = it.constructors.first().call()
                    entity.x = engine.input.xWorldMouse
                    entity.y = engine.input.yWorldMouse
                    entity.width = texture.width.toFloat()
                    entity.height = texture.height.toFloat()
                    entity.setProperty("textureName", texture.name)
                    dragAndDropEntity = entity
                }
        }
    }

    private fun selectSingleEntity(entity: SceneEntity)
    {
        entitySelection.clear()
        entitySelection.add(entity)
        propertiesUI.clearChildren()
        propertiesBodyUI.hidden = false
        propertyUiRows.clear()

        createEntityTypePropertyUI(entity)

        for (prop in entity::class.memberProperties)
        {
            if (prop !is KMutableProperty<*> || prop.visibility == KVisibility.PRIVATE)
                continue

            createPropertyUI(entity, prop)
        }
    }

    private fun handleEntityTypeChanged(type: KClass<out SceneEntity>)
    {
        if (entitySelection.size != 1)
            return

        val oldEntity = entitySelection.first()

        try {
            val newEntity = type.constructors.first().call()

            oldEntity::class.memberProperties.forEach { prop ->
                if (prop.visibility == KVisibility.PUBLIC)
                    prop.getter.call(oldEntity)?.let { value -> newEntity.setProperty(prop.name, value) }
            }

            oldEntity.set(DEAD)
            scene?.addEntity(newEntity)
            selectSingleEntity(newEntity)
        }
        catch (e: Exception)
        {
            Logger.error("Failed to change entity type, reason: ${e.message}")
        }
    }

    ////////////////////////////// RENDERING  //////////////////////////////

    private fun renderSelection(surface: Surface2D)
    {
        if (isSelecting)
        {
            val pos = activeCamera.worldPosToScreenPos(xStartSelect, yStartSelect)
            val x = pos.x
            val y = pos.y
            val w = (xEndSelect - xStartSelect) * activeCamera.xScale
            val h = (yEndSelect - yStartSelect) * activeCamera.yScale

            surface.setDrawColor(1f, 1f, 1f, 0.8f)
            surface.drawLine(x, y, x + w, y)
            surface.drawLine(x, y + h, x + w, y + h)
            surface.drawLine(x, y, x, y + h)
            surface.drawLine(x + w, y, x + w, y + h)
        }
    }

    private fun SceneEntity.renderGizmo(surface: Surface2D, showResizeDots: Boolean)
    {
        val pos = activeCamera.worldPosToScreenPos(x, y)
        val w = (width + GIZMO_PADDING * 2) * activeCamera.xScale / 2f
        val h = (height + GIZMO_PADDING * 2) * activeCamera.yScale / 2f
        val size = 4f
        val halfSize = size / 2f

        if (rotation != 0f)
        {
            val r = this.rotation / 180f * PI.toFloat()
            val c = cos(r)
            val s = sin(r)
            val x0 = -w * c - h * s
            val y0 = -w * s + h * c
            val x1 =  w * c - h * s
            val y1 =  w * s + h * c

            surface.setDrawColor(1f, 1f, 1f, 0.8f)
            surface.drawLine(pos.x + x0, pos.y + y0, pos.x + x1, pos.y + y1)
            surface.drawLine(pos.x + x1, pos.y + y1, pos.x - x0, pos.y - y0)
            surface.drawLine(pos.x - x0, pos.y - y0, pos.x - x1, pos.y - y1)
            surface.drawLine(pos.x - x1, pos.y - y1, pos.x + x0, pos.y + y0)

            if (showResizeDots)
            {
                surface.setDrawColor(1f, 1f, 1f, 1f)
                surface.drawQuad(pos.x + x0 - halfSize, pos.y + y0 - halfSize, size, size)
                surface.drawQuad(pos.x + x1 - halfSize, pos.y + y1 - halfSize, size, size)
                surface.drawQuad(pos.x - x0 - halfSize, pos.y - y0 - halfSize, size, size)
                surface.drawQuad(pos.x - x1 - halfSize, pos.y - y1 - halfSize, size, size)
            }
        }
        else
        {
            surface.setDrawColor(1f, 1f, 1f, 0.8f)
            surface.drawLine(pos.x - w, pos.y - h, pos.x + w, pos.y - h)
            surface.drawLine(pos.x - w, pos.y + h, pos.x + w, pos.y + h)
            surface.drawLine(pos.x - w, pos.y - h, pos.x - w, pos.y + h)
            surface.drawLine(pos.x + w, pos.y - h, pos.x + w, pos.y + h)

            if (showResizeDots)
            {
                surface.setDrawColor(1f, 1f, 1f, 1f)
                surface.drawQuad(pos.x - w - halfSize, pos.y - h - halfSize, size, size)
                surface.drawQuad(pos.x + w - halfSize, pos.y - h - halfSize, size, size)
                surface.drawQuad(pos.x - w - halfSize, pos.y + h - halfSize, size, size)
                surface.drawQuad(pos.x + w - halfSize, pos.y + h - halfSize, size, size)
            }
        }
    }

    ////////////////////////////// SceneEntity UTILS  //////////////////////////////

    private fun SceneEntity.isInside(xWorld: Float, yWorld: Float): Boolean
    {
        val w = (abs(width) + GIZMO_PADDING * 2)
        val h = (abs(height) + GIZMO_PADDING * 2)
        val xDiff = xWorld - x
        val yDiff = yWorld - y
        val angle = atan2(yDiff, xDiff) - (this.rotation / 180f * PI.toFloat())
        val len = sqrt(xDiff * xDiff + yDiff * yDiff)
        val xWorldNew = x + cos(angle) * len
        val yWorldNew = y + sin(angle) * len

        return xWorldNew > x - w / 2f && xWorldNew < x + w / 2 && yWorldNew > y - h / 2f && yWorldNew < y + h / 2f
    }

    private fun SceneEntity.isOverlapping(xWorld: Float, yWorld: Float, width: Float, height: Float): Boolean
    {
        return this.x > xWorld && this.x < xWorld + width && this.y > yWorld && this.y < yWorld + height
    }

    private fun SceneEntity.setProperty(name: String, value: Any)
    {
        val prop = this::class.memberProperties.find { it.name == name }
        if (prop != null && prop is KMutableProperty<*>)
        {
            try { prop.setter.call(this, value) }
            catch (e: Exception) { Logger.error("Failed to set property with name: $name, reason: ${e.message}") }
        }
    }

    private fun SceneEntity.setPropertyFromString(property: KMutableProperty<*>, value: String)
    {
        try
        {
            when (property.javaField?.type)
            {
                String::class.java  -> value
                Int::class.java     -> value.toIntOrNull()
                Float::class.java   -> value.toFloatOrNull()
                Double::class.java  -> value.toDoubleOrNull()
                Long::class.java    -> value.toLongOrNull()
                Boolean::class.java -> value.toBoolean()
                else                -> null
            }
                ?.let { property.setter.call(this, it) }
        }
        catch (e: Exception) { Logger.error("Failed to parse value: $value into required type: ${property.javaField?.type}, reason: ${e.message}") }
    }

    private fun SceneEntity.createCopy(): SceneEntity
    {
        val copy = this::class.constructors.first().call()
        for (prop in this::class.members)
        {
            if (prop is KMutableProperty<*> && prop.visibility == KVisibility.PUBLIC)
                prop.getter.call(this)?.let { copy.setProperty(prop.name, it) }
        }
        return copy
    }

    private fun updatePropertiesPanel(propName: String, value: Any)
    {
        propertyUiRows[propName]?.let {
            if (it is InputField)
                it.text = value.toString()
        }
    }

    private fun setScene(scene: Scene?)
    {
        Logger.debug("Setting scene")
        this.scene = scene
        dragAndDropEntity = null
        changeToType = null
        isMoving = false
        isSelecting = false
        isCopying = false
        isRotating = false
        isResizingVertically = false
        isResizingHorizontally = false
        propertiesUI.clearChildren()
        propertyUiRows.clear()
        entitySelection.clear()
    }

    override fun onDestroy(engine: PulseEngine) {
        if (shouldLoadAndSaveLayout)
            rootUI.saveLayout(engine, "/editor_layout.cfg")
    }

    companion object
    {
        val INPUT_COLOR = Color(41, 43, 46)
        val INPUT_COLOR_HOVER_LIGHT = Color(46, 48, 51)
        val HEADER_COLOR = Color(46, 48, 51)
        val HEADER_COLOR_HOVER_LIGHT = Color(46/255f*1.08f, 48/255f*1.08f, 51/255f*1.08f, 1f)
        val HEADER_COLOR_HOVER_DARK = Color(46/255f*0.92f, 48/255f*0.92f, 51/255f*0.92f, 1f)
        val BG_COLOR = Color(59, 63, 67)
        val BG2_COLOR = Color(54, 58, 62)
        val TILE_COLOR = Color(52, 53, 56)
        val TILE_COLOR_HOVER = Color(52/255f*1.08f, 53/255f*1.08f, 56/255f*1.08f, 1f)
        val STROKE_COLOR = Color(77/255f*2f, 80/255f*2f, 85/255f*2f, 1f)
        val FONT_COLOR = Color(212, 214, 218)
        const val GIZMO_PADDING = 3
    }
}

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValueRange(val min: Float, val max: Float)