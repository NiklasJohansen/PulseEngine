package no.njoh.pulseengine.modules

import no.njoh.pulseengine.data.ScreenMode
import no.njoh.pulseengine.data.ScreenMode.FULLSCREEN
import no.njoh.pulseengine.data.ScreenMode.WINDOWED
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWWindowSizeCallback
import org.lwjgl.system.MemoryUtil.NULL

// Exposed to game code
interface WindowInterface
{
    fun updateScreenMode(mode: ScreenMode)
    fun close()
    val screenMode: ScreenMode
    val width: Int
    val height: Int
    var title: String
}

// Exposed to game engine
interface WindowEngineInterface : WindowInterface
{
    fun init(initWidth: Int, initHeight: Int, screenMode: ScreenMode, gameName: String)
    fun cleanUp()
    fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit)
    fun swapBuffers()
    fun isOpen(): Boolean
    val windowHandle: Long
}

class Window : WindowEngineInterface
{
    // Exposed properties
    override var windowHandle : Long = NULL
    override var screenMode: ScreenMode = WINDOWED
    override var width: Int = 800
    override var height: Int = 600
    override var title: String = ""
        set(value) {
            glfwSetWindowTitle(windowHandle, value)
            field = value
        }

    // Internal properties
    private var resizeCallBack: (width: Int, height: Int, windowRecreated: Boolean) -> Unit = { _, _, _ -> }
    private var windowedWidth: Int = 800
    private var windowedHeight: Int = 600

    override fun init(initWidth: Int, initHeight: Int, screenMode: ScreenMode, gameName: String)
    {
        println("Initializing Window...")

        if (!glfwInit())
            throw IllegalStateException("Unable to initialize GLFW")

        GLFWErrorCallback.createPrint(System.err).set()
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_DEPTH_BITS,24)

        this.screenMode = screenMode
        this.windowedWidth = initWidth
        this.windowedHeight = initHeight
        this.width = initWidth
        this.height = initHeight

        var monitor = NULL
        if (screenMode == FULLSCREEN)
        {
            monitor = getWindowMonitor()
            val videoMode = glfwGetVideoMode(monitor)
            width = videoMode?.width() ?: width
            height = videoMode?.height() ?: height
        }

        val newWindowHandle = glfwCreateWindow(width, height, title, monitor, windowHandle)
        if (newWindowHandle == NULL)
            throw RuntimeException("Failed to create the GLFW windowHandle")

        // Destroy previous window if it existed
        if(windowHandle != NULL)
            glfwDestroyWindow(windowHandle)

        this.windowHandle = newWindowHandle
        this.title = gameName

        if(screenMode == WINDOWED)
        {
            val mode = glfwGetVideoMode(getWindowMonitor())!!
            glfwSetWindowPos(windowHandle, (mode.width() - width) / 2, (mode.height() - height) / 2)
        }

        glfwSetWindowSizeCallback(windowHandle, object : GLFWWindowSizeCallback()
        {
            override fun invoke(window: Long, width: Int, height: Int)
            {
                if(width != 0 && height != 0)
                {
                    this@Window.width = width
                    this@Window.height = height
                    resizeCallBack(width, height, false)
                }
            }
        })

        glfwMakeContextCurrent(windowHandle)
        glfwSwapInterval(0)
        glfwShowWindow(windowHandle)
    }

    override fun updateScreenMode(mode: ScreenMode)
    {
        if(mode == this.screenMode)
            return

        // Create new window
        init(windowedWidth, windowedHeight, mode, title)

        // Notify observers
        resizeCallBack(width, height, true)
    }

    override fun close()
    {
        glfwSetWindowShouldClose(windowHandle, true)
    }

    override fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit) { resizeCallBack = callback }

    override fun swapBuffers() = glfwSwapBuffers(windowHandle)

    override fun isOpen(): Boolean = !glfwWindowShouldClose(windowHandle)

    override fun cleanUp()
    {
        println("Cleaning up window...")
        glfwSetErrorCallback(null)
        glfwDestroyWindow(windowHandle)
        glfwTerminate()
    }

    private fun getWindowMonitor(): Long
    {
        if(windowHandle == NULL)
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
}