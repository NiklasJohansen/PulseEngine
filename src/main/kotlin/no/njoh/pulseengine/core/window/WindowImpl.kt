package no.njoh.pulseengine.core.window

import no.njoh.pulseengine.core.config.ConfigurationInternal
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.LogLevel
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWWindowContentScaleCallback
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil
import kotlin.math.max

open class WindowImpl : WindowInternal
{
    override var windowHandle = MemoryUtil.NULL
    override var screenMode = ScreenMode.WINDOWED
    override var width = 800
    override var height = 600
    override var scale = 1f
    override var isFocused = false
    override var wasResized = false
    override var title = ""

    private var scaleChangeCallbacks = mutableListOf<(Float) -> Unit>()
    private var resizeCallBack: (width: Int, height: Int, windowRecreated: Boolean) -> Unit = { _, _, _ -> }
    private var windowedWidth = 800
    private var windowedHeight = 600

    override fun init(config: ConfigurationInternal)
    {
        Logger.info("Initializing window (${this::class.simpleName})")

        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW")

        GLFWErrorCallback.createPrint(System.err).set()

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)

        if (config.getEnum("gpuLogLevel", LogLevel::class) != LogLevel.OFF)
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)

        this.title          = config.gameName
        this.screenMode     = config.screenMode
        this.windowedWidth  = config.windowWidth
        this.windowedHeight = config.windowHeight

        createWindow()
    }

    private fun createWindow()
    {
        width  = windowedWidth
        height = windowedHeight

        var monitor = MemoryUtil.NULL
        if (screenMode == ScreenMode.FULLSCREEN)
        {
            monitor = getWindowMonitor()
            val videoMode = glfwGetVideoMode(monitor)
            width = videoMode?.width() ?: windowedWidth
            height = videoMode?.height() ?: windowedHeight
        }

        val newWindowHandle = glfwCreateWindow(width, height, title, monitor, windowHandle)
        if (newWindowHandle == MemoryUtil.NULL)
            throw RuntimeException("Failed to create the GLFW windowHandle")

        // Destroy previous window if it existed
        if (windowHandle != MemoryUtil.NULL)
            glfwDestroyWindow(windowHandle)

        this.windowHandle = newWindowHandle
        this.scale = getMonitorScaling()
        this.isFocused = glfwGetWindowAttrib(windowHandle, GLFW_FOCUSED) == GLFW_TRUE

        if (screenMode == ScreenMode.WINDOWED)
        {
            val mode = glfwGetVideoMode(getWindowMonitor())!!
            glfwSetWindowPos(windowHandle, (mode.width() - width) / 2, (mode.height() - height) / 2)
        }

        glfwSetWindowSizeCallback(windowHandle, object : GLFWWindowSizeCallback()
        {
            override fun invoke(window: Long, width: Int, height: Int)
            {
                if (width != 0 && height != 0)
                {
                    this@WindowImpl.width = width
                    this@WindowImpl.height = height
                    resizeCallBack(width, height, false)
                    wasResized = true
                }
            }
        })

        glfwSetWindowContentScaleCallback(windowHandle, object : GLFWWindowContentScaleCallback()
        {
            override fun invoke(window: Long, xScale: Float, yScale: Float)
            {
                val newScale = max(xScale, yScale)
                if (newScale != scale)
                {
                    scale = newScale
                    scaleChangeCallbacks.forEachFast { it(newScale) }
                }
            }
        })

        glfwSetWindowFocusCallback(windowHandle) { _, focused -> isFocused = focused }

        updateTitle(title)
        glfwMakeContextCurrent(windowHandle)
        glfwSwapInterval(0)
        glfwShowWindow(windowHandle)
    }

    override fun updateScreenMode(screenMode: ScreenMode)
    {
        if (screenMode == this.screenMode)
            return

        // Create new window
        this.screenMode = screenMode
        createWindow()

        // Notify observers
        resizeCallBack(width, height, true)
        wasResized = true
    }

    override fun updateTitle(title: String)
    {
        if (title == this.title)
            return

        this.title = title
        glfwSetWindowTitle(windowHandle, title)
    }

    override fun close()
    {
        glfwSetWindowShouldClose(windowHandle, true)
    }

    override fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit) { resizeCallBack = callback }

    override fun swapBuffers() = glfwSwapBuffers(windowHandle)

    override fun isOpen(): Boolean = !glfwWindowShouldClose(windowHandle)

    override fun destroy()
    {
        Logger.info("Destroying window (${this::class.simpleName})")
        glfwSetErrorCallback(null)
        glfwDestroyWindow(windowHandle)
        glfwTerminate()
    }

    override fun setOnScaleChanged(callback: (scale: Float) -> Unit)
    {
        scaleChangeCallbacks.add(callback)
    }

    private fun getWindowMonitor(): Long
    {
        if (windowHandle == MemoryUtil.NULL)
            return glfwGetPrimaryMonitor()

        val xWindow = IntArray(1)
        val yWindow = IntArray(1)
        val widthWindow = IntArray(1)
        val heightWindow = IntArray(1)
        glfwGetWindowPos(windowHandle, xWindow, yWindow)
        glfwGetWindowSize(windowHandle, widthWindow, heightWindow)

        val xWindowCenter = xWindow[0] + widthWindow[0] / 2
        val yWindowCenter = yWindow[0] + heightWindow[0] / 2

        return getMonitors().firstOrNull { monitor ->
            val videoMode = glfwGetVideoMode(monitor)
            val widthMonitor = videoMode?.width() ?: 0
            val heightMonitor = videoMode?.height() ?: 0
            val xMonitor = IntArray(1)
            val yMonitor = IntArray(1)
            glfwGetMonitorPos(monitor, xMonitor, yMonitor)

            // True if center of window is within the monitor bounds
            xWindowCenter >= xMonitor[0] && xWindowCenter <= xMonitor[0] + widthMonitor &&
            yWindowCenter >= yMonitor[0] && yWindow[0] <= yWindowCenter + heightMonitor

        } ?: glfwGetPrimaryMonitor()
    }

    private fun getMonitors(): List<Long> =
        glfwGetMonitors()?.let {
            monitors -> 0.until(monitors.limit()).mapNotNull { monitors[it] }
        } ?: emptyList()

    private fun getMonitorScaling(): Float {
        val xScale = FloatArray(1)
        val yScale = FloatArray(1)
        glfwGetMonitorContentScale(getWindowMonitor(), xScale, yScale)
        return max(xScale[0], yScale[0])
    }
}