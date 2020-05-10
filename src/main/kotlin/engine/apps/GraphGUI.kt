package engine.apps

import engine.GameEngine
import engine.data.Font
import engine.modules.console.CommandResult
import java.util.*

class GraphGUI : EngineApp
{
    private var open =  false
    private var xPos = 10f
    private var yPos = 10f
    private var width = 400f
    private var height = 150f
    private var lastTime = 0L

    private val fpsGraph = Graph("FRAMES PER SECOND")
    private val renderTimeGraph = Graph("RENDER TIME (MS)")
    private val updateTimeGraph = Graph("UPDATE TIME (MS)")
    private val fixUpdateTimeGraph = Graph("FIXED UPDATE TIME (MS)")

    override fun init(engine: GameEngine)
    {
        engine.asset.loadFont("/clacon.ttf", "graph_font", floatArrayOf(TICK_MARK_FONT_SIZE, HEADER_FONT_SIZE, VALUE_FONT_SIZE))
        engine.console.registerCommand("showGraphs") {
            open = !open
            CommandResult("", showCommand = false)
        }
    }

    override fun update(engine: GameEngine)
    {
        if(!open) return

        if(System.currentTimeMillis() - lastTime > 1000 / TICK_RATE)
        {
            updateGraph(engine)
            lastTime = System.currentTimeMillis()
        }
    }

    private fun updateGraph(engine: GameEngine)
    {
        fpsGraph.update(engine.data.currentFps.toFloat())
        renderTimeGraph.update(engine.data.renderTimeMs)
        updateTimeGraph.update(engine.data.updateTimeMS)
        fixUpdateTimeGraph.update(engine.data.fixedUpdateTimeMS)
    }

    override fun render(engine: GameEngine)
    {
        if(!open) return

        fpsGraph.render(engine, xPos, yPos, width, height)
        renderTimeGraph.render(engine, xPos, yPos + (height + 10), width, height)
        updateTimeGraph.render(engine, xPos + width + 10, yPos, width, height)
        fixUpdateTimeGraph.render(engine, xPos + width + 10, yPos + (height + 10) * 1, width, height)
    }

    override fun cleanup(engine: GameEngine)
    {

    }

    class Graph(val name: String) : Iterable<Float>
    {
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

        fun update(value: Float)
        {
            latestValue = value
            data[headCursor] = value
            headCursor = (headCursor + 1) % data.size

            if(size() >= WINDOWS_LENGTH)
                taleCursor = (taleCursor + 1) % data.size
        }

        fun render(engine: GameEngine, xPos: Float, yPos: Float, width: Float, height: Float)
        {
            val font = engine.asset.get<Font>("graph_font")

            engine.gfx.camera.disable()
            engine.gfx.setColor(0.1f, 0.1f, 0.1f, 0.9f)
            engine.gfx.drawQuad(xPos, yPos, width, height)
            engine.gfx.setColor(1f,1f,1f,0.95f)
            engine.gfx.drawText(name, xPos + PADDING, yPos + 22f, font = font, fontSize = HEADER_FONT_SIZE, yOrigin = 0.5f)

            val x = xPos + PADDING
            val y = yPos + TOP_PADDING
            val w = width - PADDING - RIGHT_PADDING
            val h = height - PADDING - TOP_PADDING
            val sampleWidth = w / WINDOWS_LENGTH

            engine.gfx.setColor(0.5f, 0.5f, 0.5f, 1f)
            engine.gfx.drawLine(x, y + h / 2f, x + w, y + h / 2f)
            engine.gfx.drawLine(x + w, y, x + w, y + h)
            engine.gfx.drawLine(x, y, x + w, y)
            engine.gfx.drawLine(x, y, x, y + h)
            engine.gfx.drawLine(x, y + h, x + w, y + h)

            val (min, max) = findMinMax()
            val valueRange = (max - min)
            val nTicks = 4
            val tickLength = 8

            for (i in 0 .. nTicks)
            {
                val fraction = (i.toFloat() / nTicks) * 2f - 1f
                val xTick = x + w - tickLength / 2
                val yTick = y + (h / 2) + (h / 2) * fraction
                val tickValue = min + (valueRange / 2f) - (valueRange / 2f) * fraction
                val tickValueText = if(valueRange < nTicks) "%.1f".format(Locale.US, tickValue) else tickValue.toInt().toString()

                // Guide line
                if(i % 2 != 0)
                {
                    engine.gfx.setColor(1f, 1f, 1f, 0.01f)
                    engine.gfx.setColor(0.3f, 0.3f, 0.3f, 1f)
                    engine.gfx.drawLine(x, yTick, xTick, yTick)
                }

                // Tick mark
                engine.gfx.setColor(1f, 1f, 1f, 1f)
                engine.gfx.drawLine(xTick, yTick, xTick + tickLength, yTick)

                // Value text
                engine.gfx.setColor(1f, 1f, 1f, 0.95f)
                engine.gfx.drawText(tickValueText, xTick + tickLength*1.5f, yTick, yOrigin = 0.5f, font = font, fontSize = TICK_MARK_FONT_SIZE)
            }

            engine.gfx.setColor(1f,1f,1f,0.95f)

            var xPlotLast = 0f
            var yPlotLast = 0f
            for((i, value) in this.withIndex())
            {
                val fraction = (value - min) / (max - min)
                val xPlot = x + w - (this.size() - i) * sampleWidth
                val yPlot = y + (h / 2f) + (1f - fraction * 2f) * (h / 2f)
                if( i > 0)
                    engine.gfx.drawLine(xPlot, yPlot, xPlotLast, yPlotLast)

                xPlotLast = xPlot
                yPlotLast = yPlot
            }

            val text = if(latestValue < 5) "%.2f".format(Locale.US, latestValue) else latestValue.toInt().toString()
            engine.gfx.drawText(text, x + 5, y + 22f, font = font, fontSize = VALUE_FONT_SIZE, yOrigin = 0.5f)
        }

        private fun findMinMax(): Pair<Float, Float> =
            Pair(this.min() ?: 0f, this.max() ?: 0f)

        override fun iterator(): Iterator<Float>
        {
            return iterator.reset(taleCursor, headCursor)
        }

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

    companion object {
        const val PADDING = 15f
        const val RIGHT_PADDING = 40f
        const val TOP_PADDING = 40f
        const val TICK_RATE = 100       // Update every 10 ms
        const val WINDOWS_LENGTH = 200  // 200 samples inside window (200 * 10ms = 2000ms)
        const val TICK_MARK_FONT_SIZE = 15f
        const val HEADER_FONT_SIZE = 24f
        const val VALUE_FONT_SIZE = 36f
    }
}