package no.njoh.pulseengine.core.data

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import de.undercouch.bson4jackson.BsonFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytesFromClassPath
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen
import org.lwjgl.glfw.GLFW.glfwGetTime
import java.io.File
import java.io.FileNotFoundException
import kotlin.system.measureNanoTime

open class DataImpl : DataInternal()
{
    override var currentFps           = 0
    override var totalFrameTimeMs     = 0f
    override var gpuRenderTimeMs      = 0f
    override var cpuRenderTimeMs      = 0f
    override var cpuUpdateTimeMs      = 0f
    override var cpuFixedUpdateTimeMs = 0f
    override var fixedDeltaTime       = 0.017f
    override var deltaTime            = 0.017f
    override var interpolation        = 0f
    override var usedMemoryKb         = 0L
    override var totalMemoryKb        = 0L
    override val metrics              = ArrayList<Metric>()

    private val fpsFilter      = FloatArray(20)
    private var fpsTimer       = 0.0
    private var frameStartTime = 0.0
    private var frameCounter   = 0
    private var getSaveDir     = { "n/a" }

    var lastFrameTime          = 0.0
    var fixedUpdateAccumulator = 0.0
    var fixedUpdateLastTime    = 0.0

    fun init()
    {
        Logger.info { "Initializing data (DataImpl)" }

        addMetric("FRAMES PER SECOND (FPS)")    { sample(currentFps.toFloat())                }
        addMetric("FRAME TIME (MS)")            { sample(totalFrameTimeMs)                    }
        addMetric("GPU RENDER TIME (MS)")       { sample(gpuRenderTimeMs)                     }
        addMetric("CPU RENDER TIME (MS)")       { sample(cpuRenderTimeMs)                     }
        addMetric("CPU UPDATE TIME (MS)")       { sample(cpuUpdateTimeMs)                     }
        addMetric("CPU FIXED UPDATE TIME (MS)") { sample(cpuFixedUpdateTimeMs)                }
        addMetric("USED MEMORY (KB)")           { sample(usedMemoryKb.toFloat())              }
        addMetric("MEMORY OF TOTAL (%)")        { sample(usedMemoryKb * 100f / totalMemoryKb) }
    }

    override fun addMetric(name: String, onSample: Metric.() -> Unit)
    {
        metrics.removeWhen { it.name == name }
        metrics += Metric(name, onSample)
    }

    override fun exists(filePath: String): Boolean =
        getFile(filePath).let { it.exists() || it.isDirectory }

    override fun <T> saveObject(data: T, filePath: String, format: FileFormat): Boolean =
        runCatching {
            val nanoTime = measureNanoTime {
                val file = getFile(filePath)
                if (!file.parentFile.exists())
                    file.parentFile.mkdirs()
                file.writeBytes(getMapper(format).writeValueAsBytes(data))
            }
            Logger.debug { "Saved state into $filePath in ${"%.3f".format(nanoTime / 1_000_000f)} ms" }
            true
        }
        .onFailure { Logger.error { "Failed to save file: $filePath - reason: ${it.message}" } }
        .getOrDefault(false)

    override fun <T> loadObject(filePath: String, type: Class<T>, fromClassPath: Boolean): T? =
        runCatching {
            var state: T? = null
            val nanoTime = measureNanoTime {
                state = if (fromClassPath)
                    filePath.loadBytesFromClassPath()
                        ?.let { byteArray -> getMapper(getFormat(byteArray)).readValue(byteArray, type) }
                        ?: throw FileNotFoundException("File not found: $filePath")
                else
                    getFile(filePath)
                        .readBytes()
                        .let { byteArray -> getMapper(getFormat(byteArray)).readValue(byteArray, type) }
            }
            Logger.debug { "Loaded state from $filePath in ${"%.3f".format(nanoTime / 1_000_000f)} ms" }
            state
        }
        .onFailure { Logger.error { "Failed to load state (fromClassPath=$fromClassPath): $filePath - reason: ${it.message}" } }
        .getOrNull()

    override fun <T> saveObjectAsync(data: T, filePath: String, format: FileFormat, onComplete: (T) -> Unit)
    {
        GlobalScope.launch(Dispatchers.IO)
        {
            saveObject(data, filePath, format).takeIf { it }?.let { onComplete.invoke(data) }
        }
    }

    override fun <T> loadObjectAsync(
        filePath: String,
        type: Class<T>,
        fromClassPath: Boolean,
        onFail: () -> Unit,
        onComplete: (T) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO)
        {
            loadObject(filePath, type, fromClassPath)?.let(onComplete) ?: onFail()
        }
    }

    override fun setOnGetSaveDirectory(callback: () -> String) { getSaveDir = callback }

    fun startFrameTimer()
    {
        frameStartTime = glfwGetTime()
    }

    inline fun measureAndUpdateTimeStats(block: () -> Unit)
    {
        val startTime = glfwGetTime()
        deltaTime = (startTime - lastFrameTime).toFloat()

        block.invoke()

        lastFrameTime = glfwGetTime()
        cpuUpdateTimeMs = ((glfwGetTime() - startTime) * 1000.0).toFloat()
    }

    fun updateMemoryStats()
    {
        usedMemoryKb = (runtime.totalMemory() - runtime.freeMemory()) / KILO_BYTE
        totalMemoryKb = runtime.maxMemory() / KILO_BYTE
    }

    inline fun measureCpuRenderTime(block: () -> Unit)
    {
        val startTime = glfwGetTime()

        block.invoke()

        cpuRenderTimeMs = ((glfwGetTime() - startTime) * 1000.0).toFloat()
    }

    inline fun measureGpuRenderTime(block: () -> Unit)
    {
        val startTime = glfwGetTime()

        block.invoke()

        gpuRenderTimeMs = ((glfwGetTime() - startTime) * 1000.0).toFloat()
    }

    fun updateInterpolationValue()
    {
        interpolation = fixedUpdateAccumulator.toFloat() / fixedDeltaTime
    }

    fun calculateFrameRate()
    {
        val nowTime = glfwGetTime()
        fpsFilter[frameCounter] = 1.0f / (nowTime - fpsTimer).toFloat()
        frameCounter = (frameCounter + 1) % fpsFilter.size
        currentFps = fpsFilter.average().toInt()
        fpsTimer = nowTime
        totalFrameTimeMs = ((nowTime - frameStartTime) * 1000.0).toFloat()
    }

    private fun getMapper(fileFormat: FileFormat) =
        when (fileFormat)
        {
            FileFormat.JSON -> jsonMapper
            FileFormat.BINARY -> bsonMapper
        }

    private fun getFormat(byteArray: ByteArray) =
        if (byteArray.firstOrNull() == '{'.toByte()) FileFormat.JSON else FileFormat.BINARY

    private fun getFile(filePath: String): File =
        File(filePath).takeIf { it.isAbsolute } ?: File("${getSaveDir()}/$filePath")

    companion object
    {
        private val bsonMapper = ObjectMapper(BsonFactory())
            .registerModule(KotlinModule.Builder().build())
            .enableDefaultTyping()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)

        private val jsonMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enableDefaultTyping()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)

        private const val KILO_BYTE = 1024L
        private val runtime = Runtime.getRuntime()
    }
}