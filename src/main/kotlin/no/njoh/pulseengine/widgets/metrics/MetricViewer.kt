package no.njoh.pulseengine.widgets.metrics

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.input.MouseButton
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.console.CommandResult
import no.njoh.pulseengine.core.data.Metric
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.utils.Extensions.append
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.isNotIn
import no.njoh.pulseengine.core.shared.utils.Extensions.noneMatches
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import no.njoh.pulseengine.core.widget.Widget
import kotlin.math.max
import kotlin.math.min

/**
 * Widget to view all registered [Metric]s in the Data module.
 */
class MetricViewer : Widget
{
    override var isRunning = false

    private var xPos = 10f
    private var yPos = 10f

    private var graphWidth = 400f
    private var graphHeight = 150f
    private var graphPadding = 10f

    private var lastTime = 0L
    private var grabbed = false
    private var adjustingSize = false
    private val area = FocusArea(0f, 0f, 1f, 1f)

    private var maxWidth = 820f
    private var minWidth = 285f
    private val graphs =  mutableListOf<Graph>()

    override fun onCreate(engine: PulseEngine)
    {
        engine.gfx.createSurface("metric_viewer", zOrder = -101)
        engine.asset.loadFont("/pulseengine/assets/clacon.ttf", "graph_font")
        engine.console.registerCommand("showMetricViewer")
        {
            isRunning = !isRunning
            CommandResult("", showCommand = false)
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        graphs.removeWhen { it.metric isNotIn engine.data.metrics }

        engine.data.metrics.forEachFast { metric ->
            if (graphs.noneMatches { it.metric === metric })
                graphs.add(Graph(metric))
        }

        engine.input.requestFocus(area)
        val insideArea = area.isInside(engine.input.xMouse, engine.input.yMouse)

        if (engine.input.isPressed(MouseButton.RIGHT))
        {
            if (insideArea) adjustingSize = true
        }
        else adjustingSize = false

        if (engine.input.isPressed(MouseButton.LEFT))
        {
            if (insideArea) grabbed = true
        }
        else grabbed = false

        if (grabbed)
        {
            xPos = max(0f, min(engine.window.width.toFloat()-area.width, xPos + engine.input.xdMouse))
            yPos = max(0f, min(engine.window.height.toFloat()-area.height, yPos + engine.input.ydMouse))
        }
        else if (adjustingSize)
        {
            maxWidth = max(minWidth, maxWidth + engine.input.xdMouse * 5)
        }
        else engine.input.releaseFocus(area)

        if (System.currentTimeMillis() - lastTime > 1000 / TICK_RATE)
        {
            graphs.forEachFast { it.update() }
            lastTime = System.currentTimeMillis()
        }
    }

    override fun onRender(engine: PulseEngine)
    {
        var x = xPos
        var y = yPos
        var w = graphWidth
        var h = graphHeight
        var xMax = xPos
        var yMax = yPos

        if (maxWidth < graphWidth)
        {
            val scale = (maxWidth / graphWidth)
            w *= scale
            h *= scale
        }

        val surface = engine.gfx.getSurfaceOrDefault("metric_viewer")
        val font = engine.asset.getOrNull("graph_font") ?: Font.DEFAULT

        graphs.forEachFast()
        {
            it.render(surface, font, x, y, w, h)
            xMax = max(xMax, x + w + graphPadding)
            yMax = max(yMax, y + h + graphPadding)
            x += w + graphPadding
            if (x + w - graphPadding >= xPos + maxWidth)
            {
                x = xPos
                y += h + graphPadding
            }
        }

        area.update(xPos, yPos, xMax - graphPadding, yMax - graphPadding)

        if (adjustingSize)
        {
            surface.setDrawColor(1f, 0f, 0f, 0.9f)
            surface.drawLine(xPos, yPos, xPos + maxWidth, yPos)
            surface.drawLine(xPos, yPos + area.height, xPos + maxWidth, yPos + area.height)
            surface.drawLine(xPos, yPos, xPos, yPos + area.height)
            surface.drawLine(xPos + maxWidth, yPos, xPos + maxWidth, yPos + area.height)
        }
    }

    override fun onDestroy(engine: PulseEngine) {  }

    class Graph(val metric: Metric)
    {
        private var data = FloatArray(WINDOWS_LENGTH * 2)
        private var taleCursor = 0
        private var headCursor = 0
        private var latestValue = 0f
        private var averageValue = 0f

        fun size(): Int = if (taleCursor > headCursor) data.size - taleCursor + headCursor else headCursor - taleCursor

        fun update()
        {
            metric.onSample(metric)

            latestValue = metric.latestValue
            data[headCursor] = latestValue
            headCursor = (headCursor + 1) % data.size

            if (size() >= WINDOWS_LENGTH)
                taleCursor = (taleCursor + 1) % data.size

            var avg = 0f
            forEachValue { avg += it }
            averageValue = avg / max(size(), 1)
        }

        fun render(surface: Surface, font: Font, xPos: Float, yPos: Float, width: Float, height: Float)
        {
            val headerText = newText(metric.name)
            surface.setDrawColor(0.1f, 0.1f, 0.1f, 0.9f)
            surface.drawTexture(Texture.BLANK, xPos, yPos, width, height, cornerRadius = 4f)
            surface.setDrawColor(1f,1f,1f,0.95f)
            surface.drawText(headerText, xPos + PADDING, yPos + 22f, font = font, fontSize = HEADER_FONT_SIZE, yOrigin = 0.5f)

            var minVal = if (size() > 0) Float.MAX_VALUE else 0f
            var maxVal = if (size() > 0) Float.MIN_VALUE else 0f
            forEachValue()
            {
                if (it < minVal) minVal = it
                if (it > maxVal) maxVal = it
            }
            val valueRange = (maxVal - minVal)
            val nTicks = 4
            val tickLength = 8
            val maxTickText = newText().append(maxVal, decimals = if (maxVal < nTicks) 1 else 0)
            val tickTextSize = (2 + maxTickText.length) * (TICK_MARK_FONT_SIZE / 2f)
            val x = xPos + PADDING
            val y = yPos + TOP_PADDING
            val w = width - PADDING - tickTextSize
            val h = height - PADDING - TOP_PADDING
            val sampleWidth = w / WINDOWS_LENGTH

            surface.setDrawColor(0.5f, 0.5f, 0.5f, 1f)
            surface.drawLine(x, y + h / 2f, x + w, y + h / 2f)
            surface.drawLine(x + w, y, x + w, y + h)
            surface.drawLine(x, y, x + w, y)
            surface.drawLine(x, y, x, y + h)
            surface.drawLine(x, y + h, x + w, y + h)

            for (i in 0 .. nTicks)
            {
                val fraction = (i.toFloat() / nTicks) * 2f - 1f
                val xTick = x + w - tickLength / 2
                val yTick = y + (h / 2) + (h / 2) * fraction
                val tickValue = minVal + (valueRange / 2f) - (valueRange / 2f) * fraction
                val tickValueText = newText().append(tickValue, decimals = if (maxVal < nTicks) 1 else 0)

                // Guide line
                if (i % 2 != 0)
                {
                    surface.setDrawColor(1f, 1f, 1f, 0.01f)
                    surface.setDrawColor(0.3f, 0.3f, 0.3f, 1f)
                    surface.drawLine(x, yTick, xTick, yTick)
                }

                // Tick mark
                surface.setDrawColor(1f, 1f, 1f, 1f)
                surface.drawLine(xTick, yTick, xTick + tickLength, yTick)

                // Value text
                if (maxVal != minVal || i == nTicks / 2)
                {
                    surface.setDrawColor(1f, 1f, 1f, 0.95f)
                    surface.drawText(tickValueText, xTick + tickLength * 1.5f, yTick, yOrigin = 0.5f, font = font, fontSize = TICK_MARK_FONT_SIZE)
                }
            }

            surface.setDrawColor(1f,1f,1f,0.95f)

            var i = 0
            val size = size()
            var xPlotLast = 0f
            var yPlotLast = 0f
            val range = (maxVal - minVal)
            forEachValue()
            {
                val fraction = if (range == 0f) 0.5f else (it - minVal) / range
                val xPlot = x + w - (size - i) * sampleWidth
                val yPlot = y + (h / 2f) + (1f - fraction * 2f) * (h / 2f)
                if (i > 0)
                    surface.drawLine(xPlot, yPlot, xPlotLast, yPlotLast)
                xPlotLast = xPlot
                yPlotLast = yPlot
                i++
            }

            val lastValueText = newText('(').append(latestValue, decimals = if (latestValue < 5) 2 else 0).append(')')
            surface.drawText(lastValueText, x + 5, y + 22f, font = font, fontSize = VALUE_FONT_SIZE, yOrigin = 0.5f)

            val averageValueText = newText("(avg ").append(averageValue, decimals = if (averageValue < 5) 2 else 0).append(')')
            surface.drawText(averageValueText, x + 5, y + 50f, font = font, fontSize = AVG_VALUE_FONT_SIZE, yOrigin = 0.5f)
        }

        private inline fun forEachValue(action: (Float) -> Unit)
        {
            var i = taleCursor
            while (i != headCursor)
            {
                action(data[i])
                i = (i + 1) % data.size
            }
        }
    }

    companion object
    {
        private val sb = StringBuilder(150)
        private fun newText() = sb.clear()
        private fun newText(s: String) = newText().append(s)
        private fun newText(c: Char) = newText().append(c)

        const val PADDING = 15f
        const val TOP_PADDING = 40f
        const val TICK_RATE = 100       // Update every 10 ms
        const val WINDOWS_LENGTH = 200  // 200 samples inside window (200 * 10ms = 2000ms)
        const val TICK_MARK_FONT_SIZE = 15f
        const val HEADER_FONT_SIZE = 24f
        const val VALUE_FONT_SIZE = 36f
        const val AVG_VALUE_FONT_SIZE = 20f
    }
}