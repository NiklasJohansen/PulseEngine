package no.njoh.pulseengine.modules

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import de.undercouch.bson4jackson.BsonFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.njoh.pulseengine.data.FileFormat
import no.njoh.pulseengine.data.FileFormat.*
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.loadBytes
import org.lwjgl.glfw.GLFW.*
import java.io.File
import java.io.FileNotFoundException
import kotlin.system.measureNanoTime

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

abstract class DataEngineInterface : Data()
{
    abstract fun init(creatorName: String, gameName: String)
    abstract fun updateSaveDirectory(creatorName: String, gameName: String)
}

class MutableDataContainer : DataEngineInterface()
{
    override var currentFps: Int = 0
    override var renderTimeMs: Float = 0f
    override var updateTimeMS: Float = 0f
    override var fixedUpdateTimeMS: Float = 0f
    override var fixedDeltaTime: Float = 0.017f
    override var deltaTime: Float = 0.017f
    override var interpolation: Float = 0f
    override var usedMemory: Long = 0L
    override var totalMemory: Long = 0L
    override var metrics = mutableMapOf<String, Metric>()
    override lateinit var saveDirectory: String

    // Used by engine
    private var fpsTimer = 0.0
    private val fpsFilter = FloatArray(20)
    private var frameCounter = 0
    var lastFrameTime = 0.0
    var fixedUpdateAccumulator = 0.0
    var fixedUpdateLastTime = 0.0

    override fun init(creatorName: String, gameName: String)
    {
        updateSaveDirectory(creatorName, gameName)
    }

    override fun addMetric(name: String, unit: String, source: () -> Float)
    {
        metrics[name] = Metric(name, unit, source)
    }

    override fun exists(fileName: String): Boolean =
        getFile(fileName).let { it.exists() || it.isDirectory }

    override fun <T> saveObject(data: T, fileName: String, format: FileFormat): Boolean =
        runCatching {
            val nanoTime = measureNanoTime {
                val file = getFile(fileName)
                if (!file.parentFile.exists())
                    file.parentFile.mkdirs()
                file.writeBytes(getMapper(format).writeValueAsBytes(data))
            }
            Logger.debug("Saved state in ${"%.3f".format(nanoTime / 1_000_000f)} ms")
            true
        }
        .onFailure { Logger.error("Failed to save file: $fileName - reason: ${it.message}"); }
        .getOrDefault(false)

    override fun <T> loadObject(fileName: String, type: Class<T>, fromClassPath: Boolean): T? =
        runCatching {
            var state: T? = null
            val nanoTime = measureNanoTime {
                state = if (fromClassPath)
                    fileName.loadBytes()
                        ?.let { byteArray -> getMapper(getFormat(byteArray)).readValue(byteArray, type) }
                        ?: throw FileNotFoundException("File not found!")
                else
                    getFile(fileName)
                        .readBytes()
                        .let { byteArray -> getMapper(getFormat(byteArray)).readValue(byteArray, type) }
            }
            Logger.debug("Loaded state in ${"%.3f".format(nanoTime / 1_000_000f)} ms")
            state
        }
        .onFailure { Logger.error("Failed to load state (fromClassPath=$fromClassPath): $fileName - reason: ${it.message}") }
        .getOrNull()

    override fun <T> saveObjectAsync(data: T, fileName: String, format: FileFormat, onComplete: (T) -> Unit)
    {
        GlobalScope.launch {
            saveObject(data, fileName, format)
                .takeIf { it }
                ?.let { onComplete.invoke(data) }
        }
    }

    override fun <T> loadObjectAsync(
        fileName: String, type: Class<T>,
        fromClassPath: Boolean,
        onFail: () -> Unit,
        onComplete: (T) -> Unit
    ) {
        GlobalScope.launch {
            loadObject(fileName, type, fromClassPath)
                ?.let(onComplete)
                ?: onFail()
        }
    }

    override fun updateSaveDirectory(creatorName: String, gameName: String) =
        javax.swing.JFileChooser().fileSystemView.defaultDirectory
            .toString()
            .let {
                val file = File("$it/$creatorName/$gameName")
                saveDirectory = file.absolutePath
            }

    inline fun measureAndUpdateTimeStats(block: () -> Unit)
    {
        val startTime = glfwGetTime()
        deltaTime = (startTime - lastFrameTime).toFloat()

        block.invoke()

        lastFrameTime = glfwGetTime()
        updateTimeMS = ((glfwGetTime() - startTime) * 1000.0).toFloat()
    }

    fun updateMemoryStats()
    {
        usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / MEGA_BYTE
        totalMemory = runtime.maxMemory() / MEGA_BYTE
    }

    inline fun measureRenderTimeAndUpdateInterpolationValue(block: () -> Unit)
    {
        val startTime = glfwGetTime()
        interpolation = fixedUpdateAccumulator.toFloat() / fixedDeltaTime

        block.invoke()

        renderTimeMs = ((glfwGetTime() - startTime) * 1000.0).toFloat()
    }

    fun calculateFrameRate()
    {
        val nowTime = glfwGetTime()
        fpsFilter[frameCounter] = 1.0f / (nowTime - fpsTimer).toFloat()
        frameCounter = (frameCounter + 1) % fpsFilter.size
        currentFps = fpsFilter.average().toInt()
        fpsTimer = nowTime
    }

    private fun getMapper(fileFormat: FileFormat) =
        when (fileFormat)
        {
            JSON -> jsonMapper
            BINARY -> bsonMapper
        }

    private fun getFormat(byteArray: ByteArray) =
        if (byteArray.firstOrNull() == '{'.toByte()) JSON else BINARY

    private fun getFile(fileName: String): File =
        File(fileName)
            .takeIf { it.isAbsolute }
            ?: File("$saveDirectory/$fileName")

    companion object
    {
        private val bsonMapper = ObjectMapper(BsonFactory())
            .registerModule(KotlinModule())
            .enableDefaultTyping()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)

        private val jsonMapper = ObjectMapper()
            .registerModule(KotlinModule())
            .enableDefaultTyping()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)

        private val runtime = Runtime.getRuntime()
        private const val MEGA_BYTE = 1048576L
    }
}

data class Metric(
    val name: String,
    val unit: String,
    val source: () -> Float
)
