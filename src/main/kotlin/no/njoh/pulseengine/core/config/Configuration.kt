package no.njoh.pulseengine.core.config

import no.njoh.pulseengine.core.shared.primitives.GameLoopMode
import no.njoh.pulseengine.core.window.ScreenMode
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

interface Configuration
{
    /** The name of the game. Sets the default save folder name and window title */
    var gameName: String

    /** The target frames per second */
    var targetFps: Int

    /** The rate of ticks per second for the fixed update loop */
    var fixedTickRate: Int

    /** Defines whether the game loop should be multithreaded or not */
    var gameLoopMode: GameLoopMode

    /**
     * Loads a configuration file with the given [fileName] from classpath
     */
    fun load(fileName: String)

    /**
     * Returns the named config property as a [String] or null if not found
     */
    fun getString(name: String): String?

    /**
     * Returns the named config property as an [Int] or null if not present
     */
    fun getInt(name: String): Int?

    /**
     * Returns the named config property as a [Float] or null if not present
     */
    fun getFloat(name: String): Float?

    /**
     * Returns the named config property as a [Boolean] or null if not present
     */
    fun getBool(name: String): Boolean?

    /**
     * Returns the named config property as an [Enum] or null if not present
     */
    fun <T: Enum<T>> getEnum(name: String, type: KClass<T>): T?
}

interface ConfigurationInternal : Configuration
{
    val windowWidth: Int
    val windowHeight: Int
    val screenMode: ScreenMode

    fun init()
    fun setOnChanged(callback: (property: KProperty<*>, value: Any) -> Unit)
}