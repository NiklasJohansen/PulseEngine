package no.njoh.pulseengine.core.data

import no.njoh.pulseengine.core.data.FileFormat.JSON

abstract class Data
{
    /** The current number of frames the engine is processing per second */
    abstract val currentFps: Int

    /** The number of the current frame. Counts up by one each frame */
    abstract val frameNumber: Long

    /** Total time in milliseconds used to process the most recent frame */
    abstract val totalFrameTimeMs: Float

    /** Time in milliseconds used by the engine to prepare, batch, upload GPU data and perform GPU draw calls in the most recent frame */
    abstract val engineRenderTimeMs: Float

    /** Time in milliseconds used by the game and services to prepare and submit render data for the next frame */
    abstract val gameRenderTimeMs: Float

    /** Time in milliseconds used by the game and services to update the game state */
    abstract val gameUpdateTimeMs: Float

    /**** Time in milliseconds used by the game and services to perform fixed update logic */
    abstract val gameFixedUpdateTimeMs: Float

    /** The fixed time step in seconds used for the fixed update loop. Equal to: 1.0 / fixedTickRate */
    abstract val fixedDeltaTime: Float

    /** The variable time step in seconds used for the update loop. */
    abstract val deltaTime: Float

    /** The interpolation value (0.0 - 1.0) used to smooth out rendering between fixed updates. */
    abstract val interpolation: Float

    /** The total amount of memory in kilobytes available */
    abstract val totalMemoryKb: Long

    /** The amount of memory in kilobytes currently in use */
    abstract val usedMemoryKb: Long

    /** The list of available metrics used to monitor the engine and game stats */
    abstract val metrics: List<Metric>

    /**
     * Adds a new named metric to the metric list.
     */
    abstract fun addMetric(name: String, onSample: Metric.() -> Unit)

    /**
     * Checks if a file with the given [filePath] exists.
     * If the [filePath] does not contain an absolute path, the file will be searched for in the [saveDirectory].
     */
    abstract fun exists(filePath: String): Boolean

    /**
     * Saves the given [data] object to a file with the given [filePath] and [format].
     * @returns true if the save was successful
     */
    abstract fun <T> saveObject(data: T, filePath: String, format: FileFormat = JSON): Boolean

    /**
     * Saves the given [data] object to a file with the given [filePath] and [format] asynchronously.
     * The [onComplete] callback will be called if the save was successful.
     */
    abstract fun <T> saveObjectAsync(data: T, filePath: String, format: FileFormat = JSON, onComplete: (T) -> Unit = {})

    /**
     * Loads an object with the given [filePath].
     * Absolute paths are loaded directly from disk. Relative paths are first resolved from the
     * configured save directory, then from packaged resources when no external file exists.
     */
    inline fun <reified T> loadObject(filePath: String): T? = loadObject(filePath, T::class.java)

    /**
     * Loads an object with the given [filePath].
     * Absolute paths are loaded directly from disk. Relative paths are first resolved from the
     * configured save directory, then from packaged resources when no external file exists.
     */
    abstract fun <T> loadObject(filePath: String, type: Class<T>): T?

    /**
     * Asynchronously loads an object with the given [filePath].
     * Absolute paths are loaded directly from disk. Relative paths are first resolved from the
     * configured save directory, then from packaged resources when no external file exists.
     */
    inline fun <reified T> loadObjectAsync(filePath: String, noinline onFail: () -> Unit = {}, noinline onComplete: (T) -> Unit) =
        loadObjectAsync(filePath, T::class.java, onFail, onComplete)

    /**
     * Asynchronously loads an object with the given [filePath].
     * Absolute paths are loaded directly from disk. Relative paths are first resolved from the
     * configured save directory, then from packaged resources when no external file exists.
     */
    abstract fun <T> loadObjectAsync(filePath: String, type: Class<T>, onFail: () -> Unit, onComplete: (T) -> Unit)

    /**
     * Creates a deep copy of [data].
     */
    abstract fun <T : Any> copyObject(data: T): T?

    /**
     * Serializes [data] to a JSON String.
     */
    abstract fun serializeToJson(data: Any): String?

    /**
     * Deserializes the [json] String to an object of type [T].
     */
    inline fun <reified T> deserializeFromJson(json: String): T? =
        deserializeFromJson(json, T::class.java)

    /**
     * Deserializes the [json] String to an object of type [T].
     */
    abstract fun <T> deserializeFromJson(json: String, type: Class<T>): T?
}

abstract class DataInternal : Data()
{
    abstract override var deltaTime: Float
    abstract override var gameUpdateTimeMs: Float
    abstract override var gameRenderTimeMs: Float
    abstract override var engineRenderTimeMs: Float
    abstract override var interpolation: Float
    abstract override var fixedDeltaTime: Float
    abstract override var gameFixedUpdateTimeMs: Float

    abstract fun init()
    abstract fun update()
    abstract fun calculateFrameRate()
    abstract fun updateMemoryStats()
    abstract fun setOnGetSaveDirectory(callback: () -> String)
}

data class Metric(
    val name: String,
    val onSample: Metric.() -> Unit,
    var latestValue: Float = 0f
) {
    fun sample(value: Float) { latestValue = value }
}