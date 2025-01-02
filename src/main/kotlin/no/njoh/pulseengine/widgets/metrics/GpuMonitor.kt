package no.njoh.pulseengine.widgets.metrics

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.console.CommandResult
import no.njoh.pulseengine.core.graphics.api.Multisampling
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Color.Companion.WHITE
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.append
import no.njoh.pulseengine.core.widget.Widget
import no.njoh.pulseengine.modules.gui.Position
import no.njoh.pulseengine.modules.gui.ScaledValue
import no.njoh.pulseengine.modules.gui.Size
import no.njoh.pulseengine.modules.gui.UiElement
import no.njoh.pulseengine.modules.gui.elements.Label
import no.njoh.pulseengine.modules.gui.elements.Label.TextSizeStrategy.CROP_TEXT
import no.njoh.pulseengine.modules.gui.elements.Label.TextSizeStrategy.NONE
import no.njoh.pulseengine.modules.gui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.gui.layout.RowPanel
import no.njoh.pulseengine.modules.gui.layout.VerticalPanel
import no.njoh.pulseengine.modules.gui.layout.WindowPanel
import no.njoh.pulseengine.widgets.editor.UiElementFactory
import java.util.Random
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow

/**
 * Widget for viewing all GPU timings measured by the [GpuProfiler].
 */
class GpuMonitor : Widget
{
    override var isRunning = false

    private var window: WindowPanel? = null
    private var timeRows = RowPanel()
    private var measurements = mutableListOf<Measurement>()
    private var uiFactory = UiElementFactory()

    override fun onCreate(engine: PulseEngine)
    {
        engine.console.registerCommand("showGpuMonitor")
        {
            init(engine)
            CommandResult("", showCommand = false)
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        updateMeasurements()
        window?.update(engine)
    }

    override fun onRender(engine: PulseEngine)
    {
        val surface = engine.gfx.getSurface(SURFACE) ?: return
        window?.render(engine, surface)
    }

    private fun init(engine: PulseEngine)
    {
        isRunning = !isRunning
        engine.config.gpuProfiling = isRunning

        if (!isRunning)
        {
            measurements.clear()
            return
        }

        if (engine.gfx.getSurface(SURFACE) == null)
        {
            engine.gfx.createSurface(
                name = SURFACE,
                zOrder = engine.gfx.getAllSurfaces().minOf { it.config.zOrder } - 1,
                multisampling = Multisampling.MSAA4
            )
        }

        window = window ?: createWindow(engine)
    }

    private fun createWindow(engine: PulseEngine) = uiFactory.createWindowUI(
        title = "GPU Monitor",
        iconName = "MONITOR",
        x = 20f,
        y = 20f,
        width = 700f,
        height = 700f,
        onClosed = { init(engine) }
    ).apply {
        val fractionBar = createFractionBar()
        val scrollableTimeRows = uiFactory.createScrollableSectionUI(timeRows).apply { padding.setAll(5f) }
        val vPanel = VerticalPanel().apply { addChildren(fractionBar, scrollableTimeRows) }
        body.addChildren(vPanel)
    }

    private fun createFractionBar() = object : UiElement(
        x = Position.auto(),
        y = Position.auto(),
        width = Size.auto(),
        height = Size.absolute(25f)
    ) {
        init { padding.setAll(10f); padding.bottom = ScaledValue.of(5f) }

        override fun onUpdate(engine: PulseEngine) { }

        override fun onRender(engine: PulseEngine, surface: Surface)
        {
            val xm = engine.input.xMouse
            val ym = engine.input.yMouse
            val mouseOver = (ym > y.value && ym < y.value + height.value)
            var isHighlighted = !mouseOver
            var x = x.value
            measurements.forEachFast()
            {
                if (it.depth == 1)
                {
                    val w = width.value * it.frac
                    isHighlighted = !mouseOver || (xm >= x && xm < x + w)
                    surface.setDrawColor(it.highlightColor)
                    surface.drawTexture(
                        texture = Texture.BLANK,
                        x = x,
                        y = y.value,
                        width = w - 2f,
                        height = height.value,
                        cornerRadius = 2f
                    )
                    x += w
                }
                it.setHighlighted(isHighlighted)
            }
        }
    }

    private fun RowPanel.createEntries()
    {
        val rows = mutableListOf<UiElement>()

        fun createLabel(text: CharSequence, color: Color, width: Float? = null, cropText: Boolean = false) = Label(
            text = text,
            width = width?.let { Size.relative(it) } ?: Size.auto()
        ).apply {
            this.textResizeStrategy = if (cropText) CROP_TEXT else NONE
            this.fontSize = ScaledValue.of(20f)
            this.padding.left = ScaledValue.of(5f)
            this.color = color
        }

        rows += HorizontalPanel(height = Size.absolute(25f)).apply()
        {
            addChildren(
                createLabel("Measurement", color = WHITE, cropText = true),
                createLabel("Avg",         color = WHITE, width = 0.15f),
                createLabel("Min",         color = WHITE, width = 0.15f),
                createLabel("Max",         color = WHITE, width = 0.15f),
                createLabel("%",           color = WHITE, width = 0.10f),
            )
        }

        measurements.forEachFast()
        {
            rows += HorizontalPanel(height = Size.absolute(25f)).apply()
            {
                val label = (0 until it.depth).joinToString("") { "   " } + it.name

                addChildren(
                    createLabel(label,       color = it.highlightColor, cropText = true),
                    createLabel(it.avgText,  color = it.highlightColor, width = 0.15f),
                    createLabel(it.minText,  color = it.highlightColor, width = 0.15f),
                    createLabel(it.maxText,  color = it.highlightColor, width = 0.15f),
                    createLabel(it.fracText, color = it.fracColor,      width = 0.10f),
                )

                color = if (rows.size % 2 == 0) uiFactory.style.getColor("BUTTON") else Color.BLANK
            }
        }

        clearChildren()
        addChildren(rows)
    }

    private fun updateMeasurements()
    {
        var currentLabel: CharSequence = ""
        var updateUi = false
        var i = 0

        GpuProfiler.getMeasurements().forEachFast()
        {
            if (it.depth <= 1) currentLabel = it.label

            if (i > measurements.lastIndex)
            {
                measurements.add(Measurement(it.label.toString(), Color(nextColor(currentLabel)), it.depth))
                updateUi = true
            }

            val measurement = measurements[i]
            if (!it.label.contentEquals(measurement.name) || measurement.depth != it.depth)
            {
                measurements.clear() // Clear and recreate all measurements when changes are detected
                return
            }

            measurement.update(it.timeNanoSec / 1_000_000f)
            measurement.calculateFraction(totalAvg = measurements[0].avg)
            i++
        }

        if (updateUi) timeRows.createEntries()
    }

    companion object
    {
        private const val SURFACE = "gpu_monitor"
        private val random = Random()
        private val color = Color(1f, 1f, 1f)
        private fun nextColor(label: CharSequence): Color
        {
            random.setSeed(label.toString().hashCode().toLong())
            return color.setFromHsb(
                hue = random.nextFloat(),
                saturation = 0.5f + 0.4f * random.nextFloat(),
                brightness = 0.7f + 0.3f * random.nextFloat(),
            )
        }
    }

    private class Measurement(
        var name: CharSequence,
        var color: Color,
        var depth: Int,
        historyLength: Int = 200
    ) {
        var value = 0f
        var avg   = 0f
        var frac  = 0f // Fraction of total
        var min   = Float.MAX_VALUE
        var max   = Float.MIN_VALUE

        val avgText  = StringBuilder()
        val fracText = StringBuilder()
        val minText  = StringBuilder()
        val maxText  = StringBuilder()
        val fracColor = Color(1f, 1f, 1f)
        val highlightColor = Color(1f, 1f, 1f)

        private var history = FloatArray(historyLength)
        private var head = 0
        private var total = 0f
        private var count = 0

        fun update(newValue: Float)
        {
            val lastValue = history[head]
            history[head] = newValue
            head = (head + 1) % history.size
            total = total - lastValue + newValue
            count = min(count + 1, history.size)
            avg = total / count
            min = min(min, newValue)
            max = max(max, newValue)
            value = newValue

            avgText.clear().append(avg, decimals = 3).append(" ms")
            minText.clear().append(min, decimals = 3).append(" ms")
            maxText.clear().append(max, decimals = 3).append(" ms")
        }

        fun calculateFraction(totalAvg: Float)
        {
            frac = avg / totalAvg
            fracText.clear().append(frac * 100, decimals = 1).append(" %")

            val f = if (frac == 1f) 1f else (1f - frac).pow(2f)
            fracColor.setFromRgba(1f, f, f)
        }

        fun setHighlighted(isHighlighted: Boolean)
        {
            if (isHighlighted)
                highlightColor.setFrom(color)
            else
                highlightColor.setFromRgba(0.3f, 0.3f, 0.3f, 1f)
        }
    }
}