package no.njoh.pulseengine.modules

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import de.undercouch.bson4jackson.BsonFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.lwjgl.glfw.GLFW.*
import java.io.File

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
    abstract fun <T> saveState(data: T, fileName: String): Boolean
    abstract fun <T> saveStateAsync(data: T, fileName: String, onComplete: (T) -> Unit = {})

    @PublishedApi internal abstract fun <T> loadState(fileName: String, type: Class<T>, fromClassPath: Boolean): T?
    @PublishedApi internal abstract fun <T> loadStateAsync(fileName: String, type: Class<T>, fromClassPath: Boolean, onComplete: (T) -> Unit)

    inline fun <reified T> loadState(fileName: String, fromClassPath: Boolean = false): T? =
        loadState(fileName, T::class.java, fromClassPath)

    inline fun <reified T> loadStateAsync(fileName: String, fromClassPath: Boolean = false, noinline onLoad: (T) -> Unit) =
        loadStateAsync(fileName, T::class.java, fromClassPath, onLoad)
}

abstract class DataEngineInterface : DataInterface()
{
    abstract fun init(creatorName: String, gameName: String)
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
        saveDirectory = createAndGetDefaultSaveDirectory(creatorName, gameName)
    }

    override fun addSource(name: String, unit: String, source: () -> Float)
    {
        dataSources[name] = DataSource(name, unit, source)
    }

    override fun exists(fileName: String): Boolean
    {
        val file = File("$saveDirectory/$fileName")
        return file.exists() || file.isDirectory
    }

    override fun <T> saveState(data: T, fileName: String): Boolean =
        runCatching {
            File("$saveDirectory/$fileName").let { file ->
                if (!file.parentFile.exists())
                    file.parentFile.mkdirs()
                file.writeBytes(objectMapper.writeValueAsBytes(data))
            }

            true
        }
        .onFailure { System.err.println("Failed to save file: $fileName - reason: ${it.message}"); }
        .getOrDefault(false)

    override fun <T> loadState(fileName: String, type: Class<T>, fromClassPath: Boolean): T? =
        runCatching {
            if (fromClassPath)
                MutableDataContainer::class.java.getResource("/$fileName")
                    .readBytes()
                    .let { byteArray -> objectMapper.readValue(byteArray, type) }
            else
                File("$saveDirectory/$fileName")
                    .readBytes()
                    .let { byteArray -> objectMapper.readValue(byteArray, type) }
        }
        .onFailure { System.err.println("Failed to load file (fromClassPath=$fromClassPath): $fileName - reason: ${it.message}") }
        .getOrNull()

    override fun <T> saveStateAsync(data: T, fileName: String, onComplete: (T) -> Unit)
    {
        GlobalScope.launch {
            saveState(data, fileName)
                .takeIf { it }
                ?.let { onComplete.invoke(data) }
        }
    }

    override fun <T> loadStateAsync(fileName: String, type: Class<T>, fromClassPath: Boolean, onComplete: (T) -> Unit)
    {
        GlobalScope.launch {
            loadState(fileName, type, fromClassPath)
                ?.let(onComplete)
        }
    }

    private fun createAndGetDefaultSaveDirectory(creatorName: String, gameName: String): String =
        javax.swing.JFileChooser().fileSystemView.defaultDirectory
            .toString()
            .let {
                val file = File("$it/$creatorName/$gameName")
                if (!file.isDirectory)
                    file.mkdirs()
                file.absolutePath
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

    companion object
    {
        private val objectMapper = ObjectMapper(BsonFactory())
            .registerModule(KotlinModule())
        private val runtime = Runtime.getRuntime()
        private const val MEGA_BYTE = 1048576L
    }
}

data class DataSource(
    val name: String,
    val unit: String,
    val source: () -> Float
)
