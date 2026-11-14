package no.njoh.pulseengine.modules.metrics

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.console.CommandResult
import no.njoh.pulseengine.core.input.CursorType.ARROW
import no.njoh.pulseengine.core.input.CursorType.HAND
import no.njoh.pulseengine.core.graphics.GraphicsInternal
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.*
import no.njoh.pulseengine.core.graphics.postprocessing.FrostedGlassEffect
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceOutputSpec
import no.njoh.pulseengine.core.graphics.util.GpuProfiler
import no.njoh.pulseengine.core.graphics.util.GpuTimeQueryResult
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Color.Companion.WHITE
import no.njoh.pulseengine.core.shared.primitives.CornerRadius
import no.njoh.pulseengine.core.shared.datastructures.DynamicList
import no.njoh.pulseengine.core.shared.datastructures.StaticList
import no.njoh.pulseengine.core.shared.utils.Extensions.append
import no.njoh.pulseengine.core.service.Service
import no.njoh.pulseengine.core.shared.utils.TextBuilderContext
import no.njoh.pulseengine.modules.ui.Position
import no.njoh.pulseengine.modules.ui.ScaledValue
import no.njoh.pulseengine.modules.ui.Size
import no.njoh.pulseengine.modules.ui.UiElement
import no.njoh.pulseengine.modules.ui.elements.Button
import no.njoh.pulseengine.modules.ui.elements.Label
import no.njoh.pulseengine.modules.ui.elements.Label.TextSizeStrategy.CROP_TEXT
import no.njoh.pulseengine.modules.ui.elements.Label.TextSizeStrategy.NONE
import no.njoh.pulseengine.modules.ui.layout.HorizontalPanel
import no.njoh.pulseengine.modules.ui.layout.Panel
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
    private var window       = null as WindowPanel?
    private var windowTitle  = TextBuilderContext()
    private var timeRows     = RowPanel()
    private var uiFactory    = UiElementFactory()
    private var data         = GpuMonitorData()
    private var measurements = mutableListOf<Measurement>()

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
            engine       = engine,
            target       = bgSurface,
            x            = window.x.value,
            y            = window.y.value,
            width        = window.width.value,
            height       = window.height.value,
            cornerRadius = CornerRadius(8f)
        )

        // Update title with current FPS
        val gpuName = (engine.gfx as? GraphicsInternal)?.gpuName ?: ""
        windowTitle.build { "GPU Monitor - " plus gpuName plus " [" plus engine.data.currentFps plus " FPS]" }
  
        // Foreground UI
        window.render(engine, fgSurface)
    }

    private fun init(engine: PulseEngine)
    {
        if (isRunning) stop() else start()

        engine.config.gpuProfiling = isRunning

        if (!isRunning)
        {
            data.clear()
            measurements.clear()
            timeRows.clearChildren()
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
            engine.gfx.createSurface(FOREGROUND_SURFACE, zOrder = zOrder, output = SurfaceOutputSpec(multisampling = MSAA16))
        }

        window = window ?: createWindow(engine)
    }

    private fun createWindow(engine: PulseEngine) = uiFactory.createWindowUI(
        title    = windowTitle.content,
        iconName = "MONITOR",
        x        = max(20f, engine.window.width * 0.5f - 500f),
        y        = max(20f, engine.window.height * 0.4f - 400f),
        width    = 1000f,
        height   = 800f,
        onClosed = { init(engine) }
    ).apply {
        timeRows.padding.right = ScaledValue.of(5f)
        val scrollableTimeRows = uiFactory.createScrollableSectionUI(timeRows, cornerRadius = CornerRadius(2f) ).apply { padding.setAll(5f) }
        val fractionBar = createFractionBar()
        val vPanel = VerticalPanel().apply { addChildren(fractionBar, scrollableTimeRows) }
        body.addChildren(vPanel)
    }

    private fun createFractionBar() = object : UiElement(
        x      = Position.auto(),
        y      = Position.auto(),
        width  = Size.auto(),
        height = Size.absolute(50f)
    ) {
        private var hoveredMeasurement: Measurement? = null

        init { padding.setAll(10f); padding.bottom = ScaledValue.of(0f) }

        override fun onUpdate(engine: PulseEngine)
        {
            hoveredMeasurement = findHoveredMeasurement(engine.input.xMouse, engine.input.yMouse)
            if (mouseInsideArea)
                engine.input.setCursorType(if (hoveredMeasurement != null) HAND else ARROW)
        }

        override fun onMouseLeave(engine: PulseEngine)
        {
            hoveredMeasurement = null
            engine.input.setCursorType(ARROW)
        }

        override fun onMouseClicked(engine: PulseEngine)
        {
            val measurement = hoveredMeasurement ?: return
            data.focus(measurement)
            updateCollapsedRows()
        }

        private fun findHoveredMeasurement(xMouse: Float, yMouse: Float): Measurement?
        {
            val rowHeight = (height.value - FRACTION_BAR_ROW_GAP * (FRACTION_BAR_LEVELS - 1)) / FRACTION_BAR_LEVELS
            val rowStride = rowHeight + FRACTION_BAR_ROW_GAP
            var i = 0
            while (i < measurements.size)
            {
                val measurement = measurements[i++]
                if (!measurement.fractionBarVisible)
                    continue
                val segmentY = y.value + (measurement.depth - 1) * rowStride
                if (xMouse >= measurement.fractionBarX && xMouse < measurement.fractionBarX + measurement.fractionBarWidth &&
                    yMouse >= segmentY && yMouse < segmentY + rowHeight)
                    return measurement
            }
            return null
        }

        override fun onRender(engine: PulseEngine, surface: Surface)
        {
            val xStart     = x.value
            val xEnd       = xStart + width.value
            val rowHeight  = (height.value - FRACTION_BAR_ROW_GAP * (FRACTION_BAR_LEVELS - 1)) / FRACTION_BAR_LEVELS
            val rowStride  = rowHeight + FRACTION_BAR_ROW_GAP
            val xm         = engine.input.xMouse
            val ym         = engine.input.yMouse
            val mouseOver  = (xm > xStart && xm < xEnd && ym > y.value && ym < y.value + height.value)
            var xRootChild = xStart
            hoveredMeasurement = null

            var i = 0
            while (i < measurements.size)
            {
                val measurement = measurements[i++]
                measurement.fractionBarVisible = false
                measurement.fractionBarChildX = 0f
            }

            i = 0
            while (i < measurements.size)
            {
                val measurement = measurements[i++]
                val depth = measurement.depth
                if (measurement.frac <= 0f || depth !in 1..FRACTION_BAR_LEVELS)
                    continue

                val parent     = measurement.parent
                var segmentX   = 0f
                var segmentEnd = 0f
                if (depth == 1)
                {
                    segmentX   = xRootChild
                    segmentEnd = xEnd
                }
                else
                {
                    if (parent?.fractionBarVisible != true)
                        continue

                    segmentX = parent.fractionBarChildX
                    segmentEnd = min(xEnd, parent.fractionBarX + parent.fractionBarWidth)
                }

                val segmentWidth = min(width.value * measurement.frac, segmentEnd - segmentX).coerceAtLeast(0f)
                if (segmentWidth <= 0f)
                    continue

                measurement.fractionBarVisible = true
                measurement.fractionBarX       = segmentX
                measurement.fractionBarWidth   = segmentWidth
                measurement.fractionBarChildX  = segmentX

                if (depth == 1)
                    xRootChild += segmentWidth
                else
                    parent!!.fractionBarChildX += segmentWidth

            }

            hoveredMeasurement = if (mouseOver) findHoveredMeasurement(xm, ym) else null
            val hovered = hoveredMeasurement
            
            i = 0
            while (i < measurements.size)
            {
                val measurement = measurements[i++]
                val highlighted = !mouseOver || measurement === hovered
                measurement.setHighlighted(highlighted)
            }

            i = 0
            while (i < measurements.size)
            {
                val measurement = measurements[i++]
                if (!measurement.fractionBarVisible)
                    continue

                surface.setDrawColor(measurement.highlightColor)
                surface.drawTexture(
                    texture      = Texture.BLANK,
                    x            = measurement.fractionBarX,
                    y            = y.value + (measurement.depth - 1) * rowStride,
                    width        = max(1f, measurement.fractionBarWidth - FRACTION_BAR_SEGMENT_GAP),
                    height       = rowHeight,
                    cornerRadius = CornerRadius(2f)
                )
            }
        }
    }

    private fun RowPanel.createEntries()
    {
        val rows = mutableListOf<UiElement>()

        fun createLabel(text: CharSequence, color: Color, width: Float? = null, cropText: Boolean = false) = Label(
            text  = text,
            width = width?.let { Size.relative(it) } ?: Size.auto()
        ).apply {
            this.textResizeStrategy = if (cropText) CROP_TEXT else NONE
            this.fontSize           = ScaledValue.of(17f)
            this.padding.left       = ScaledValue.of(5f)
            this.color              = color
        }

        fun createCollapseControl(measurement: Measurement): UiElement
        {
            val control = if (data.hasChildren(measurement))
            {
                Button(width = Size.absolute(COLLAPSE_BUTTON_WIDTH)).apply()
                {
                    isPressed            = measurement.collapsed
                    toggleButton         = true
                    iconFontName         = uiFactory.style.iconFontName
                    iconSize             = ScaledValue.of(15f)
                    iconCharacter        = uiFactory.style.getIcon("ARROW_DOWN")
                    pressedIconCharacter = uiFactory.style.getIcon("ARROW_RIGHT")
                    xOrigin              = 0.70f
                    yOrigin              = 0.62f
                    color                = uiFactory.style.getColor("LABEL")
                    activeColor          = uiFactory.style.getColor("LABEL")
                    hoverColor           = uiFactory.style.getColor("LABEL_DARK")
                    activeHoverColor     = uiFactory.style.getColor("LABEL_DARK")
                    setOnClicked()
                    {
                        yOrigin = if (it.isPressed) 0.55f else 0.62f
                        measurement.collapsed = it.isPressed
                        if (it.isPressed)
                            data.collapseDescendants(measurement)
                        updateCollapsedRows()
                    }
                }
            }
            else
            {
                Panel(width = Size.absolute(COLLAPSE_BUTTON_WIDTH)).apply { focusable = false }
            }

            control.padding.left = ScaledValue.of(2f + measurement.depth * ROW_INDENT)
            return control
        }

        rows += HorizontalPanel(height = Size.absolute(23f)).apply()
        {
            addChildren(
                createLabel("Measurement",     color = WHITE, cropText = true),
                createLabel("Last 2s (peaks)", color = WHITE, width = 0.23f),
                createLabel("Value",           color = WHITE, width = 0.08f),
                createLabel("Avg",             color = WHITE, width = 0.08f),
                createLabel("Min",             color = WHITE, width = 0.08f),
                createLabel("Max",             color = WHITE, width = 0.08f),
                createLabel("Total %",         color = WHITE, width = 0.08f),
            )
        }

        measurements.clear()
        fun addMeasurementRows(parent: Measurement?)
        {
            data.measurements.forEach()
            {
                if (it.parent !== parent) return@forEach

                measurements += it
                rows += HorizontalPanel(height = Size.absolute(23f)).apply()
                {
                    addChildren(
                        HorizontalPanel().apply()
                        {
                            addChildren(
                                createCollapseControl(it),
                                createLabel(it.labelText, color = it.highlightColor, cropText = true)
                            )
                        },
                        MeasurementGraph(it,                                  width = 0.23f),
                        createLabel(it.latestText, color = it.latestColor,    width = 0.08f),
                        createLabel(it.avgText,    color = it.highlightColor, width = 0.08f),
                        createLabel(it.minText,    color = it.highlightColor, width = 0.08f),
                        createLabel(it.maxText,    color = it.highlightColor, width = 0.08f),
                        createLabel(it.fracText,   color = it.fracColor,      width = 0.08f),
                    )

                    setCornerRadius(ScaledValue.of(1f))
                    color = if (rows.size % 2 == 0) uiFactory.style.getColor("ROW") else Color.BLANK
                }
                addMeasurementRows(it)
            }
        }
        addMeasurementRows(parent = null)

        clearChildren()
        addChildren(rows)
        updateCollapsedRows()
    }

    private fun updateCollapsedRows()
    {
        var visibleIndex = 0
        var i = 0
        while (i < measurements.size && i + 1 < timeRows.children.size)
        {
            val measurement     = measurements[i]
            val row             = timeRows.children[i + 1] as HorizontalPanel
            val measurementCell = row.children[0] as HorizontalPanel
 
            (measurementCell.children[0] as? Button)?.isPressed = measurement.collapsed
            row.hidden = measurement.hasCollapsedAncestor()

            if (!row.hidden)
            {
                row.color = if (visibleIndex % 2 == 0) uiFactory.style.getColor("ROW") else Color.BLANK
                visibleIndex++
            }

            i++
        }
    }

    private fun updateMeasurements()
    {
        if (data.update(GpuProfiler.getMeasurements()))
        {
            data.measurements.forEach()
            {
                if (!it.colorInitialized)
                {
                    it.color.setFrom(nextColor(it))
                    it.colorInitialized = true
                }
            }
            timeRows.createEntries()
        }
    }

    companion object
    {
        private const val BACKGROUND_SURFACE       = "gpu_monitor_bg"
        private const val FOREGROUND_SURFACE       = "gpu_monitor_fg"
        private const val COLLAPSE_BUTTON_WIDTH    = 10f
        private const val ROW_INDENT               = 12f
        private const val FRACTION_BAR_LEVELS      = 5
        private const val FRACTION_BAR_ROW_GAP     = 1f
        private const val FRACTION_BAR_SEGMENT_GAP = 1f

        private val random = Random()
        private val color  = Color(1f, 1f, 1f)
 
        private fun nextColor(measurement: Measurement): Color
        {
            var family = measurement
            while (family.depth > COLOR_FAMILY_DEPTH)
                family = family.parent ?: break

            random.setSeed((family.timerId + 1L).rotateLeft(9))
            val familyHue = random.nextFloat()
            if (measurement === family)
            {
                color.setFromHsb(
                    hue = familyHue,
                    saturation = 0.65f + 0.3f * random.nextFloat(),
                    brightness = 0.92f + 0.08f * random.nextFloat(),
                )
                return ensureReadable(color)
            }

            val parentId    = measurement.parent?.timerId ?: 0L
            val variantSeed = measurement.timerId xor parentId.rotateLeft(17) xor measurement.depth.toLong()
            
            random.setSeed(variantSeed)
            color.setFromHsb(
                hue        = familyHue + (random.nextFloat() - 0.5f) * COLOR_FAMILY_HUE_SPREAD,
                saturation = 0.65f + 0.35f * random.nextFloat(),
                brightness = 0.88f + 0.12f * random.nextFloat(),
            )
  
            return ensureReadable(color)
        }

        private fun ensureReadable(color: Color): Color
        {
            val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
            if (luminance >= MIN_COLOR_LUMINANCE)
                return color

            val whiteMix = (MIN_COLOR_LUMINANCE - luminance) / (1f - luminance)
            color.red   += (1f - color.red)   * whiteMix
            color.green += (1f - color.green) * whiteMix
            color.blue  += (1f - color.blue)  * whiteMix
            return color
        }

        private const val COLOR_FAMILY_DEPTH      = 2
        private const val COLOR_FAMILY_HUE_SPREAD = 0.55f
        private const val MIN_COLOR_LUMINANCE     = 0.32f
    }

}

private class MeasurementGraph(private val measurement: Measurement, width: Float) : Panel(width = Size.relative(width))
{
    init { focusable = false; padding.setAll(4f) }

    override fun onRender(engine: PulseEngine, surface: Surface)
    {
        val count = measurement.graphSampleCount
        if (count < 2 || width.value <= 0f || height.value <= 0f) 
            return

        var low = Float.MAX_VALUE
        var high = 0f
        for (i in 0 until count)
        {
            val value = measurement.graphValue(i)
            low = min(low, value)
            high = max(high, value)
        }

        val range = high - low
        val step = width.value / (measurement.graphCapacity - 1).coerceAtLeast(1)
        var xPrevious = 0f
        var yPrevious = 0f
        
        surface.setDrawColor(measurement.highlightColor)
        
        for (i in 0 until count)
        {
            val fraction = if (range > 0f) (measurement.graphValue(i) - low) / range else 0.5f
            val xPlot    = x.value + width.value - (count - 1 - i) * step
            val yPlot    = y.value + height.value * (1f - fraction)
            
            if (i > 0) surface.drawLine(xPrevious, yPrevious, xPlot, yPlot)

            xPrevious = xPlot
            yPrevious = yPlot
        }
    }
}

private class GpuMonitorData(
    private val historyLength: Int       = 400,
    private val inactiveGraceFrames: Int = 10,
    initialDepthCapacity: Int = 16
) {
    val measurements = DynamicList<Measurement>()

    private var depthStack          = arrayOfNulls<Measurement>(initialDepthCapacity)
    private var maxDepthUsed        = -1
    private var frameId             = 0L
    private var lastGpuFrameNumber  = Long.MIN_VALUE
    private var lastGraphSampleTime = 0L

    fun update(results: StaticList<GpuTimeQueryResult>): Boolean
    {
        val now = System.nanoTime()
        if (lastGraphSampleTime == 0L) 
            lastGraphSampleTime = now
        
        val graphInterval  = 2_000_000_000L / historyLength // 2 seconds divided by history length
        val elapsedBuckets = (now - lastGraphSampleTime) / graphInterval
        if (elapsedBuckets > 0L)
        {
            val bucketsToRecord = min(elapsedBuckets, historyLength.toLong() + 1).toInt()
            repeat(bucketsToRecord) { measurements.forEach { it.recordGraphSample() } }
            lastGraphSampleTime += elapsedBuckets * graphInterval
        }

        if (results.size == 0 || results[0].frameNumber == lastGpuFrameNumber) 
        {
            val totalFrameTime = measurements.firstOrNull { it.depth == 0 }?.frameTimeTotal ?: 0f
            measurements.forEach { it.calculateFraction(totalFrameTime) }
            return false
        }
        
        lastGpuFrameNumber = results[0].frameNumber
        frameId++

        measurements.forEach { it.markNotMeasured(inactiveGraceFrames) }
        for (i in 0..maxDepthUsed)
            depthStack[i] = null
        maxDepthUsed = -1

        var layoutChanged = false
        var frameMeasurement: Measurement? = null

        results.forEach()
        {
            val depth = max(0, it.depth)
            if (depth >= depthStack.size)
            {
                var newSize = depthStack.size.coerceAtLeast(1)
                while (newSize <= depth)
                    newSize *= 2
                depthStack = depthStack.copyOf(newSize)
                layoutChanged = true
            }

            val parent = if (depth == 0) null else depthStack[depth - 1]
            var measurement: Measurement? = null
            var i = 0
            while (i < measurements.size)
            {
                val candidate = measurements[i++]
                if (candidate.parent === parent && candidate.timerId == it.timerId && candidate.lastSeenFrame != frameId)
                {
                    measurement = candidate
                    break
                }
            }

            if (measurement == null)
            {
                measurement = Measurement(it.timerId, depth, parent, historyLength)
                measurements += measurement
                layoutChanged = true
            }

            measurement.lastSeenFrame = frameId
            measurement.markMeasured()
            measurement.setLabel(it.label)
            measurement.setTime(it.timeNanoSec / 1_000f)

            depthStack[depth] = measurement
            maxDepthUsed      = max(maxDepthUsed, depth)

            if (depth == 0 && frameMeasurement == null)
                frameMeasurement = measurement
        }

        val totalFrameTime = frameMeasurement?.frameTimeTotal ?: 0f
        measurements.forEach { it.calculateFraction(totalFrameTime) }

        return layoutChanged
    }

    fun clear()
    {
        measurements.clear()
        for (i in depthStack.indices)
            depthStack[i] = null

        maxDepthUsed        = -1
        frameId             = 0L
        lastGpuFrameNumber  = Long.MIN_VALUE
        lastGraphSampleTime = 0L
    }

    fun hasChildren(parent: Measurement): Boolean
    {
        var i = 0
        while (i < measurements.size)
        {
            if (measurements[i++].parent === parent)
                return true
        }
        return false
    }

    fun collapseDescendants(parent: Measurement)
    {
        var i = 0
        while (i < measurements.size)
        {
            val measurement = measurements[i++]
            if (measurement.isDescendantOf(parent) && hasChildren(measurement))
                measurement.collapsed = true
        }
    }

    fun collapseAll()
    {
        var i = 0
        while (i < measurements.size)
        {
            val measurement = measurements[i++]
            if (hasChildren(measurement))
                measurement.collapsed = true
        }
    }

    fun expandAncestors(measurement: Measurement)
    {
        var ancestor = measurement.parent
        while (ancestor != null)
        {
            ancestor.collapsed = false
            ancestor = ancestor.parent
        }
    }

    fun focus(measurement: Measurement)
    {
        collapseAll()
        expandAncestors(measurement)
        measurement.collapsed = false
    }
}

private class Measurement(
    val timerId: Long,
    val depth: Int,
    val parent: Measurement?,
    historyLength: Int
) {
    val color              =  Color()
    var active             = false
    var measuredThisFrame  = false
    var inactiveFrames     = 0
    var collapsed          = false
    var avg                = 0f
    var frac               = 0f
    var min                = Float.MAX_VALUE
    var max                = Float.MIN_VALUE
    var lastSeenFrame      = Long.MIN_VALUE
    var colorInitialized   = false
    var fractionBarVisible = false
    var fractionBarX       = 0f
    var fractionBarWidth   = 0f
    var fractionBarChildX  = 0f

    val labelText      = StringBuilder(MAX_LABEL_LENGTH)
    val avgText        = StringBuilder(VALUE_TEXT_CAPACITY)
    val latestText     = StringBuilder(VALUE_TEXT_CAPACITY)
    val latestColor    = Color(1f, 1f, 1f, 0f)
    val fracText       = StringBuilder(VALUE_TEXT_CAPACITY)
    val minText        = StringBuilder(VALUE_TEXT_CAPACITY)
    val maxText        = StringBuilder(VALUE_TEXT_CAPACITY)
    val fracColor      = Color(1f, 1f, 1f)
    val highlightColor = Color(1f, 1f, 1f)

    private val intervalTotals = FloatArray(historyLength)
    private val intervalCounts = IntArray(historyLength)
    private val graphHistory   = FloatArray(historyLength)
    private val graphMeasured  = BooleanArray(historyLength)
    private var graphHead      = 0

    var frameTimeTotal   = 0f; private set
    var sampleCount      = 0;  private set
    var graphSampleCount = 0;  private set
    val graphCapacity get() = graphHistory.size

    private var latestValueTime = 0L
    private var graphIntervalPeak = 0f
    private var graphIntervalMeasured = false
    private var graphIntervalTotal = 0f
    private var graphIntervalCount = 0

    fun markNotMeasured(graceFrames: Int)
    {
        measuredThisFrame = false
        frac = 0f
        if (inactiveFrames < graceFrames)
            inactiveFrames++
        if (inactiveFrames >= graceFrames)
        {
            active = false
            fracColor.setFromRgba(INACTIVE_COLOR, INACTIVE_COLOR, INACTIVE_COLOR, 1f)
        }
    }

    fun markMeasured()
    {
        measuredThisFrame = true
        active            = true
        inactiveFrames    = 0
    }

    fun setTime(newValue: Float)
    {
        latestText.clear().append(newValue, decimals = 1).append(" \u00B5s")
        latestValueTime       = System.nanoTime()
        graphIntervalPeak     = max(graphIntervalPeak, newValue)
        graphIntervalMeasured = true
        graphIntervalTotal   += newValue
        graphIntervalCount++
    }

    fun recordGraphSample()
    {
        frameTimeTotal            = (frameTimeTotal - intervalTotals[graphHead] + graphIntervalTotal).coerceAtLeast(0f)
        sampleCount              += graphIntervalCount - intervalCounts[graphHead]
        intervalTotals[graphHead] = graphIntervalTotal
        intervalCounts[graphHead] = graphIntervalCount
        graphIntervalTotal        = 0f
        graphIntervalCount        = 0

        avg = if (sampleCount > 0) frameTimeTotal / sampleCount else 0f
        avgText.clear()
        if (sampleCount > 0) avgText.append(avg, decimals = 1).append(" \u00B5s") else avgText.append("—")

        graphHistory[graphHead]  = graphIntervalPeak
        graphMeasured[graphHead] = graphIntervalMeasured
        graphIntervalPeak        = 0f
        graphIntervalMeasured    = false
        graphHead                = (graphHead + 1) % graphHistory.size
        graphSampleCount         = min(graphSampleCount + 1, graphHistory.size)

        var windowMin = Float.MAX_VALUE
        var windowMax = 0f
        var hasMeasurement = false
        for (i in graphHistory.indices)
        {
            if (!graphMeasured[i]) continue
            hasMeasurement = true
            windowMin = min(windowMin, graphHistory[i])
            windowMax = max(windowMax, graphHistory[i])
        }

        min = if (hasMeasurement) windowMin else 0f
        max = windowMax
        minText.clear()
        maxText.clear()

        if (hasMeasurement)
        {
            minText.append(min, decimals = 1).append(" \u00B5s")
            maxText.append(max, decimals = 1).append(" \u00B5s")
        }
        else
        {
            minText.append("—")
            maxText.append("—")
        }
    }

    fun graphValue(index: Int): Float =
        graphHistory[(graphHead - graphSampleCount + index + graphHistory.size) % graphHistory.size]

    fun calculateFraction(totalFrameTime: Float)
    {
        frac = if (totalFrameTime > 0f) frameTimeTotal / totalFrameTime else 0f
        fracText.clear().append(frac * 100, decimals = 1).append(" %")

        val f = if (frac == 1f) 1f else (1f - frac).pow(2f)
        fracColor.setFromRgba(1f, f, f)
    }

    fun setLabel(label: CharSequence)
    {
        labelText.clear()
        val count = min(label.length, MAX_LABEL_LENGTH)
        labelText.append(label, 0, count)
        if (count < label.length && labelText.isNotEmpty())
            labelText.setCharAt(labelText.lastIndex, ELLIPSIS)
    }

    fun hasCollapsedAncestor(): Boolean
    {
        var ancestor = parent
        while (ancestor != null)
        {
            if (ancestor.collapsed)
                return true
            ancestor = ancestor.parent
        }
        return false
    }

    fun isDescendantOf(ancestor: Measurement): Boolean
    {
        var current = parent
        while (current != null)
        {
            if (current === ancestor)
                return true
            current = current.parent
        }
        return false
    }

    fun setHighlighted(isHighlighted: Boolean)
    {
        if (isHighlighted)
            latestColor.setFrom(color)
        else
            latestColor.setFromRgba(INACTIVE_COLOR, INACTIVE_COLOR, INACTIVE_COLOR, 1f)
        
        latestColor.alpha = 
            if (latestValueTime == 0L) 0f 
            else (1f - (System.nanoTime() - latestValueTime) / 750_000_000f).coerceIn(0f, 1f)

        if (active && isHighlighted)
            highlightColor.setFrom(color)
        else
            highlightColor.setFromRgba(INACTIVE_COLOR, INACTIVE_COLOR, INACTIVE_COLOR, 1f)
    }

    companion object
    {
        private const val MAX_LABEL_LENGTH    = 128
        private const val VALUE_TEXT_CAPACITY = 24
        private const val ELLIPSIS            = '\u2026'
        private const val INACTIVE_COLOR      = 0.3f
    }
}
