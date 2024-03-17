package no.njoh.pulseengine.core.data

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import de.undercouch.bson4jackson.BsonFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytes
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import org.lwjgl.glfw.GLFW.glfwGetTime
import java.io.File
import java.io.FileNotFoundException
import kotlin.system.measureNanoTime

open class DataImpl : Data()
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
    override val metrics = mutableListOf<Metric>()
    override lateinit var saveDirectory: String

    // Used by engine
    private var fpsTimer = 0.0
    private val fpsFilter = FloatArray(20)
    private var frameCounter = 0
    var lastFrameTime = 0.0
    var fixedUpdateAccumulator = 0.0
    var fixedUpdateLastTime = 0.0

    fun init(gameName: String)
    {
        Logger.info("Initializing data (${this::class.simpleName})")
        updateSaveDirectory(gameName)

        addMetric("FRAMES PER SECOND (FPS)") { sample(currentFps.toFloat()) }
        addMetric("RENDER TIME (MS)")        { sample(renderTimeMs) }
        addMetric("UPDATE TIME (MS)")        { sample(updateTimeMS) }
        addMetric("FIXED UPDATE TIME (MS)")  { sample(fixedUpdateTimeMS) }
        addMetric("USED MEMORY (KB)")        { sample(usedMemory.toFloat()) }
        addMetric("MEMORY OF TOTAL (%)")     { sample(usedMemory * 100f / totalMemory) }
    }

    override fun addMetric(name: String, onSample: Metric.() -> Unit)
    {
        metrics.removeWhen { it.name == name }
        metrics += Metric(name, onSample)
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
            Logger.debug("Saved state into $fileName in ${"%.3f".format(nanoTime / 1_000_000f)} ms")
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
            Logger.debug("Loaded state from $fileName in ${"%.3f".format(nanoTime / 1_000_000f)} ms")
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

    fun updateSaveDirectory(gameName: String)
    {
        saveDirectory = File("$homeDir/$gameName").absolutePath
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
        usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / KILO_BYTE
        totalMemory = runtime.maxMemory() / KILO_BYTE
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
            FileFormat.JSON -> jsonMapper
            FileFormat.BINARY -> bsonMapper
        }

    private fun getFormat(byteArray: ByteArray) =
        if (byteArray.firstOrNull() == '{'.toByte()) FileFormat.JSON else FileFormat.BINARY

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

        private const val KILO_BYTE = 1024L
        private val runtime = Runtime.getRuntime()
        val homeDir = javax.swing.JFileChooser().fileSystemView.defaultDirectory.toString()
    }
}