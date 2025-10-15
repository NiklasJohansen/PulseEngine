package no.njoh.pulseengine.modules.metrics

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.console.CommandResult
import no.njoh.pulseengine.core.graphics.GraphicsInternal
import no.njoh.pulseengine.core.graphics.api.Multisampling.*
import no.njoh.pulseengine.core.graphics.postprocessing.effects.FrostedGlassEffect
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Color.Companion.WHITE
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.append
import no.njoh.pulseengine.core.service.Service
import no.njoh.pulseengine.modules.ui.Position
import no.njoh.pulseengine.modules.ui.ScaledValue
import no.njoh.pulseengine.modules.ui.Size
import no.njoh.pulseengine.modules.ui.UiElement
import no.njoh.pulseengine.modules.ui.elements.Label
import no.njoh.pulseengine.modules.ui.elements.Label.TextSizeStrategy.CROP_TEXT
import no.njoh.pulseengine.modules.ui.elements.Label.TextSizeStrategy.NONE
import no.njoh.pulseengine.modules.ui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.ui.layout.RowPanel
import no.njoh.pulseengine.modules.ui.layout.VerticalPanel
import no.njoh.pulseengine.modules.ui.layout.WindowPanel
import no.njoh.pulseengine.modules.editor.UiElementFactory
import java.util.Random
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow

/**
 * Tool for viewing all GPU timings measured by the [GpuProfiler].
 */
class GpuMonitor : Service()
{
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
        val window = window ?: return
        val bgSurface = engine.gfx.getSurface(BACKGROUND_SURFACE) ?: return
        val fgSurface = engine.gfx.getSurface(FOREGROUND_SURFACE) ?: return

        // Frosted glass background
        FrostedGlassEffect.drawToTargetSurface(
            engine = engine,
            target = bgSurface,
            x = window.x.value,
            y = window.y.value,
            width = window.width.value,
            height = window.height.value
        )

        // Foreground UI
        window.render(engine, fgSurface)
    }

    private fun init(engine: PulseEngine)
    {
        if (isRunning) stop() else start()

        engine.config.gpuProfiling = isRunning

        if (!isRunning)
        {
            measurements.clear()
            return
        }

        if (engine.gfx.getSurface(BACKGROUND_SURFACE) == null)
        {
            val zOrder = engine.gfx.getAllSurfaces().minOf { it.config.zOrder } - 1
            engine.gfx.createSurface(BACKGROUND_SURFACE, zOrder = zOrder)
        }

        if (engine.gfx.getSurface(FOREGROUND_SURFACE) == null)
        {
            val zOrder = engine.gfx.getAllSurfaces().minOf { it.config.zOrder } - 2
            engine.gfx.createSurface(FOREGROUND_SURFACE, zOrder = zOrder, multisampling = MSAA16)
        }

        window = window ?: createWindow(engine)
    }

    private fun createWindow(engine: PulseEngine) = uiFactory.createWindowUI(
        title = "GPU Monitor - ${(engine.gfx as? GraphicsInternal)?.gpuName}",
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
        init { padding.setAll(10f); padding.bottom = ScaledValue.of(0f) }

        override fun onUpdate(engine: PulseEngine) { }

        override fun onRender(engine: PulseEngine, surface: Surface)
        {
            var x = x.value
            val xm = engine.input.xMouse
            val ym = engine.input.yMouse
            val mouseOver = (xm > x && xm < x + width.value && ym > y.value && ym < y.value + height.value)
            var isHighlighted = !mouseOver

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
                        width = max(2f, w - 2f),
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
            this.fontSize = ScaledValue.of(17f)
            this.padding.left = ScaledValue.of(5f)
            this.color = color
        }

        rows += HorizontalPanel(height = Size.absolute(23f)).apply()
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
            rows += HorizontalPanel(height = Size.absolute(23f)).apply()
            {
                addChildren(
                    createLabel(it.labelText, color = it.highlightColor, cropText = true),
                    createLabel(it.avgText,   color = it.highlightColor, width = 0.15f),
                    createLabel(it.minText,   color = it.highlightColor, width = 0.15f),
                    createLabel(it.maxText,   color = it.highlightColor, width = 0.15f),
                    createLabel(it.fracText,  color = it.fracColor,      width = 0.10f),
                )

                color = if (rows.size % 2 == 0) uiFactory.style.getColor("ROW") else Color.BLANK
            }
        }

        clearChildren()
        addChildren(rows)
    }

    private fun updateMeasurements()
    {
        var i = 0
        val startSize = measurements.size
        GpuProfiler.getMeasurements().forEach()
        {
            if (i > measurements.lastIndex)
                measurements += Measurement()

            val measurement = measurements[i]
            measurement.setTime(it.timeNanoSec / 1_000f)
            measurement.setLabel(it.label, it.depth)
            measurement.calculateFraction(totalAvg = measurements[0].avg)
            i++
        }

        while (measurements.size > i)
            measurements.removeLast()

        if (measurements.size != startSize)
        {
            var i = 0
            var currentLabel: CharSequence = ""
            GpuProfiler.getMeasurements().forEach()
            {
                if (it.depth <= 1)
                    currentLabel = it.label.toString()
                val measurement = measurements[i++]
                measurement.color.setFrom(nextColor(currentLabel))
                measurement.resetHistory()
            }
            timeRows.createEntries()
        }
    }

    companion object
    {
        private const val BACKGROUND_SURFACE = "gpu_monitor_bg"
        private const val FOREGROUND_SURFACE = "gpu_monitor_fg"
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

    private class Measurement(historyLength: Int = 200)
    {
        val color = Color()
        var depth = 0
        var avg   = 0f
        var frac  = 0f // Fraction of total
        var min   = Float.MAX_VALUE
        var max   = Float.MIN_VALUE

        val labelText = StringBuilder()
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

        fun setTime(newValue: Float)
        {
            val lastValue = history[head]
            history[head] = newValue
            head = (head + 1) % history.size
            total = total - lastValue + newValue
            count = min(count + 1, history.size)
            avg = total / count
            min = min(min, newValue)
            max = max(max, newValue)

            avgText.clear().append(avg, decimals = 1).append(" µs")
            minText.clear().append(min, decimals = 1).append(" µs")
            maxText.clear().append(max, decimals = 1).append(" µs")
        }

        fun calculateFraction(totalAvg: Float)
        {
            frac = avg / totalAvg
            fracText.clear().append(frac * 100, decimals = 1).append(" %")

            val f = if (frac == 1f) 1f else (1f - frac).pow(2f)
            fracColor.setFromRgba(1f, f, f)
        }

        fun setLabel(label: CharSequence, depth: Int)
        {
            this.depth = depth
            labelText.clear()
            repeat(depth) { labelText.append("   ") }
            labelText.append(label)
        }

        fun setHighlighted(isHighlighted: Boolean)
        {
            if (isHighlighted)
                highlightColor.setFrom(color)
            else
                highlightColor.setFromRgba(0.3f, 0.3f, 0.3f, 1f)
        }

        fun resetHistory()
        {
            total = 0f
            avg = 0f
            min = Float.MAX_VALUE
            max = Float.MIN_VALUE
            history.fill(0f)
            count = 0
        }
    }
}