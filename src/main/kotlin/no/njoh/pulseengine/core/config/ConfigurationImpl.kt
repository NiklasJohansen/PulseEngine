package no.njoh.pulseengine.core.config

import no.njoh.pulseengine.core.shared.primitives.GameLoopMode
import no.njoh.pulseengine.core.shared.primitives.GameLoopMode.*
import no.njoh.pulseengine.core.shared.utils.Extensions.loadTextFromDisk
import no.njoh.pulseengine.core.window.ScreenMode
import no.njoh.pulseengine.core.shared.utils.LogLevel
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.toNowFormatted
import no.njoh.pulseengine.core.shared.utils.LogLevel.*
import no.njoh.pulseengine.core.shared.utils.LogTarget
import no.njoh.pulseengine.core.shared.utils.LogTarget.*
import no.njoh.pulseengine.core.window.ScreenMode.*
import java.io.File
import java.io.FileNotFoundException
import java.io.StringReader
import java.lang.Exception
import java.util.Properties
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

open class ConfigurationImpl : ConfigurationInternal
{
    private val properties = Properties()
    private var defaultSaveDir = File("$homeDir/ExampleGame").absolutePath
    private var onChangeCallback = { propName: String, value: Any -> }

    override var gameName: String           by StringConfig("ExampleGame")
    override var saveDirectory: String      by StringConfig(defaultSaveDir)
    override var targetFps: Int             by IntConfig(120)
    override var fixedTickRate: Float       by FloatConfig(60f, minValue = 0.000000001f)
    override var windowWidth: Int           by IntConfig(1920)
    override var windowHeight: Int          by IntConfig(1080)
    override var screenMode: ScreenMode     by EnumConfig(WINDOWED, ScreenMode::class)
    override var gameLoopMode: GameLoopMode by EnumConfig(MULTITHREADED, GameLoopMode::class)
    override var logTarget: LogTarget       by EnumConfig(STDOUT, LogTarget::class)
    override var logLevel: LogLevel         by EnumConfig(INFO, LogLevel::class)
    override var gpuLogLevel: LogLevel      by EnumConfig(OFF, LogLevel::class)
    override var gpuProfiling: Boolean      by BoolConfig(false)

    override fun init()
    {
        Logger.info { "Initializing configuration (ConfigurationImpl)" }
        runCatching { loadConfigFile("application.cfg") }
        runCatching { loadConfigFile("application-dev.cfg") }
        Logger.LEVEL = logLevel
        Logger.TARGET = logTarget
    }

    override fun load(filePath: String) = try
    {
        loadConfigFile(filePath)
    }
    catch (e: Exception)
    {
        Logger.error { "Failed to load configuration: $filePath, reason: ${e.message}" }
    }

    private fun loadConfigFile(filePath: String)
    {
        val startTime = System.nanoTime()
        val content = filePath.loadTextFromDisk()?.replace("\\", "/") ?: throw FileNotFoundException("file not found")
        val newProps = Properties().also { it.load(StringReader(content)) }
        for ((key, value) in newProps)
        {
            val propName = key.toString()
            val propValue = value.toString().trim()
            if (propValue.isBlank() || propName.isBlank() || propName.startsWith("#"))
                continue // Skip comments and empty lines

            val lastValue = properties[propName]
            val newValue = when {
                propValue == "true" || value == "false" -> propValue.toBoolean()
                propValue.all { it.isDigit() } -> propValue.toInt()
                propValue.all { it.isDigit() || it == '.' } -> propValue.toFloat()
                else -> value
            }
            properties[propName] = newValue
            if (newValue != lastValue)
                notifyPropertyChange(propName, newValue)
        }
        Logger.debug { "Loaded configuration file: $filePath in ${startTime.toNowFormatted()}" }
    }

    private fun notifyPropertyChange(propName: String, value: Any)
    {
        onChangeCallback(propName, value)

        // Update save directory if game name has changed
        if (propName == ::gameName.name && saveDirectory == defaultSaveDir)
        {
            defaultSaveDir = File("$homeDir/$value").absolutePath
            saveDirectory = defaultSaveDir
        }
    }

    override fun setOnChanged(callback: (propName: String, value: Any) -> Unit)
    {
        onChangeCallback = callback
    }

    override fun getString(name: String) =
        (properties[name] as? String) ?: null.also { Logger.error { "Config property: $name (String) not found" } }

    override fun getInt(name: String) =
        (properties[name] as? Int) ?: null.also { Logger.error { "Config property: $name (Int) not found" } }

    override fun getFloat(name: String) =
        (properties[name] as? Float) ?: null.also { Logger.error { "Config property: $name (Float) not found" } }

    override fun getBool(name: String) =
        (properties[name] as? Boolean) ?: null.also { Logger.error { "Config property: $name (Boolean) not found" } }

    override fun <T: Enum<T>> getEnum(name: String, type: KClass<T>): T?
    {
        val value = properties[name] ?: return null.also { Logger.error { "Config property: $name (Enum) not found" } }
        if (value::class == type)
            return value as T

        if (value is String)
            runCatching { return java.lang.Enum.valueOf(type.java, value).also { properties[name] = it } }

        Logger.error { "Config property: $name (Enum) not applicable to type: ${type.simpleName}" }
        return null
    }

    inner class StringConfig(private val initValue: String)
    {
        operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: String)
        {
            properties[prop.name] = value
            notifyPropertyChange(prop.name, value)
        }

        operator fun getValue(thisRef: Any?, prop: KProperty<*>): String
        {
            val value = properties[prop.name]
            return if (value is String) value else
            {
                if (value != null)
                    Logger.warn { "Config property: ${prop.name} (String) has value: $value (${value::class.simpleName}), using default: $initValue" }
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
            notifyPropertyChange(prop.name, value)
        }

        operator fun getValue(thisRef: Any?, prop: KProperty<*>): Int
        {
            val value = properties[prop.name]
            return if (value is Int) value else
            {
                if (value != null)
                    Logger.warn { "Config property: ${prop.name} (Int) has value: $value (${value::class.simpleName}), using default: $initValue" }
                properties[prop.name] = initValue
                initValue
            }
        }
    }

    inner class FloatConfig(private val initValue: Float, private val minValue: Float = Float.MIN_VALUE)
    {
        operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: Float)
        {
            var value = value
            if (value < minValue)
            {
                Logger.warn { "Config property: ${prop.name} (Float) with value $value can not be lower than $minValue, using default: $minValue" }
                value = minValue
            }
            properties[prop.name] = value
            notifyPropertyChange(prop.name, value)
        }

        operator fun getValue(thisRef: Any?, prop: KProperty<*>): Float
        {
            val value = properties[prop.name]
            return if (value is Float) value else
            {
                if (value != null)
                    Logger.warn { "Config property: ${prop.name} (Float) has value: $value (${value::class.simpleName}), using default: $initValue" }
                properties[prop.name] = initValue
                initValue
            }
        }
    }

    inner class BoolConfig(private val initValue: Boolean)
    {
        operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: Boolean)
        {
            properties[prop.name] = value
            notifyPropertyChange(prop.name, value)
        }

        operator fun getValue(thisRef: Any?, prop: KProperty<*>): Boolean
        {
            val value = properties[prop.name]
            return if (value is Boolean) value else
            {
                if (value != null)
                    Logger.warn { "Config property: ${prop.name} (Boolean) has value: $value (${value::class.simpleName}), using default: $initValue" }
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
            notifyPropertyChange(prop.name, value)
        }

        operator fun getValue(thisRef: Any?, prop: KProperty<*>): T
        {
            val value = properties[prop.name]
            if (value != null && value::class == type)
                return value as T

            var newValue = initValue
            if (value is String)
            {
                try
                {
                    newValue = java.lang.Enum.valueOf(type.java, value)
                }
                catch (e: Exception)
                {
                    Logger.warn { "Config property: ${prop.name} (Enum) has unknown value: $value, using default: ${type.simpleName}.$newValue" }
                }
            }
            else if (value != null)
            {
                Logger.warn { "Config property: ${prop.name} (Enum) has unknown value: $value (${value::class.simpleName}), using default: ${type.simpleName}.$newValue" }
            }
            properties[prop.name] = newValue
            return newValue
        }
    }

    companion object
    {
        private val homeDir = System.getProperty("user.home")
    }
}