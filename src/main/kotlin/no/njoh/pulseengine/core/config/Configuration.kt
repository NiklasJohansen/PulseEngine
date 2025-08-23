package no.njoh.pulseengine.core.config

import no.njoh.pulseengine.core.shared.primitives.GameLoopMode
import no.njoh.pulseengine.core.shared.utils.LogLevel
import no.njoh.pulseengine.core.shared.utils.LogTarget
import no.njoh.pulseengine.core.window.ScreenMode
import kotlin.reflect.KClass

interface Configuration
{
    /** The name of the game. Sets the default save folder name and window title */
    var gameName: String

    /** The absolute path to where save files are stored. Default: ~/gameName/ */
    var saveDirectory: String

    /** The target frames per second */
    var targetFps: Int

    /** The rate of ticks per second for the fixed update loop */
    var fixedTickRate: Float

    /** Defines whether the game loop should be multithreaded or not */
    var gameLoopMode: GameLoopMode

    /** The output target to for all logging */
    var logTarget: LogTarget

    /** The general level for all logging */
    var logLevel: LogLevel

    /** The level for logging GPU related messages */
    var gpuLogLevel: LogLevel

    /** Enables measuring of GPU operations if true */
    var gpuProfiling: Boolean

    /**
     * Loads a configuration file with the given [filePath]
     */
    fun load(filePath: String)

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
    fun setOnChanged(callback: (propName: String, value: Any) -> Unit)
}