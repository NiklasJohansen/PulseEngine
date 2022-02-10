package no.njoh.pulseengine.modules.config

import no.njoh.pulseengine.data.ScreenMode
import no.njoh.pulseengine.util.LogLevel
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.loadStream
import java.lang.Exception
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

open class ConfigurationImpl : ConfigurationInternal
{
    // Exposed properties
    override var creatorName: String    by StringConfig("PulseEngine")
    override var gameName: String       by StringConfig("ExampleGame")
    override var fixedTickRate: Int     by IntConfig(60)
    override var targetFps: Int         by IntConfig(60)
    override var windowWidth: Int       by IntConfig(1000)
    override var windowHeight: Int      by IntConfig(800)
    override var screenMode: ScreenMode by EnumConfig(ScreenMode.WINDOWED, { ScreenMode.valueOf(it.toUpperCase()) })

    // Internal properties
    private val properties: Properties = Properties()
    private var onChangeCallback: (property: KProperty<*>, value: Any) -> Unit = { _, _ -> }

    override fun init()
    {
        load("application.cfg")
    }

    override fun setOnChanged(callback: (property: KProperty<*>, value: Any) -> Unit)
    {
        onChangeCallback = callback
    }

    override fun load(fileName: String) =
        try
        {
            fileName.loadStream()
                ?.let {
                    properties.load(it)
                    applyConfigProperties()
                    Logger.info("Configuration file: $fileName was loaded successfully")
                } ?: Logger.warn("Configuration file: $fileName was not found")
        }
        catch (e: Exception) {
            Logger.error("Failed to load configuration: $fileName, reason: ${e.message}")
        }

    private fun applyConfigProperties()
    {
        getString("logLevel")?.let { Logger.logLevel = LogLevel.valueOf(it.toUpperCase()) }
    }

    override fun getString(name: String): String? =
        try { properties[name] as String? }
        catch (e: Exception) { throw Exception("Failed to find or parse String property: $name")
        }

    override fun getInt(name: String): Int? =
        try { properties[name]?.toString()?.toInt() }
        catch (e: Exception) { throw Exception("Failed to find or parse Int property: $name")
        }

    override fun getBool(name: String): Boolean? =
        try { properties[name]?.toString()?.toBoolean() }
        catch (e: Exception) { throw Exception("Failed to find or parse Boolean property: $name")
        }

    override fun <T: Enum<T>> getEnum(name: String, type: KClass<T>): T? =
        try { java.lang.Enum.valueOf(type.java, properties[name].toString()) }
        catch (e: Exception) { throw Exception("Failed to find or parse Boolean property: $name")
        }

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