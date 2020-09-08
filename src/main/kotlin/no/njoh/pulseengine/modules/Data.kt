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
import org.lwjgl.glfw.GLFW.*
import java.io.File
import kotlin.system.measureNanoTime

abstract class DataInterface
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
    abstract val dataSources: Map<String, DataSource>
    abstract var saveDirectory: String

    abstract fun addSource(name: String, unit: String, source: () -> Float)
    abstract fun exists(fileName: String): Boolean
    abstract fun <T> saveState(data: T, fileName: String, fileFormat: FileFormat = JSON): Boolean
    abstract fun <T> saveStateAsync(data: T, fileName: String, fileFormat: FileFormat = JSON, onComplete: (T) -> Unit = {})

    @PublishedApi internal abstract fun <T> loadState(fileName: String, type: Class<T>, fromClassPath: Boolean): T?
    @PublishedApi internal abstract fun <T> loadStateAsync(fileName: String, type: Class<T>, fromClassPath: Boolean, onFail: () -> Unit, onComplete: (T) -> Unit)

    inline fun <reified T> loadState(fileName: String, fromClassPath: Boolean = false): T? =
        loadState(fileName, T::class.java, fromClassPath)

    inline fun <reified T> loadStateAsync(fileName: String, fromClassPath: Boolean = false, noinline onFail: () -> Unit = {}, noinline onComplete: (T) -> Unit) =
        loadStateAsync(fileName, T::class.java, fromClassPath, onFail, onComplete)
}

abstract class DataEngineInterface : DataInterface()
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
    override var dataSources = mutableMapOf<String, DataSource>()
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

    override fun addSource(name: String, unit: String, source: () -> Float)
    {
        dataSources[name] = DataSource(name, unit, source)
    }

    override fun exists(fileName: String): Boolean
    {
        val file = File("$saveDirectory$fileName")
        return file.exists() || file.isDirectory
    }

    override fun <T> saveState(data: T, fileName: String, fileFormat: FileFormat): Boolean =
        runCatching {
            val nanoTime = measureNanoTime {
                val file = File("$saveDirectory$fileName")
                if (!file.parentFile.exists())
                    file.parentFile.mkdirs()
                file.writeBytes(getMapper(fileFormat).writeValueAsBytes(data))
            }
            Logger.debug("Saved state in ${"%.3f".format(nanoTime / 1_000_000f)} ms")
            true
        }
        .onFailure { Logger.error("Failed to save file: $fileName - reason: ${it.message}"); }
        .getOrDefault(false)

    override fun <T> loadState(fileName: String, type: Class<T>, fromClassPath: Boolean): T? =
        runCatching {
            var state: T? = null
            val nanoTime = measureNanoTime {
                state = if (fromClassPath)
                    MutableDataContainer::class.java.getResource(fileName)
                        .readBytes()
                        .let { byteArray -> getMapper(getFormat(byteArray)).readValue(byteArray, type) }
                else
                    File("$saveDirectory$fileName")
                        .readBytes()
                        .let { byteArray -> getMapper(getFormat(byteArray)).readValue(byteArray, type) }
            }
            Logger.debug("Loaded state in ${"%.3f".format(nanoTime / 1_000_000f)} ms")
            state
        }
        .onFailure { Logger.error("Failed to load state (fromClassPath=$fromClassPath): $fileName - reason: ${it.message}") }
        .getOrNull()

    override fun <T> saveStateAsync(data: T, fileName: String, fileFormat: FileFormat, onComplete: (T) -> Unit)
    {
        GlobalScope.launch {
            saveState(data, fileName, fileFormat)
                .takeIf { it }
                ?.let { onComplete.invoke(data) }
        }
    }

    override fun <T> loadStateAsync(
        fileName: String, type: Class<T>,
        fromClassPath: Boolean,
        onFail: () -> Unit,
        onComplete: (T) -> Unit
    ) {
        GlobalScope.launch {
            loadState(fileName, type, fromClassPath)
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

    companion object
    {
        private val bsonMapper = ObjectMapper(BsonFactory())
            .registerModule(KotlinModule())
            .enableDefaultTyping()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        private val jsonMapper = ObjectMapper()
            .registerModule(KotlinModule())
            .enableDefaultTyping()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        private val runtime = Runtime.getRuntime()
        private const val MEGA_BYTE = 1048576L
    }
}

data class DataSource(
    val name: String,
    val unit: String,
    val source: () -> Float
)
