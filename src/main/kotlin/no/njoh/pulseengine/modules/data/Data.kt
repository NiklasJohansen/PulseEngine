package no.njoh.pulseengine.modules.data

import no.njoh.pulseengine.data.FileFormat
import no.njoh.pulseengine.data.FileFormat.JSON

abstract class Data
{
    abstract val currentFps: Int
    abstract val renderTimeMs: Float
    abstract val updateTimeMS: Float
    abstract val fixedUpdateTimeMS: Float
    abstract val fixedDeltaTime: Float
    abstract val deltaTime: Float
    abstract val interpolation: Float
    abstract val totalMemory: Long
    abstract val usedMemory: Long
    abstract val metrics: Map<String, Metric>
    abstract var saveDirectory: String

    abstract fun addMetric(name: String, unit: String = "", source: () -> Float)
    abstract fun exists(fileName: String): Boolean
    abstract fun <T> saveObject(data: T, fileName: String, format: FileFormat = JSON): Boolean
    abstract fun <T> saveObjectAsync(data: T, fileName: String, format: FileFormat = JSON, onComplete: (T) -> Unit = {})

    inline fun <reified T> loadObject(fileName: String, fromClassPath: Boolean = false): T? =
        loadObject(fileName, T::class.java, fromClassPath)

    inline fun <reified T> loadObjectAsync(fileName: String, fromClassPath: Boolean = false, noinline onFail: () -> Unit = {}, noinline onComplete: (T) -> Unit) =
        loadObjectAsync(fileName, T::class.java, fromClassPath, onFail, onComplete)

    @PublishedApi
    internal abstract fun <T> loadObject(fileName: String, type: Class<T>, fromClassPath: Boolean): T?

    @PublishedApi
    internal abstract fun <T> loadObjectAsync(fileName: String, type: Class<T>, fromClassPath: Boolean, onFail: () -> Unit, onComplete: (T) -> Unit)
}

data class Metric(
    val name: String,
    val unit: String,
    val source: () -> Float
)