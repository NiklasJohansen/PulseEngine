package no.njoh.pulseengine.core.config

import no.njoh.pulseengine.core.shared.primitives.GameLoopMode
import no.njoh.pulseengine.core.shared.primitives.GameLoopMode.*
import no.njoh.pulseengine.core.window.ScreenMode
import no.njoh.pulseengine.core.shared.utils.LogLevel
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.loadStream
import no.njoh.pulseengine.core.shared.utils.Extensions.toNowFormatted
import no.njoh.pulseengine.core.window.ScreenMode.*
import java.io.FileNotFoundException
import java.lang.Exception
import java.util.Properties
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

open class ConfigurationImpl : ConfigurationInternal
{
    override var gameName: String           by StringConfig("ExampleGame")
    override var targetFps: Int             by IntConfig(120)
    override var fixedTickRate: Int         by IntConfig(60)
    override var windowWidth: Int           by IntConfig(1000)
    override var windowHeight: Int          by IntConfig(800)
    override var screenMode: ScreenMode     by EnumConfig(WINDOWED, ScreenMode::class)
    override var gameLoopMode: GameLoopMode by EnumConfig(MULTITHREADED, GameLoopMode::class)

    private val properties = Properties()
    private var onChangeCallback = { prop: KProperty<*>, value: Any -> }

    override fun init()
    {
        Logger.info("Initializing configuration (${this::class.simpleName})")
        load("/pulseengine/config/engine_default.cfg")
        load("application.cfg")
        Logger.logLevel = getEnum("logLevel", LogLevel::class) ?: LogLevel.INFO
    }

    override fun load(fileName: String) =
        try
        {
            val startTime = System.nanoTime()
            val stream = fileName.loadStream() ?: throw FileNotFoundException("file not found")
            properties.load(stream)
            for ((key, value) in properties)
            {
                properties[key] = when {
                    value !is String -> value
                    value == "true" || value == "false" -> value.toBoolean()
                    value.all { it.isDigit() } -> value.toInt()
                    value.all { it.isDigit() || it == '.' } -> value.toFloat()
                    else -> value
                }
            }
            Logger.debug("Loaded configuration file: $fileName in ${startTime.toNowFormatted()}")
        }
        catch (e: Exception)
        {
            Logger.error("Failed to load configuration: $fileName, reason: ${e.message}")
        }

    override fun setOnChanged(callback: (property: KProperty<*>, value: Any) -> Unit)
    {
        onChangeCallback = callback
    }

    override fun getString(name: String) =
        (properties[name] as? String) ?: null.also { Logger.error("Config property: $name (String) not found") }

    override fun getInt(name: String) =
        (properties[name] as? Int) ?: null.also { Logger.error("Config property: $name (Int) not found") }

    override fun getFloat(name: String) =
        (properties[name] as? Float) ?: null.also { Logger.error("Config property: $name (Float) not found") }

    override fun getBool(name: String) =
        (properties[name] as? Boolean) ?: null.also { Logger.error("Config property: $name (Boolean) not found") }

    override fun <T: Enum<T>> getEnum(name: String, type: KClass<T>): T? {
        val value = properties[name] ?: return null.also { Logger.error("Config property: $name (Enum) not found") }
        if (value::class == type)
            return value as T

        if (value is String)
            runCatching { return java.lang.Enum.valueOf(type.java, value).also { properties[name] = it } }

        Logger.error("Config property: $name (Enum) not applicable to type: ${type.simpleName}")
        return null
    }

    inner class StringConfig(private val initValue: String)
    {
        operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: String)
        {
            properties[prop.name] = value
            onChangeCallback.invoke(prop, value)
        }

        operator fun getValue(thisRef: Any?, prop: KProperty<*>): String
        {
            val value = properties[prop.name]
            return if (value is String) value else
            {
                if (value != null)
                    Logger.warn("Config property: ${prop.name} (String) has value: $value (${value::class.simpleName}), using default: $initValue")
                properties[prop.name] = initValue
                initValue
            }
        }
    }

    inner class IntConfig(private val initValue: Int)
    {
        operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: Int)
        {
            properties[prop.name] = value
            onChangeCallback.invoke(prop, value)
        }

        operator fun getValue(thisRef: Any?, prop: KProperty<*>): Int
        {
            val value = properties[prop.name]
            return if (value is Int) value else
            {
                if (value != null)
                    Logger.warn("Config property: ${prop.name} (Int) has value: $value (${value::class.simpleName}), using default: $initValue")
                properties[prop.name] = initValue
                initValue
            }
        }
    }

    inner class EnumConfig <T : Enum<T>> (private val initValue: T, private val type: KClass<T>)
    {
        operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: T)
        {
            properties[prop.name] = value
            onChangeCallback.invoke(prop, value)
        }

        operator fun getValue(thisRef: Any?, prop: KProperty<*>): T {
            val value = properties[prop.name]
            if (value != null && value::class == type)
                return value as T

            var newValue = initValue
            if (value is String)
            {
                try { newValue = java.lang.Enum.valueOf(type.java, value) }
                catch (e: Exception)
                {
                    Logger.warn("Config property: ${prop.name} (Enum) has unknown value: $value, using default: ${type.simpleName}.$newValue")
                }
            }
            else if (value != null)
            {
                Logger.warn("Config property: ${prop.name} (Enum) has unknown value: $value (${value::class.simpleName}), using default: ${type.simpleName}.$newValue")
            }
            properties[prop.name] = newValue
            return newValue
        }
    }
}