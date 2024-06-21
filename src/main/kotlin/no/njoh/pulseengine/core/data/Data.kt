package no.njoh.pulseengine.core.data

import no.njoh.pulseengine.core.data.FileFormat.JSON

abstract class Data
{
    /** The current number of frames the engine is processing per second */
    abstract val currentFps: Int

    /** Total time in milliseconds used to process the most recent frame */
    abstract val totalFrameTimeMs: Float

    /** Time in milliseconds used to upload GPU data and perform GPU draw calls in the most recent frame */
    abstract val gpuRenderTimeMs: Float

    /** Time in milliseconds used by the CPU to prepare GPU data for the next frame */
    abstract val cpuRenderTimeMs: Float

    /** Time in milliseconds used by the CPU to update the game state */
    abstract val cpuUpdateTimeMs: Float

    /**** Time in milliseconds used by the CPU to perform fixed update logic */
    abstract val cpuFixedUpdateTimeMs: Float

    /** The fixed time step in seconds used for the fixed update loop. Equal to: 1.0 / fixedTickRate */
    abstract val fixedDeltaTime: Float

    /** The variable time step in seconds used for the update loop. */
    abstract val deltaTime: Float

    /** The interpolation value used to smooth out rendering between fixed updates */
    abstract val interpolation: Float

    /** The total amount of memory in kilobytes available */
    abstract val totalMemoryKb: Long

    /** The amount of memory in kilobytes currently in use */
    abstract val usedMemoryKb: Long

    /** The list of available metrics used to monitor the engine and game stats */
    abstract val metrics: List<Metric>

    /** The absolute path to where save files are stored */
    abstract var saveDirectory: String

    /**
     * Adds a new named metric to the metric list.
     */
    abstract fun addMetric(name: String, onSample: Metric.() -> Unit)

    /**
     * Checks if a file with the given [fileName] exists.
     * If the [fileName] does not contain an absolute path, the file will be searched for in the [saveDirectory].
     */
    abstract fun exists(fileName: String): Boolean

    /**
     * Saves the given [data] object to a file with the given [fileName] and [format].
     * @returns true if the save was successful
     */
    abstract fun <T> saveObject(data: T, fileName: String, format: FileFormat = JSON): Boolean

    /**
     * Saves the given [data] object to a file with the given [fileName] and [format] asynchronously.
     * The [onComplete] callback will be called if the save was successful.
     */
    abstract fun <T> saveObjectAsync(data: T, fileName: String, format: FileFormat = JSON, onComplete: (T) -> Unit = {})

    /**
     * Loads an object with the given [fileName] either from an absolute path, if included in the [fileName],
     * or from the [saveDirectory]. Will load the object from classpath if [fromClassPath] is true.
     */
    inline fun <reified T> loadObject(fileName: String, fromClassPath: Boolean = false): T? =
        loadObject(fileName, T::class.java, fromClassPath)

    /**
     * Loads an object with the given [fileName] either from an absolute path, if included in the [fileName],
     * or from the [saveDirectory]. Will load the object from classpath if [fromClassPath] is true.
     */
    abstract fun <T> loadObject(fileName: String, type: Class<T>, fromClassPath: Boolean): T?

    /**
     * Asynchronously loads an object with the given [fileName] either from an absolute path, if included in the
     * [fileName], or from the [saveDirectory]. Will load the object from classpath if [fromClassPath] is true.
     */
    inline fun <reified T> loadObjectAsync(fileName: String, fromClassPath: Boolean = false, noinline onFail: () -> Unit = {}, noinline onComplete: (T) -> Unit) =
        loadObjectAsync(fileName, T::class.java, fromClassPath, onFail, onComplete)

    /**
     * Asynchronously loads an object with the given [fileName] either from an absolute path, if included in the
     * [fileName], or from the [saveDirectory]. Will load the object from classpath if [fromClassPath] is true.
     */
    abstract fun <T> loadObjectAsync(fileName: String, type: Class<T>, fromClassPath: Boolean, onFail: () -> Unit, onComplete: (T) -> Unit)
}

data class Metric(
    val name: String,
    val onSample: Metric.() -> Unit,
    var latestValue: Float = 0f
) {
    fun sample(value: Float) { latestValue = value }
}