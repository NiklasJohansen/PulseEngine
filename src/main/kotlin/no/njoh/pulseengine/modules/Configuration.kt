package no.njoh.pulseengine.modules

import no.njoh.pulseengine.data.ScreenMode
import no.njoh.pulseengine.data.ScreenMode.WINDOWED
import no.njoh.pulseengine.modules.console.ConsoleTarget
import java.lang.Exception
import java.util.*
import kotlin.reflect.KProperty

// Exposed to game and engine
interface ConfigurationInterface
{
    var creatorName: String
    var gameName: String
    var targetFps: Int
    var fixedTickRate: Int

    fun load(fileName: String)
    fun getString(name: String): String?
    fun getInt(name: String): Int?
    fun getBool(name: String): Boolean?
}

// Exposed to engine
interface ConfigurationEngineInterface : ConfigurationInterface
{
    val windowWidth: Int
    val windowHeight: Int
    val screenMode: ScreenMode
    fun init()
    fun setOnChanged(callback: (property: KProperty<*>, value: Any) -> Unit)
}

@ConsoleTarget
class Configuration : ConfigurationEngineInterface
{
    // Exposed properties
    override var creatorName: String    by StringConfig("PulseEngine")
    override var gameName: String       by StringConfig("ExampleGame")
    override var fixedTickRate: Int     by IntConfig(60)
    override var targetFps: Int         by IntConfig(60)
    override var windowWidth: Int       by IntConfig(1000)
    override var windowHeight: Int      by IntConfig(800)
    override var screenMode: ScreenMode by EnumConfig(WINDOWED, { ScreenMode.valueOf(it.toUpperCase()) })

    // Internal properties
    private val properties: Properties = Properties()
    private var onChangeCallback: (property: KProperty<*>, value: Any) -> Unit = { _,_ -> }

    override fun init()
    {
        load("/application.cfg")
        getString("creatorName")?.let { creatorName = it }
        getString("gameName")?.let { gameName = it }
        getInt("targetFps")?.let { targetFps = it }
        getInt("windowWidth")?.let { windowWidth = it }
        getInt("windowHeight")?.let { windowHeight = it }
        getString("screenMode")?.let { ScreenMode.valueOf(it.toUpperCase()) }
    }

    override fun setOnChanged(callback: (property: KProperty<*>, value: Any) -> Unit)
    {
        onChangeCallback = callback
    }

    override fun load(fileName: String) =
        try {
            javaClass.getResourceAsStream(fileName)
                ?.let {
                    println("Loading configuration from file: $fileName ...")
                    properties.load(it)
                } ?: println("Configuration file: $fileName was not found")
        } catch (e: Exception) { System.err.println("Failed to load configuration: $fileName, reason: ${e.message}") }

    override fun getString(name: String): String? =
        try { properties[name] as String? }
        catch (e: Exception) { throw Exception("Failed to find or parse String property: $name") }

    override fun getInt(name: String): Int? =
        try { properties[name]?.toString()?.toInt() }
        catch (e: Exception) { throw Exception("Failed to find or parse Int property: $name") }

    override fun getBool(name: String): Boolean? =
        try { properties[name]?.toString()?.toBoolean() }
        catch (e: Exception) { throw Exception("Failed to find or parse Boolean property: $name") }

    inner class StringConfig(private val initValue: String)
    {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String =
            properties.getProperty(property.name)
                ?: initValue.also { setValue(thisRef, property, initValue) }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String)
        {
            properties.setProperty(property.name, value)
            onChangeCallback.invoke(property, value)
        }
    }

    inner class IntConfig(private val initValue: Int)
    {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int =
            properties.getProperty(property.name)?.toInt()
                ?: initValue.also { setValue(thisRef, property, initValue) }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int)
        {
            properties.setProperty(property.name, value.toString())
            onChangeCallback.invoke(property, value)
        }
    }

    inner class EnumConfig <T> (private val initValue: T, private val mapper: (String) -> T)
    {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
            properties.getProperty(property.name)
                ?.let { mapper.invoke(it) }
                ?: initValue.also { setValue(thisRef, property, initValue) }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
        {
            properties.setProperty(property.name, value.toString())
            onChangeCallback.invoke(property, value!!)
        }
    }
}
