package no.njoh.pulseengine.modules.ui.elements

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.AxisColors
import no.njoh.pulseengine.modules.ui.*
import no.njoh.pulseengine.modules.ui.elements.InputField.ContentType.FLOAT
import no.njoh.pulseengine.modules.ui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.ui.layout.Panel
import org.joml.Vector3f

class Vector3Input(
    val vector: Vector3f,
    x: Position = Position.auto(),
    y: Position = Position.auto(),
    width: Size = Size.auto(),
    height: Size = Size.auto()
) : UiElement(x, y, width, height) {

    var editable = true
        set(value)
        {
            field = value
            inputFields.forEach { it.editable = value }
        }

    var numberMinVal = Float.NEGATIVE_INFINITY
        set(value)
        {
            field = value
            inputFields.forEach { it.numberMinVal = value }
        }

    var numberMaxVal = Float.POSITIVE_INFINITY
        set(value)
        {
            field = value
            inputFields.forEach { it.numberMaxVal = value }
        }

    var font = Font.DEFAULT
        set(value)
        {
            field = value
            inputFields.forEach { it.font = value }
            axisLabels.forEach { it.font = value }
        }

    var fontSize = ScaledValue.of(18f)
        set(value)
        {
            field = value
            inputFields.forEach { it.fontSize = value }
            axisLabels.forEach { it.fontSize = value }
        }

    var textColor = Color.WHITE
        set(value)
        {
            field = value
            inputFields.forEach { it.textColor = value }
            axisLabels.forEach { it.color = value }
        }

    var inputBgColor = Color(25, 25, 25, 255)
        set(value)
        {
            field = value
            inputFields.forEach { it.bgColor = value }
        }

    var inputBgColorHover = Color(45, 45, 45, 255)
        set(value)
        {
            field = value
            inputFields.forEach { it.bgColorHover = value }
        }

    var inputStrokeColor = Color.BLANK
        set(value)
        {
            field = value
            inputFields.forEach { it.strokeColor = value }
        }

    var inputCornerRadius = ScaledValue.of(4f)
        set(value)
        {
            field = value
            inputFields.forEach { setInputCornerRadius(it, value) }
            axisLabelPanels.forEach { setAxisLabelCornerRadius(it, value) }
        }

    val xLabel = createAxisLabel("X")
    val yLabel = createAxisLabel("Y")
    val zLabel = createAxisLabel("Z")

    val xLabelPanel = createAxisLabelPanel(xLabel, AxisColors.X)
    val yLabelPanel = createAxisLabelPanel(yLabel, AxisColors.Y)
    val zLabelPanel = createAxisLabelPanel(zLabel, AxisColors.Z)

    val xInput = createInput(vector.x)
    val yInput = createInput(vector.y)
    val zInput = createInput(vector.z)

    val body = HorizontalPanel()

    private var xLast = vector.x
    private var yLast = vector.y
    private var zLast = vector.z
    private var onValueChanged: (lastValue: Vector3f, newValue: Vector3f) -> Unit = { _, _ -> }

    private val inputFields get() = listOf(xInput, yInput, zInput)
    private val axisLabels get() = listOf(xLabel, yLabel, zLabel)
    private val axisLabelPanels get() = listOf(xLabelPanel, yLabelPanel, zLabelPanel)

    init
    {
        focusable = false

        xInput.padding.right = ScaledValue.of(3f)
        yInput.padding.right = ScaledValue.of(3f)

        body.addChildren(xLabelPanel, xInput, yLabelPanel, yInput, zLabelPanel, zInput)
        addChildren(body)

        xInput.setOnValidTextChanged { setComponent(0, it.text.toFloat()) }
        yInput.setOnValidTextChanged { setComponent(1, it.text.toFloat()) }
        zInput.setOnValidTextChanged { setComponent(2, it.text.toFloat()) }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (vector.x != xLast || vector.y != yLast || vector.z != zLast)
        {
            syncInputsFromVector()
            updateLastVector()
        }
    }

    override fun onRender(engine: PulseEngine, surface: Surface) { }

    fun setOnValueChanged(callback: (lastValue: Vector3f, newValue: Vector3f) -> Unit)
    {
        onValueChanged = callback
    }

    fun setVectorQuiet(x: Float, y: Float, z: Float)
    {
        vector.set(x, y, z)
        syncInputsFromVector()
        updateLastVector()
    }

    private fun setComponent(componentIndex: Int, value: Float)
    {
        val lastValue = Vector3f(xLast, yLast, zLast)

        when (componentIndex)
        {
            0 -> vector.x = value
            1 -> vector.y = value
            2 -> vector.z = value
        }

        if (vector.x == xLast && vector.y == yLast && vector.z == zLast)
            return

        updateLastVector()
        onValueChanged(lastValue, Vector3f(vector))
    }

    private fun syncInputsFromVector()
    {
        xInput.setTextQuiet(vector.x.toString())
        yInput.setTextQuiet(vector.y.toString())
        zInput.setTextQuiet(vector.z.toString())
    }

    private fun updateLastVector()
    {
        xLast = vector.x
        yLast = vector.y
        zLast = vector.z
    }

    private fun createInput(value: Float) =
        InputField(value.toString(), width = Size.relative(1f)).also() 
        {
            it.contentType = FLOAT
            it.editable = editable
            it.numberMinVal = numberMinVal
            it.numberMaxVal = numberMaxVal
            it.font = font
            it.fontSize = fontSize
            it.textColor = textColor
            it.bgColor = inputBgColor
            it.bgColorHover = inputBgColorHover
            it.strokeColor = inputStrokeColor
            it.leftTextPadding = ScaledValue.of(5f)
            it.numberStepperWidth = ScaledValue.of(18f)
            setInputCornerRadius(it, inputCornerRadius)
        }

    private fun createAxisLabel(text: String) =
        Label(text).also()
        {
            it.font = font
            it.fontSize = fontSize
            it.color = textColor
            it.horizontalAlignment = 0.5f
            it.verticalAlignment = 0.5f
        }

    private fun createAxisLabelPanel(label: Label, bgColor: Color) =
        Panel(width = Size.absolute(14f)).also() 
        {
            it.focusable = false
            it.color = bgColor
            setAxisLabelCornerRadius(it, inputCornerRadius)
            it.addChildren(label)
        }

    private fun setAxisLabelCornerRadius(panel: Panel, radius: ScaledValue)
    {
        panel.cornerRadiusTopLeft = radius
        panel.cornerRadiusBottomLeft = radius
        panel.cornerRadiusTopRight = ScaledValue.of(0f)
        panel.cornerRadiusBottomRight = ScaledValue.of(0f)
    }

    private fun setInputCornerRadius(input: InputField, radius: ScaledValue)
    {
        input.cornerRadiusTopLeft = ScaledValue.of(0f)
        input.cornerRadiusBottomLeft = ScaledValue.of(0f)
        input.cornerRadiusTopRight = radius
        input.cornerRadiusBottomRight = radius
    }
}