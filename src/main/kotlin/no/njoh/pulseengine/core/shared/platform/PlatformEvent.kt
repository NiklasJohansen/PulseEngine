package no.njoh.pulseengine.core.shared.platform

import gnu.trove.map.hash.THashMap
import no.njoh.pulseengine.core.asset.types.Cursor
import no.njoh.pulseengine.core.input.CursorMode
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/**
 * Base class for all platform events.
 * Platform events are used to communicate between the engine and the platform.
 */
abstract class PlatformEvent()
{
    companion object
    {
        @PublishedApi
        internal val pool = THashMap<Class<*>, MutableList<PlatformEvent>>()

        inline fun <reified T: PlatformEvent> create(new: () -> T, set: (T) -> Unit): T
        {
            val obj = pool.getOrPut(T::class.java) { mutableListOf() }.removeLastOrNull() ?: new()
            set(obj as T)
            return obj
        }

        fun release(event: PlatformEvent) = pool.getOrPut(this::class.java) { mutableListOf() }.add(event)
    }
}

/**
 * Dispatched by the platform when a key is pressed, released or repeated.
 */
class KeyEvent private constructor(var keyCode: Int, var action: KeyAction) : PlatformEvent()
{
    enum class KeyAction { PRESSED, RELEASED, REPEAT }
    
    companion object
    {
        operator fun invoke(keyCode: Int, action: KeyAction) = create(
            new = { KeyEvent(keyCode, action) },
            set = { it.keyCode = keyCode; it.action = action }
        )
    }
}

/**
 * Dispatched by the platform when a character is typed.
 */
class CharacterEvent private constructor(var character: Char) : PlatformEvent()
{
    companion object 
    {
        operator fun invoke(character: Char) = create(
            new = { CharacterEvent(character) }, 
            set = { it.character = character }
        )
    }
}

/**
 * Dispatched by the platform when the mouse cursor moves.
 */
class MouseMoveEvent private constructor(var x: Float, var y: Float) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(x: Float, y: Float) = create(
            new = { MouseMoveEvent(x, y) },
            set = { it.x = x; it.y = y }
        )
    }
}

/**
 * Dispatched by the platform when a mouse button is pressed or released.
 */
class MouseButtonEvent(var button: Int, var action: MouseAction) : PlatformEvent()
{
    enum class MouseAction { PRESSED, RELEASED }
    
    companion object
    {
        operator fun invoke(button: Int, action: MouseAction) = create(
            new = { MouseButtonEvent(button, action) },
            set = { it.button = button; it.action = action }
        )
    }
}

/**
 * Dispatched by the platform when the mouse wheel is scrolled.
 */
class ScrollEvent(var xOffset: Float, var yOffset: Float) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(xOffset: Float, yOffset: Float) = create(
            new = { ScrollEvent(xOffset, yOffset) },
            set = { it.xOffset = xOffset; it.yOffset = yOffset }
        )
    }
}

/**
 * Dispatched by the platform when a gamepad is connected or disconnected.
 */
class GamepadConnectionEvent(var id: Int, var connected: Boolean) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(id: Int, connected: Boolean) = create(
            new = { GamepadConnectionEvent(id, connected) },
            set = { it.id = id; it.connected = connected }
        )
    }
}

/**
 * Dispatched by the engine when a new gamepad state has been fetched from the platform.
 */
class GamepadUpdateEvent(var id: Int, var axes: FloatBuffer, var buttons: ByteBuffer) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(id: Int, axes: FloatBuffer, buttons: ByteBuffer) = create(
            new = { GamepadUpdateEvent(id, axes, buttons) },
            set = { it.id = id; it.axes = axes; it.buttons = buttons }
        )
    }
}

/**
 * Dispatched by the engine to make the platform change the cursor mode.
 */
class CursorModeEvent(var mode: CursorMode) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(mode: CursorMode) = create(
            new = { CursorModeEvent(mode) },
            set = { it.mode = mode }
        )
    }
}

/**
 * Dispatched by the engine to make the platform change the cursor position.
 */
class CursorSetPosEvent(var xPos: Float, var yPos: Float) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(xPos: Float, yPos: Float) = create(
            new = { CursorSetPosEvent(xPos, yPos) },
            set = { it.xPos = xPos; it.yPos = yPos }
        )
    }
}

/**
 * Dispatched by the engine to make the platform change the active cursor.
 */
class CursorSetEvent(var handle: Long) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(handle: Long) = create(
            new = { CursorSetEvent(handle) },
            set = { it.handle = handle }
        )
    }
}

/**
 * Dispatched by the engine to make the platform create a new cursor.
 */
class CursorCreateEvent(var cursor: Cursor) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(cursor: Cursor) = create(
            new = { CursorCreateEvent(cursor) },
            set = { it.cursor = cursor }
        )
    }
}

/**
 * Dispatched by the engine to make the platform destroy a cursor.
 */
class CursorDestroyEvent(var handle: Long) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(handle: Long) = create(
            new = { CursorDestroyEvent(handle) },
            set = { it.handle = handle }
        )
    }
}

/**
 * Dispatched by the engine when the clipboard content has been updated.
 */
class ClipboardUpdateEvent(var content: String) : PlatformEvent()
{
    companion object
    {
        operator fun invoke(content: String) = create(
            new = { ClipboardUpdateEvent(content) },
            set = { it.content = content }
        )
    }
}

/**
 * Dispatched by the engine to request the clipboard content. 
 * The platform will then dispatch a [ClipboardUpdateEvent] when the content is available.
 */
class ClipboardRequestUpdateEvent private constructor() : PlatformEvent()
{
    companion object 
    {
        operator fun invoke() = create(new = { ClipboardRequestUpdateEvent() }, {}) 
    }
}