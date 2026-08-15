package no.njoh.pulseengine.core.window

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.config.ConfigurationInternal
import no.njoh.pulseengine.core.graphics.gpu.GlContract.OPENGL_41
import no.njoh.pulseengine.core.graphics.gpu.glContract
import no.njoh.pulseengine.core.input.CursorMode.*
import no.njoh.pulseengine.core.shared.platform.*
import no.njoh.pulseengine.core.shared.platform.KeyEvent.*
import no.njoh.pulseengine.core.shared.platform.MouseButtonEvent.*
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.component1
import no.njoh.pulseengine.core.shared.utils.Extensions.component2
import no.njoh.pulseengine.core.shared.utils.LogLevel
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.window.ScreenMode.*
import org.joml.Vector2i
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.memPointerBuffer
import org.lwjgl.system.MemoryUtil.memUTF8
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

    private val incomingEvents = ArrayList<PlatformEvent>()
    private var contentScaleChangedCallbacks = ArrayList<(Float) -> Unit>()
    private val onInitFrame = ArrayList<(PulseEngineInternal) -> Unit>()
    private var onFileDropped = mutableListOf<(String) -> Unit>()
    private var resizeCallback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit = { _, _, _ -> }
    private var connectedGamepadIds = ArrayList<Int>()
    private var initWidth = 800
    private var initHeight = 600
    private var glContract = OPENGL_41

    override fun init(config: ConfigurationInternal)
    {
        Logger.info { "Initializing window (WindowImpl)" }
        val glContract = checkNotNull(config.runtimeProfile.glContract) { "WindowImpl cannot be initialized in RuntimeProfile.HEADLESS" }

        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW")

        GLFWErrorCallback.createPrint(System.err).set()

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, glContract.glMajorVersion)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, glContract.glMinorVersion)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_SRGB_CAPABLE, GLFW_TRUE)

        if (config.gpuLogLevel != LogLevel.OFF)
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)

        this.title               = config.gameName
        this.screenMode          = config.screenMode
        this.initWidth           = config.windowWidth
        this.initHeight          = config.windowHeight
        this.glContract          = glContract

        createWindow()
    }

    override fun initFrame(engineInternal: PulseEngineInternal)
    {
        wasResized = false

        onInitFrame.forEachFast { it(engineInternal) }
        onInitFrame.clear()
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
            throw RuntimeException(
                "Failed to create the GLFW window: $glContract requires an OpenGL " +
                "${glContract.glMajorVersion}.${glContract.glMinorVersion} core context"
            )

        // Destroy previous window if it exists
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
                resizeCallback(w, h, false)
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
        
        glfwSetCharCallback(windowHandle) { _, character -> incomingEvents += CharacterEvent(character.toChar()) }

        glfwSetCursorPosCallback(windowHandle) { _, xPos, yPos -> incomingEvents += MouseMoveEvent(xPos.toFloat(), yPos.toFloat()) } 

        glfwSetScrollCallback(windowHandle) { _, xOffset, yOffset -> incomingEvents += ScrollEvent(xOffset.toFloat(), yOffset.toFloat()) }

        glfwSetMouseButtonCallback(windowHandle) { _, button, action, _ -> 
            when (action)
            {
                GLFW_PRESS   -> incomingEvents += MouseButtonEvent(button, MouseAction.PRESSED)
                GLFW_RELEASE -> incomingEvents += MouseButtonEvent(button, MouseAction.RELEASED)
            }
        }

        glfwSetKeyCallback(windowHandle) { _, keyCode, _, action, _ ->
            when (action)
            {
                GLFW_PRESS   -> incomingEvents += KeyEvent(keyCode, KeyAction.PRESSED)
                GLFW_RELEASE -> incomingEvents += KeyEvent(keyCode, KeyAction.RELEASED)
                GLFW_REPEAT  -> incomingEvents += KeyEvent(keyCode, KeyAction.REPEAT)
            }
        }

        glfwSetJoystickCallback { jid: Int, event: Int ->
            when (event)
            {
                GLFW_CONNECTED ->
                {
                    incomingEvents += GamepadConnectionEvent(jid, connected = true)
                    connectedGamepadIds += jid
                }
                GLFW_DISCONNECTED ->
                {
                    incomingEvents += GamepadConnectionEvent(jid, connected = false)
                    connectedGamepadIds -= jid
                }
            }
        }

        glfwSetDropCallback(windowHandle) { _, count, names ->
            val pointers = memPointerBuffer(names, count)
            for (i in 0 until count)
            {
                val path = memUTF8(pointers[i])
                onFileDropped.forEachFast { it(path) }
                Logger.info { "File dropped: $path" }
            }
        }

        IntRange(GLFW_JOYSTICK_1, GLFW_JOYSTICK_LAST)
            .filter { glfwJoystickPresent(it) && glfwJoystickIsGamepad(it) }
            .forEach { 
                incomingEvents += GamepadConnectionEvent(it, connected = true)
                connectedGamepadIds += it
            }

        updateTitle(title)
        glfwMakeContextCurrent(windowHandle)
        glfwSwapInterval(0)
        glfwShowWindow(windowHandle)
    }

    override fun pollIncomingPlatformEvents(buffer: PlatformEventBuffer)
    {
        glfwPollEvents()
        pollGamepads()

        incomingEvents.forEachFast { buffer.add(it) }
        incomingEvents.clear()
    }

    override fun handleOutgoingPlatformEvents(buffer: PlatformEventBuffer)
    {
        buffer.forEachEvent()
        {
            when (it)
            {
                is ClipboardUpdateEvent ->
                {
                    glfwSetClipboardString(windowHandle, it.content)
                }
                is ClipboardRequestUpdateEvent ->
                {
                    val content = glfwGetClipboardString(windowHandle) ?: ""
                    incomingEvents += ClipboardUpdateEvent(content)
                }
                is CursorSetEvent ->
                {
                    glfwSetCursor(windowHandle, it.handle)
                }
                is CursorSetPosEvent ->
                {
                    glfwSetCursorPos(windowHandle, it.xPos.toDouble(), it.yPos.toDouble())
                }
                is CursorModeEvent ->
                {
                    val glfwMode = when (it.mode)
                    {
                        NORMAL  -> GLFW_CURSOR_NORMAL
                        HIDDEN  -> GLFW_CURSOR_HIDDEN
                        GRABBED -> GLFW_CURSOR_DISABLED
                    }
                    glfwSetInputMode(windowHandle, GLFW_CURSOR, glfwMode)
                }
                is CursorCreateEvent ->
                {
                    if (it.cursor.standardShape != null)
                    {
                        val handle = glfwCreateStandardCursor(it.cursor.standardShape!!)
                        it.cursor.finalize(handle)
                    }
                    else
                    {
                        val cursorImg = GLFWImage.create()
                        cursorImg.width(it.cursor.width)
                        cursorImg.height(it.cursor.height)
                        cursorImg.pixels(it.cursor.pixelBuffer!!)
                        val handle = glfwCreateCursor(cursorImg, it.cursor.xHotspot, it.cursor.yHotspot)
                        it.cursor.finalize(handle)
                    }
                }
                is CursorDestroyEvent ->
                {
                    glfwDestroyCursor(it.handle)
                }
            }
        }
    }
 
    override fun updateScreenMode(mode: ScreenMode)
    {
        if (mode == screenMode)
            return

        screenMode = mode
        runOnInitFrame()
        {
            createWindow()
            resizeCallback(width, height, true)
            wasResized = true
        }
    }

    override fun updateTitle(title: String)
    {
        if (title == this.title)
            return

        this.title = title
        runOnInitFrame { glfwSetWindowTitle(windowHandle, title) }
    }

    override fun close()
    {
        runOnInitFrame { glfwSetWindowShouldClose(windowHandle, true) }
    }

    override fun setOnResizeEvent(callback: (width: Int, height: Int, windowRecreated: Boolean) -> Unit) { resizeCallback = callback }

    override fun setOnFileDropped(callback: (String) -> Unit)
    {
        onFileDropped += callback
    }
    
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

    private fun pollGamepads()
    {
        connectedGamepadIds.forEachFast() 
        {
            val axes = glfwGetJoystickAxes(it)
            val buttons = glfwGetJoystickButtons(it)
            if (axes != null && buttons != null)
                incomingEvents += GamepadUpdateEvent(it, axes, buttons)
        }
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
        glfwGetMonitors()
            ?.let { monitors -> 0.until(monitors.limit()).mapNotNull { monitors[it] } }
            ?: emptyList()

    private fun getWindowContentScaling(windowHandle: Long): Float
    {
        val xScale = FloatArray(1)
        val yScale = FloatArray(1)
        glfwGetWindowContentScale(windowHandle, xScale, yScale)
        return max(xScale[0], yScale[0])
    }

    private fun getFramebufferSize(windowHandle: Long): Vector2i
    {
        val fbWidth = IntArray(1)
        val fbHeight = IntArray(1)
        glfwGetFramebufferSize(windowHandle, fbWidth, fbHeight)
        return Vector2i(fbWidth[0], fbHeight[0])
    }

    private fun runOnInitFrame(command: PulseEngineInternal.() -> Unit) { onInitFrame.add(command) }
}
