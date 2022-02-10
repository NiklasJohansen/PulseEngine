package no.njoh.pulseengine.core.config

import no.njoh.pulseengine.core.window.ScreenMode
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

interface Configuration
{
    var creatorName: String
    var gameName: String
    var targetFps: Int
    var fixedTickRate: Int

    fun load(fileName: String)
    fun getString(name: String): String?
    fun getInt(name: String): Int?
    fun getBool(name: String): Boolean?
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