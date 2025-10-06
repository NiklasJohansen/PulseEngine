package no.njoh.pulseengine.widgets.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Asset
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.ui.*
import no.njoh.pulseengine.modules.ui.UiUtils.firstElementOrNull
import no.njoh.pulseengine.modules.ui.UiUtils.hasFocus
import no.njoh.pulseengine.modules.ui.elements.*
import no.njoh.pulseengine.modules.ui.elements.InputField.ContentType.TEXT
import no.njoh.pulseengine.modules.ui.layout.*
import kotlin.reflect.full.findAnnotation

class AssetPicker(
    private val initialAssetName: String,
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : Panel(x, y, width, height) {

    // Outside picker window
    val nameInput: InputField
    val pickerWindow: WindowPanel
    val previewButton: Button
    var previewIconCharacter = ""

    // Inside picker window
    val headerPanel: Panel
    val searchInput: InputField
    val rows: RowPanel
    val scrollbar: Scrollbar
    var rowHeight = 30f

    private var onChanged: (String) -> Unit = { }
    private val rowValues = mutableMapOf<String, UiElement>()

    init
    {
        /////////////////////////////////////////// Rows

        rows = RowPanel()

        scrollbar = Scrollbar(width = Size.absolute(10f), height).apply()
        {
            cornerRadius = ScaledValue.of(2f)
            sliderPadding = ScaledValue.of(1.5f)
            padding.setAll(2f)
            bind(rows, ScrollDirection.VERTICAL)
        }

        /////////////////////////////////////////// Header

        searchInput = InputField("").apply()
        {
            placeHolderText = "Search ..."
            cornerRadius = ScaledValue.of(4f)
            fontSize = ScaledValue.of(17f)
            padding.setAll(5f)
        }

        headerPanel = Panel(height = Size.absolute(30f)).apply()
        {
            strokeBottom = true
            strokeTop = false
            strokeLeft = false
            strokeRight = false
            addChildren(searchInput)
        }

        /////////////////////////////////////////// Picker window

        pickerWindow = WindowPanel(
            width = Size.absolute(300f),
            height = Size.absolute(400f)
        ).apply {
            color = Color.WHITE
            hidden = true
            resizable = true
            minWidth = ScaledValue.of(100f)
            minHeight = ScaledValue.of(100f)
            addChildren(
                VerticalPanel().apply {
                    addChildren(
                        headerPanel,
                        HorizontalPanel().apply {
                            addChildren(rows, scrollbar)
                        }
                    )
                }
            )
        }

        /////////////////////////////////////////// Outside

        nameInput = InputField(initialAssetName).apply()
        {
            contentType = TEXT
            bgColor = Color.BLANK
        }

        previewButton = Button(
            width = Size.absolute(10f),
            height = Size.absolute(10f)
        ).apply {
            cornerRadius = ScaledValue.of(2f)
        }

        val hPanelButton = HorizontalPanel().apply { addChildren(nameInput, previewButton) }

        addChildren(hPanelButton)
        addPopup(pickerWindow)
        createChangeHandlers()
    }

    private fun createChangeHandlers()
    {
        searchInput.setOnTextChanged()
        {
            for ((value, row) in rowValues)
                row.hidden = it.text.isNotBlank() && !value.contains(it.text, ignoreCase = false)
        }

        nameInput.setOnTextChanged()
        {
            val image = rowValues[it.text]?.firstElementOrNull { it is Image }
            setPreviewButtonImage(image)
            onChanged(it.text)
        }

        previewButton.setOnClicked()
        {
            pickerWindow.hidden = !pickerWindow.hidden
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (pickerWindow.isVisible() && !this.hasFocus(engine))
            pickerWindow.hidden = true
    }

    override fun updateChildLayout()
    {
        val size = height.value - previewButton.padding.top - previewButton.padding.bottom
        previewButton.width.setQuiet(size)
        previewButton.height.setQuiet(size)
        super.updateChildLayout()
    }

    override fun updatePopupLayout()
    {
        updatePickerAlignment()
        super.updatePopupLayout()
    }

    private fun updatePickerAlignment()
    {
        var root: UiElement = this
        while (root.parent != null)
            root = root.parent!!

        val isOnRightSide = x.value > root.x.value + root.width.value * 0.5f
        val isOnBottomSide = y.value > root.y.value + root.height.value * 0.5f

        pickerWindow.padding.left = ScaledValue.unscaled(if (isOnRightSide) -pickerWindow.width.value + width.value else 0f)
        pickerWindow.padding.top = ScaledValue.unscaled(if (isOnBottomSide) -pickerWindow.height.value else height.value)
    }

    fun addAssetRow(asset: Asset, style: EditorStyle)
    {
        val rowIconCharacter = style.getIcon(asset::class.findAnnotation<Icon>()?.iconName ?: "BOX")

        val icon = Icon(width = Size.absolute(rowHeight)).apply()
        {
            color = style.getColor("LABEL")
            iconCharacter = rowIconCharacter
            iconFontName = style.iconFontName
            iconSize = ScaledValue.of(15f)
            padding.setAll(5f)
        }

        val label = Label(asset.name).apply()
        {
            color = style.getColor("LABEL")
            fontSize = ScaledValue.of(18f)
            padding.left = ScaledValue.of(0f)
        }

        val image = if (asset is Texture)
        {
            if (asset.name == initialAssetName)
            {
                previewButton.iconCharacter = null
                previewButton.textureAssetName = asset.name
            }
            Image(width = Size.absolute(80f)).apply()
            {
                textureAssetName = asset.name
                padding.setAll(5f)
            }
        }
        else
        {
            Icon(width = Size.absolute(80f)).apply()
            {
                color = style.getColor("LABEL")
                iconCharacter = if (asset is Font) "Abc" else rowIconCharacter
                iconFontName = if (asset is Font) asset.name else style.iconFontName
                iconSize = ScaledValue.of(20f)
                padding.setAll(5f)
            }
        }

        val row = Button(height = Size.absolute(rowHeight)).apply()
        {
            bgColor = if (rows.children.size % 2 == 0) style.getColor("ROW") else Color.BLANK
            hoverColor = style.getColor( "BUTTON_HOVER")
            addChildren(HorizontalPanel().apply() { addChildren(icon, label, image) })
        }

        addRow(row, asset.name)
    }

    fun addRow(row: Button, value: String) {
        row.setOnClicked { btn ->
            setPreviewButtonImage(btn.firstElementOrNull { it is Image })
            nameInput.text = value
            onChanged(value)
        }
        rows.addChildren(row)
        rowValues[value] = row
    }

    fun setOnValueChanged(onChanged: (String) -> Unit)
    {
        this.onChanged = onChanged
    }

    private fun setPreviewButtonImage(previewElement: UiElement?)
    {
        if (previewElement is Image)
        {
            previewButton.iconCharacter = null
            previewButton.textureAssetName = previewElement.textureAssetName
        }
        else previewButton.iconCharacter = previewIconCharacter
    }
}