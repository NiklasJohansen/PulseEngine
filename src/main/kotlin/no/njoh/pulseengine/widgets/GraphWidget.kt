package no.njoh.pulseengine.widgets

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.FocusArea
import no.njoh.pulseengine.data.assets.Font
import no.njoh.pulseengine.data.Mouse
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.console.CommandResult
import no.njoh.pulseengine.modules.graphics.Surface2D
import java.util.*
import kotlin.math.max
import kotlin.math.min

class GraphWidget : Widget
{
    private var open =  false
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
        engine.gfx.createSurface2D("engineApp", 100)
        engine.asset.loadFont("/pulseengine/assets/clacon.ttf", "graph_font", floatArrayOf(TICK_MARK_FONT_SIZE, HEADER_FONT_SIZE, VALUE_FONT_SIZE))
        engine.console.registerCommand("showGraphs") {
            open = !open
            CommandResult("", showCommand = false)
        }

        graphs.addAll(listOf(
            Graph("FRAMES PER SECOND", "") { engine.data.currentFps.toFloat() },
            Graph("RENDER TIME", "MS") { engine.data.renderTimeMs },
            Graph("UPDATE TIME", "MS") { engine.data.updateTimeMS },
            Graph("FIXED UPDATE TIME", "MS") { engine.data.fixedUpdateTimeMS },
            Graph("USED MEMORY", "MB") { engine.data.usedMemory.toFloat() },
            Graph("TOTAL MEMORY", "MB") { engine.data.totalMemory.toFloat() }
        ))
    }

    override fun onUpdate(engine: PulseEngine)
    {
        if (!open) return

        for (source in engine.data.dataSources.values)
        {
            val graph = graphs.find { it.name == source.name }
            if (graph == null)
            {
                graphs.add(Graph(source.name, source.unit, source.source))
                break
            }
        }

        engine.input.requestFocus(area)

        val insideArea = area.isInside(engine.input.xMouse, engine.input.yMouse)

        if (engine.input.isPressed(Mouse.RIGHT))
        {
            if (insideArea)
                adjustingSize = true
        }
        else adjustingSize = false

        if (engine.input.isPressed(Mouse.LEFT))
        {
            if (insideArea)
                grabbed = true
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
            graphs.forEach { it.update() }
            lastTime = System.currentTimeMillis()
        }
    }

    override fun onRender(engine: PulseEngine)
    {
        if (!open) return

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

        val surface = engine.gfx.getSurface2D("engineApp")
        val font = engine.asset.get<Font>("graph_font")
        
        for (graph in graphs)
        {
            graph.render(surface, font, x, y, w, h)
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

    class Graph(
        val name: String,
        private val unit: String,
        private val source: () -> Float
    ) : Iterable<Float> {
        private var data: FloatArray = FloatArray(WINDOWS_LENGTH * 10)
        private var taleCursor: Int = 0
        private var headCursor: Int = 0
        private var latestValue: Float = 0f
        private val iterator = GraphDataIterator(data, taleCursor, headCursor)

        fun size(): Int =
            if (taleCursor > headCursor)
                data.size - taleCursor + headCursor
            else
                headCursor - taleCursor

        fun update()
        {
            val value = source.invoke()
            latestValue = value
            data[headCursor] = value
            headCursor = (headCursor + 1) % data.size

            if (size() >= WINDOWS_LENGTH)
                taleCursor = (taleCursor + 1) % data.size
        }

        fun render(surface: Surface2D, font: Font, xPos: Float, yPos: Float, width: Float, height: Float)
        {
            val headerText = name + if (unit.isNotEmpty()) " ($unit)" else ""

            surface.setDrawColor(0.1f, 0.1f, 0.1f, 0.9f)
            surface.drawTexture(Texture.BLANK, xPos, yPos, width, height)
            surface.setDrawColor(1f,1f,1f,0.95f)
            surface.drawText(headerText, xPos + PADDING, yPos + 22f, font = font, fontSize = HEADER_FONT_SIZE, yOrigin = 0.5f)

            val min = this.min() ?: 0f
            val max = this.max() ?: 0f
            val valueRange = (max - min)
            val nTicks = 4
            val tickLength = 8
            val maxTickText = if (max < nTicks) "%.1f".format(Locale.US, max) else max.toInt().toString()
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
                val tickValue = min + (valueRange / 2f) - (valueRange / 2f) * fraction
                val tickValueText = if (max < nTicks) "%.1f".format(Locale.US, tickValue) else tickValue.toInt().toString()

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
                surface.setDrawColor(1f, 1f, 1f, 0.95f)
                surface.drawText(tickValueText, xTick + tickLength*1.5f, yTick, yOrigin = 0.5f, font = font, fontSize = TICK_MARK_FONT_SIZE)
            }

            surface.setDrawColor(1f,1f,1f,0.95f)

            var xPlotLast = 0f
            var yPlotLast = 0f
            var i = 0
            for (value in this)
            {
                val fraction = (value - min) / (max - min)
                val xPlot = x + w - (this.size() - i) * sampleWidth
                val yPlot = y + (h / 2f) + (1f - fraction * 2f) * (h / 2f)
                if (i > 0)
                    surface.drawLine(xPlot, yPlot, xPlotLast, yPlotLast)

                xPlotLast = xPlot
                yPlotLast = yPlot
                i++
            }

            val text = if (latestValue < 5) "%.2f".format(Locale.US, latestValue) else latestValue.toInt().toString()
            surface.drawText(text, x + 5, y + 22f, font = font, fontSize = VALUE_FONT_SIZE, yOrigin = 0.5f)
        }

        override fun iterator(): Iterator<Float> =
            iterator.reset(taleCursor, headCursor)

        class GraphDataIterator(
            private val data: FloatArray,
            private var taleCursor: Int = 0,
            private var headCursor: Int = 0
        ): Iterator<Float> {
            override fun hasNext(): Boolean = taleCursor != headCursor
            override fun next(): Float
            {
                val value = data[taleCursor]
                taleCursor = (taleCursor + 1) % data.size
                return value
            }
            fun reset(taleCursor: Int, headCursor: Int): GraphDataIterator
            {
                this.taleCursor = taleCursor
                this.headCursor = headCursor
                return this
            }
        }
    }

    companion object
    {
        const val PADDING = 15f
        const val TOP_PADDING = 40f
        const val TICK_RATE = 100       // Update every 10 ms
        const val WINDOWS_LENGTH = 200  // 200 samples inside window (200 * 10ms = 2000ms)
        const val TICK_MARK_FONT_SIZE = 15f
        const val HEADER_FONT_SIZE = 24f
        const val VALUE_FONT_SIZE = 36f
    }
}