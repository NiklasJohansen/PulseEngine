package no.njoh.pulseengine.core.window

import no.njoh.pulseengine.core.config.ConfigurationInternal
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.component1
import no.njoh.pulseengine.core.shared.utils.Extensions.component2
import no.njoh.pulseengine.core.shared.utils.LogLevel
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.window.ScreenMode.*
import org.joml.Vector2i
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil
import kotlin.math.max

open class WindowImpl : WindowInternal
{
    override var windowHandle = MemoryUtil.NULL
    override var screenMode = WINDOWED
    override var width = 800
    override var height = 600
    override var contentScale = 1f
    override var cursorPosScale = 1f
    override var isFocused = false
    override var wasResized = false
    override var title = ""

    private var contentScaleChangedCallbacks = mutableListOf<(Float) -> Unit>()
    private var resizeCallBack: (width: Int, height: Int, windowRecreated: Boolean) -> Unit = { _, _, _ -> }
    private var initWidth = 800
    private var initHeight = 600

    override fun init(config: ConfigurationInternal)
    {
        Logger.info { "Initializing window (WindowImpl)" }

        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW")

        GLFWErrorCallback.createPrint(System.err).set()

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_SRGB_CAPABLE, GLFW_TRUE)

        if (config.gpuLogLevel != LogLevel.OFF)
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)

        this.title      = config.gameName
        this.screenMode = config.screenMode
        this.initWidth  = config.windowWidth
        this.initHeight = config.windowHeight

        createWindow()
    }

    private fun createWindow()
    {
        var windowWidth  = initWidth
        var windowHeight = initHeight
        var monitor = MemoryUtil.NULL

        if (screenMode == FULLSCREEN)
        {
            monitor = getWindowMonitor()
            val videoMode = glfwGetVideoMode(monitor)
            windowWidth = videoMode?.width() ?: initWidth
            windowHeight = videoMode?.height() ?: initHeight
        }

        val prevWindowHandle = windowHandle
        windowHandle = glfwCreateWindow(windowWidth, windowHeight, title, monitor, prevWindowHandle)
        if (windowHandle == MemoryUtil.NULL)
            throw RuntimeException("Failed to create the GLFW windowHandle")

        // Destroy previous window if it existed
        if (prevWindowHandle != MemoryUtil.NULL)
            glfwDestroyWindow(prevWindowHandle)

        val (fbWidth, fbHeight) = getFramebufferSize(windowHandle)
        width = fbWidth
        height = fbHeight
        isFocused = glfwGetWindowAttrib(windowHandle, GLFW_FOCUSED) == GLFW_TRUE
        contentScale = getWindowContentScaling(windowHandle)
        updateCursorPosScale()

        if (screenMode == WINDOWED)
        {
            val mode = glfwGetVideoMode(getWindowMonitor())!!
            glfwSetWindowPos(windowHandle, (mode.width() - windowWidth) / 2, (mode.height() - windowHeight) / 2)
        }

        glfwSetFramebufferSizeCallback(windowHandle) { _, w, h ->
            if (w != 0 && h != 0)
            {
                width = w
                height = h
                resizeCallBack(w, h, false)
                wasResized = true
            }
        }

        glfwSetWindowContentScaleCallback(windowHandle) { _, xScale, yScale ->
            val newScale = max(xScale, yScale)
            if (newScale != contentScale)
            {
                contentScale = newScale
                updateCursorPosScale()
                contentScaleChangedCallbacks.forEachFast { it(newScale) }
            }
        }

        glfwSetWindowFocusCallback(windowHandle) { _, focused -> isFocused = focused }

        updateTitle(title)
        glfwMakeContextCurrent(windowHandle)
        glfwSwapInterval(0)
        glfwShowWindow(windowHandle)
    }

    override fun updateScreenMode(mode: ScreenMode)
    {
        if (mode == this.screenMode)
            return

        // Create new window
        screenMode = mode
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
        Logger.info { "Destroying window (${this::class.simpleName})" }
        glfwSetErrorCallback(null)
        glfwDestroyWindow(windowHandle)
        glfwTerminate()
    }

    override fun setOnContentScaleChanged(callback: (scale: Float) -> Unit)
    {
        contentScaleChangedCallbacks.add(callback)
    }

    private fun updateCursorPosScale()
    {
        cursorPosScale = when (glfwGetPlatform())
        {
            GLFW_PLATFORM_WAYLAND -> contentScale
            else -> 1f
        }
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

    private fun getWindowContentScaling(windowHandle: Long): Float {
        val xScale = FloatArray(1)
        val yScale = FloatArray(1)
        glfwGetWindowContentScale(windowHandle, xScale, yScale)
        return max(xScale[0], yScale[0])
    }

    private fun getFramebufferSize(windowHandle: Long): Vector2i {
        val fbWidth = IntArray(1)
        val fbHeight = IntArray(1)
        glfwGetFramebufferSize(windowHandle, fbWidth, fbHeight)
        return Vector2i(fbWidth[0], fbHeight[0])
    }
}