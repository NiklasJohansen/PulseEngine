package engine.modules

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import de.undercouch.bson4jackson.BsonFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
    abstract fun <T> save(data: T, fileName: String): Boolean
    abstract fun <T> saveAsync(data: T, fileName: String, onComplete: (T) -> Unit = {})

    @PublishedApi internal abstract fun <T> load(fileName: String, type: Class<T>): T?
    @PublishedApi internal abstract fun <T> loadInternal(fileName: String, type: Class<T>): T?
    @PublishedApi internal abstract fun <T> loadAsync(fileName: String, type: Class<T>, onComplete: (T) -> Unit)

    inline fun <reified T> load(fileName: String): T? =
        load(fileName, T::class.java)

    inline fun <reified T> loadInternal(fileName: String): T? =
        loadInternal(fileName, T::class.java)

    inline fun <reified T> loadAsync(fileName: String, noinline onLoad: (T) -> Unit) =
        loadAsync(fileName, T::class.java, onLoad)

    companion object
    {
        internal lateinit var INSTANCE: DataInterface
    }
}

abstract class DataEngineInterface : DataInterface()
{
    abstract fun init(creatorName: String, gameName: String)
    abstract fun update()
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

    override fun init(creatorName: String, gameName: String)
    {
        INSTANCE = this
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

    override fun <T> save(data: T, fileName: String): Boolean =
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

    override fun <T> load(fileName: String, type: Class<T>): T? =
        runCatching {
            File("$saveDirectory/$fileName")
                .readBytes()
                .let { byteArray -> objectMapper.readValue(byteArray, type) }
        }
        .onFailure { System.err.println("Failed to load file: $fileName - reason: ${it.message}") }
        .getOrNull()

    override fun <T> loadInternal(fileName: String, type: Class<T>): T? =
        runCatching {
            MutableDataContainer::class.java.getResource("/$fileName")
                .readBytes()
                .let { byteArray -> objectMapper.readValue(byteArray, type) }
        }
        .onFailure { System.err.println("Failed to load internal resource: $fileName - reason: ${it.message}") }
        .getOrNull()

    override fun <T> saveAsync(data: T, fileName: String, onComplete: (T) -> Unit)
    {
        GlobalScope.launch{
            if (save(data, fileName))
                onComplete.invoke(data)
        }
    }

    override fun <T> loadAsync(fileName: String, type: Class<T>, onComplete: (T) -> Unit)
    {
        GlobalScope.launch { load(fileName, type)?.let(onComplete) }
    }

    override fun update()
    {
        usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / MEGA_BYTE
        totalMemory = runtime.maxMemory() / MEGA_BYTE
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
