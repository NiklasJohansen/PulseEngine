package engine.modules

import engine.data.ScreenMode
import java.lang.Exception
import java.util.*

// Exposed to game and engine
interface ConfigurationInterface
{
    var targetFps: Int
    var tickRate: Int

    fun load(fileName: String)
    fun getString(name: String): String
    fun getInt(name: String): Int
    fun getBool(name: String): Boolean
}

// Exposed to engine
interface ConfigurationEngineInterface : ConfigurationInterface
{
    val windowWidth: Int
    val windowHeight: Int
    val screenMode: ScreenMode
    fun init()
}

class Configuration : ConfigurationEngineInterface
{
    // Exposed properties
    override var tickRate: Int = 60
    override var targetFps: Int = 60
    override var windowWidth: Int = 800
    override var windowHeight: Int = 600
    override var screenMode = ScreenMode.WINDOWED

    // Internal properties
    private val properties: Properties = Properties()

    override fun init()
    {
        load("/default.config")
        tickRate = getInt("tickRate")
        targetFps = getInt("targetFps")
        windowWidth = getInt("windowWidth")
        windowHeight = getInt("windowHeight")
        screenMode = ScreenMode.valueOf(getString("screenMode").toUpperCase())
    }

    override fun load(fileName: String) =
        try {
            println("Loading configuration from file: $fileName ...")
            properties.load(javaClass.getResourceAsStream(fileName))
        } catch (e: Exception) { System.err.println("Failed to load configuration: $fileName, reason: ${e.message}") }

    override fun getString(name: String): String =
        try { properties[name] as String }
        catch (e: Exception) { throw Exception("failed to find or parse String property: $name") }

    override fun getInt(name: String): Int =
        try { properties[name].toString().toInt() }
        catch (e: Exception) { throw Exception("failed to find or parse Int property: $name") }

    override fun getBool(name: String): Boolean =
        try { properties[name].toString().toBoolean() }
        catch (e: Exception) { throw Exception("failed to find or parse Boolean property: $name") }
}
